#include "agent_channel_client.h"

#include <chrono>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <grpcpp/grpcpp.h>
#include <nlohmann/json.hpp>
#include <thread>
#include <condition_variable>

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <cstring>
#include <cerrno>

#include "CmsEvents.grpc.pb.h"
#include "grpc_starter.h"

using grpc::ClientContext;
using grpc::ClientReaderWriter;
using grpc::ClientReaderWriterInterface;
using com::fyordo::cms::AgentChannelService;
using com::fyordo::cms::AgentStreamEvent;
using com::fyordo::cms::ServerStreamEvent;
using com::fyordo::cms::AgentConnectEvent;

AgentChannelClient::AgentChannelClient(const AgentConfig& config, const std::string& server_address)
    : config_(config)
    , server_address_(server_address)
    , running_(false)
    , current_revision_(ReadRevisionFromFile())
{
    if (current_revision_.load() >= 0) {
        std::cout << "AgentChannelClient: Loaded stored revision " << current_revision_.load()
                  << " from " << config_.cmsRevisionFilePath << std::endl;
    }
}

AgentChannelClient::~AgentChannelClient()
{
    Stop();
}

void AgentChannelClient::Start()
{
    bool already_running = false;
    if (!running_.compare_exchange_strong(already_running, true)) {
        std::cerr << "AgentChannelClient: Already running" << std::endl;
        return;
    }
    client_thread_ = std::thread(&AgentChannelClient::Run, this);
}

void AgentChannelClient::Stop()
{
    bool was_running = true;
    if (!running_.compare_exchange_strong(was_running, false)) {
        return;
    }
    stream_cv_.notify_one();
    stream_writer_cv_.notify_all();
    if (client_thread_.joinable()) {
        client_thread_.join();
    }
}

void AgentChannelClient::Run()
{
    {
        grpc::ChannelArguments channel_args;
        channel_args.SetServiceConfigJSON(R"({"loadBalancingConfig": [{"round_robin": {}}]})");
        channel_args.SetInt(GRPC_ARG_KEEPALIVE_TIME_MS,              KEEPALIVE_TIME_MS);
        channel_args.SetInt(GRPC_ARG_KEEPALIVE_TIMEOUT_MS,           KEEPALIVE_TIMEOUT_MS);
        channel_args.SetInt(GRPC_ARG_KEEPALIVE_PERMIT_WITHOUT_CALLS, 1);
        channel_args.SetInt(GRPC_ARG_HTTP2_MAX_PINGS_WITHOUT_DATA,   0);
        channel_ = grpc::CreateCustomChannel(
            server_address_, CreateChannelCredentials(config_.tls), channel_args
        );
        stub_ = AgentChannelService::NewStub(channel_);
    }

    while (running_.load()) {
        try {
            if (!WaitForConnected(channel_.get())) {
                std::this_thread::sleep_for(RECONNECT_DELAY);
                continue;
            }

            ClientContext context;
            std::cout << "AgentChannelClient: Connecting to server - "
                      << "namespace: " << config_.ns << ", "
                      << "service: " << config_.service << ", "
                      << "appId: " << config_.appId << std::endl;

            std::shared_ptr<ClientReaderWriter<AgentStreamEvent, ServerStreamEvent>> stream(
                stub_->WatchProperties(&context)
            );

            if (!stream) {
                std::cerr << "AgentChannelClient: Failed to create stream" << std::endl;
                std::this_thread::sleep_for(RECONNECT_DELAY);
                continue;
            }

            AgentStreamEvent connect_message;
            AgentConnectEvent* connect = connect_message.mutable_connectevent();
            connect->set_namespace_(config_.ns);
            connect->set_service(config_.service);
            connect->set_appid(config_.appId);
            if (!stream->Write(connect_message)) {
                std::cerr << "AgentChannelClient: Failed to send connect event" << std::endl;
                grpc::Status status = stream->Finish();
                if (!status.ok()) {
                    std::cerr << "AgentChannelClient: Stream failed after connect event: "
                              << status.error_code() << " - " << status.error_message() << std::endl;
                }
                std::this_thread::sleep_for(RECONNECT_DELAY);
                continue;
            }
            std::cout << "AgentChannelClient: Stream created successfully" << std::endl;

            std::atomic<bool> is_reading{true};
            std::atomic<bool> is_writing{true};

            std::thread read_thread(
                &AgentChannelClient::RunStreamSession, this,
                stream.get(), std::ref(is_reading), std::ref(is_writing)
            );
            std::thread writer_thread(
                &AgentChannelClient::RunStreamWriter, this,
                stream.get(), std::ref(is_reading), std::ref(is_writing)
            );

            {
                std::unique_lock<std::mutex> lock(stream_mutex_);
                stream_cv_.wait(lock, [&] { return !running_.load() || !is_reading.load(); });
            }

            is_reading.store(false);
            is_writing.store(false);

            context.TryCancel();

            if (read_thread.joinable()) {
                read_thread.join();
            }
            if (writer_thread.joinable()) {
                writer_thread.join();
            }

            grpc::Status status = stream->Finish();
            if (!status.ok()) {
                std::cerr << "AgentChannelClient: Stream finished with error: "
                          << status.error_code() << " - " << status.error_message() << std::endl;
            } else {
                std::cout << "AgentChannelClient: Disconnected from server gracefully" << std::endl;
            }
        } catch (const std::exception& e) {
            std::cerr << "AgentChannelClient: Exception: " << e.what() << std::endl;
        }

        if (running_.load()) {
            std::cout << "AgentChannelClient: Reconnecting in " << RECONNECT_DELAY.count()
                      << " seconds..." << std::endl;
            std::this_thread::sleep_for(RECONNECT_DELAY);
        }
    }
}

void AgentChannelClient::RunStreamSession(ClientReaderWriterInterface<AgentStreamEvent, ServerStreamEvent>* stream, std::atomic<bool>& is_reading, std::atomic<bool>& is_writing)
{
    ServerStreamEvent event;

    while (is_reading.load() && stream->Read(&event)) {
        if (event.has_initevent()) {
            HandleInitEvent(event.initevent());
        } else if (event.has_updateevent()) {
            HandlePropertyUpdate(event.updateevent());
        }
    }
    is_reading.store(false);
    stream_cv_.notify_one();
    std::cout << "AgentChannelClient: Read loop finished" << std::endl;
}

void AgentChannelClient::RequestAckFlush()
{
    ack_flush_pending_.store(true);
    stream_writer_cv_.notify_one();
}

void AgentChannelClient::RunStreamWriter(
    ClientReaderWriterInterface<AgentStreamEvent, ServerStreamEvent>* stream,
    std::atomic<bool>& is_reading,
    std::atomic<bool>& is_writing)
{
    using clock = std::chrono::steady_clock;
    auto next_ack = clock::now() + ACK_INTERVAL;

    while (running_.load() && is_reading.load() && is_writing.load()) {
        {
            std::unique_lock<std::mutex> lock(stream_writer_mutex_);
            stream_writer_cv_.wait_until(lock, next_ack, [&] {
                return !running_.load() || !is_reading.load() || !is_writing.load() ||
                       ack_flush_pending_.load();
            });
        }
        if (!running_.load() || !is_reading.load() || !is_writing.load()) {
            break;
        }
        ack_flush_pending_.store(false);

        AgentStreamEvent ack_message;
        const int64_t revision = current_revision_.load();
        ack_message.mutable_ackevent()->set_revision(revision);
        if (!stream->Write(ack_message)) {
            std::cerr << "AgentChannelClient: Failed to send ack to server for revision " << revision << std::endl;
            return;
        }
        std::cout << "AgentChannelClient: Sent ack to server for revision " << revision << std::endl;
        next_ack = clock::now() + ACK_INTERVAL;
    }
}

void AgentChannelClient::HandleInitEvent(const com::fyordo::cms::ServerInitEvent& init_event)
{
    const int64_t new_revision = init_event.revision();
    const int64_t stored_revision = current_revision_.load();

    std::cout << "AgentChannelClient: Received ServerInitEvent - "
              << "revision: " << new_revision << ", "
              << "properties count: " << init_event.properties_size() << std::endl;

    if (stored_revision >= 0 && new_revision < stored_revision) {
        std::cerr << "AgentChannelClient: ERROR - init event revision " << new_revision
                  << " is less than stored revision " << stored_revision
                  << ". Rejecting event." << std::endl;
        return;
    }

    if (!config_.propertiesJsonPath.empty() && !WritePropertiesToFile(init_event)) {
        std::cerr << "AgentChannelClient: Failed to write initial properties to file" << std::endl;
        return;
    }

    if (WriteRevisionToFile(new_revision)) {
        current_revision_.store(new_revision);
        std::cout << "AgentChannelClient: Revision updated to " << new_revision << std::endl;
    }
}

void AgentChannelClient::HandlePropertyUpdate(
    const com::fyordo::cms::ServerPropertyUpdateEvent& update_event)
{
    const int64_t new_revision = update_event.revision();
    const int64_t stored_revision = current_revision_.load();

    std::cout << "AgentChannelClient: Received ServerPropertyUpdateEvent - "
              << "key: " << update_event.property().key() << ", "
              << "lastModifiedMs: " << update_event.property().modifiedms() << ", "
              << "revision: " << new_revision << std::endl;

    if (stored_revision >= 0 && new_revision < stored_revision) {
        std::cerr << "AgentChannelClient: ERROR - update event revision " << new_revision
                  << " is less than stored revision " << stored_revision
                  << ". Rejecting event." << std::endl;
        return;
    }

    if (config_.propertiesJsonPath.empty()) {
        std::cerr << "AgentChannelClient: Skipping property update (CMS_PROPERTIES_FILE not set)" << std::endl;
        return;
    }
    try {
        if (!ApplyPropertyUpdateToFile(update_event.property().key(), update_event.property().value())) {
            std::cerr << "AgentChannelClient: Failed to apply property update for key "
                      << update_event.property().key() << std::endl;
            return;
        }
    
        SendUpdateToUnixSocket(update_event.property());
    
        if (WriteRevisionToFile(new_revision)) {
            current_revision_.store(new_revision);
            std::cout << "AgentChannelClient: Revision updated to " << new_revision << std::endl;
        }
    } catch (const std::exception& e) {
        std::cerr << "AgentChannelClient: Exception: " << e.what() << std::endl;
        RequestAckFlush();
        return;
    }
}

bool AgentChannelClient::ApplyPropertyUpdateToFile(const std::string& key, const std::string& value)
{
    std::lock_guard<std::mutex> lock(properties_write_mutex_);

    // Use in-memory cache instead of re-reading from disk — avoids TOCTOU race
    if (!properties_cache_.is_object()) {
        properties_cache_ = nlohmann::json::object();
    }
    properties_cache_[key] = value;

    if (!WriteJsonToPath(properties_cache_)) {
        return false;
    }
    std::cout << "AgentChannelClient: Updated key " << key << " in " << config_.propertiesJsonPath << std::endl;
    return true;
}

bool AgentChannelClient::WritePropertiesToFile(const com::fyordo::cms::ServerInitEvent& init_event)
{
    std::lock_guard<std::mutex> lock(properties_write_mutex_);

    properties_cache_ = nlohmann::json::object();
    for (const com::fyordo::cms::Property& prop : init_event.properties()) {
        properties_cache_[prop.key()] = prop.value();
        SendUpdateToUnixSocket(prop);
    }
    if (!WriteJsonToPath(properties_cache_)) {
        std::cerr << "AgentChannelClient: Failed to write properties (use a path writable by the process, e.g. /app/application.json)"
                  << std::endl;
        return false;
    }
    std::cout << "AgentChannelClient: Wrote " << init_event.properties_size()
              << " properties to " << config_.propertiesJsonPath << std::endl;
    return true;
}

bool AgentChannelClient::WriteJsonToPath(const nlohmann::json& j)
{
    const std::string tmp_path = config_.propertiesJsonPath + ".tmp";
    const std::string content = j.dump();

    int fd = ::open(tmp_path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd == -1) {
        std::cerr << "AgentChannelClient: Failed to open file for writing: " << config_.propertiesJsonPath
                  << std::endl;
        return false;
    }

    ssize_t written = ::write(fd, content.data(), content.size());
    if (written != static_cast<ssize_t>(content.size())) {
        std::cerr << "AgentChannelClient: Write failed: " << tmp_path << std::endl;
        ::close(fd);
        return false;
    }

    if (::fsync(fd) != 0) {
        std::cerr << "AgentChannelClient: fsync failed: " << tmp_path << std::endl;
        ::close(fd);
        return false;
    }

    ::close(fd);

    if (std::rename(tmp_path.c_str(), config_.propertiesJsonPath.c_str()) != 0) {
        std::cerr << "AgentChannelClient: Failed to rename temp file to " << config_.propertiesJsonPath
                  << std::endl;
        std::remove(tmp_path.c_str());
        return false;
    }
    return true;
}

void AgentChannelClient::SendUpdateToUnixSocket(const com::fyordo::cms::Property& property)
{
    if (config_.unixSocketPath.empty()) {
        return;
    }

    int sock_fd = ::socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock_fd == -1) {
        std::cerr << "AgentChannelClient: Failed to create UNIX socket: "
                  << std::strerror(errno) << " (errno=" << errno << ")" << std::endl;
        return;
    }

    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    if (config_.unixSocketPath.size() >= sizeof(addr.sun_path)) {
        std::cerr << "AgentChannelClient: UNIX socket path is too long: " << config_.unixSocketPath << std::endl;
        ::close(sock_fd);
        return;
    }
    std::strncpy(addr.sun_path, config_.unixSocketPath.c_str(), sizeof(addr.sun_path) - 1);

    if (::connect(sock_fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) == -1) {
        if (errno == ENOENT || errno == ECONNREFUSED) {
            std::cout << "AgentChannelClient: UNIX socket not ready at "
                      << config_.unixSocketPath
                      << " (SDK may not have started yet), skipping push for key '"
                      << property.key() << "'" << std::endl;
        } else {
            std::cerr << "AgentChannelClient: Failed to connect to UNIX socket at "
                      << config_.unixSocketPath << ": "
                      << std::strerror(errno) << " (errno=" << errno << ")" << std::endl;
        }
        ::close(sock_fd);
        return;
    }

    std::cout << "AgentChannelClient: Connected to UNIX socket at "
              << config_.unixSocketPath << " for key '" << property.key()
              << "' (value_len=" << property.value().size() << ")" << std::endl;

    auto send_all = [sock_fd](const void* buf, size_t len) -> bool {
        const char* ptr = static_cast<const char*>(buf);
        size_t total_sent = 0;
        while (total_sent < len) {
            ssize_t sent = ::send(sock_fd, ptr + total_sent, len - total_sent, 0);
            if (sent <= 0) {
                return false;
            }
            total_sent += static_cast<size_t>(sent);
        }
        return true;
    };

    std::string payload;
    if (!property.SerializeToString(&payload)) {
        std::cerr << "AgentChannelClient: Failed to serialize Property" << std::endl;
        ::close(sock_fd);
        return;
    }
    uint32_t payload_len_be = htonl(static_cast<uint32_t>(payload.size()));

    if (!send_all(&payload_len_be, sizeof(payload_len_be)) ||
        !send_all(payload.data(), payload.size())) {
        std::cerr << "AgentChannelClient: Failed to send Property payload: "
                << std::strerror(errno) << " (errno=" << errno << ")" << std::endl;
    }

    ::close(sock_fd);
}

int64_t AgentChannelClient::ReadRevisionFromFile()
{
    if (config_.cmsRevisionFilePath.empty()) {
        return -1;
    }
    std::ifstream in(config_.cmsRevisionFilePath);
    if (!in.good()) {
        return -1;
    }
    int64_t revision = -1;
    in >> revision;
    if (in.fail()) {
        std::cerr << "AgentChannelClient: Failed to parse revision from "
                  << config_.cmsRevisionFilePath << std::endl;
        return -1;
    }
    return revision;
}

bool AgentChannelClient::WriteRevisionToFile(int64_t revision)
{
    if (config_.cmsRevisionFilePath.empty()) {
        return true;
    }
    std::lock_guard<std::mutex> lock(revision_write_mutex_);
    const std::string tmp_path = config_.cmsRevisionFilePath + ".tmp";
    const std::string content = std::to_string(revision);

    int fd = ::open(tmp_path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd == -1) {
        std::cerr << "AgentChannelClient: Failed to open revision file for writing: "
                  << tmp_path << std::endl;
        return false;
    }

    ssize_t written = ::write(fd, content.data(), content.size());
    if (written != static_cast<ssize_t>(content.size())) {
        std::cerr << "AgentChannelClient: Write failed for revision file: "
                  << tmp_path << std::endl;
        ::close(fd);
        return false;
    }

    if (::fsync(fd) != 0) {
        std::cerr << "AgentChannelClient: fsync failed for revision file: "
                  << tmp_path << std::endl;
        ::close(fd);
        return false;
    }

    ::close(fd);

    if (std::rename(tmp_path.c_str(), config_.cmsRevisionFilePath.c_str()) != 0) {
        std::cerr << "AgentChannelClient: Failed to rename revision temp file to "
                  << config_.cmsRevisionFilePath << std::endl;
        std::remove(tmp_path.c_str());
        return false;
    }
    return true;
}

bool AgentChannelClient::WaitForConnected(grpc::Channel* channel)
{
    auto deadline = std::chrono::system_clock::now() + CONNECTION_TIMEOUT;
    if (channel->WaitForConnected(deadline)) {
        std::cout << "AgentChannelClient: Connected to server at " << server_address_ << std::endl;
        return true;
    }
    std::cerr << "AgentChannelClient: Failed to connect to server at " << server_address_ << std::endl;
    return false;
}
