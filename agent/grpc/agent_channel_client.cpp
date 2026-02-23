#include "agent_channel_client.h"

#include <chrono>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <grpcpp/grpcpp.h>
#include <nlohmann/json.hpp>

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <cstring>

#include "AgentChannelService.grpc.pb.h"
#include "grpc_starter.h"

using grpc::ClientContext;
using grpc::ClientReader;
using grpc::ClientReaderInterface;
using com::fyordo::cms::AgentChannelService;
using com::fyordo::cms::ServerStreamEvent;
using com::fyordo::cms::AgentConnectRequest;

AgentChannelClient::AgentChannelClient(const AgentConfig& config, const std::string& server_address)
    : config_(config)
    , server_address_(server_address)
    , running_(false)
{
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
    if (client_thread_.joinable()) {
        client_thread_.join();
    }
}

void AgentChannelClient::Run()
{
    while (running_.load()) {
        try {
            AGENT_STATE.store(AgentState::CONNECT);
            auto channel = grpc::CreateChannel(server_address_, grpc::InsecureChannelCredentials());
            auto stub = AgentChannelService::NewStub(channel);

            if (!WaitForConnected(channel.get())) {
                std::this_thread::sleep_for(RECONNECT_DELAY);
                continue;
            }

            ClientContext context;
            AgentConnectRequest request;
            request.set_namespace_(config_.namespace_);
            request.set_service(config_.service);
            request.set_appid(config_.appId);

            std::cout << "AgentChannelClient: Connecting to server - "
                      << "namespace: " << config_.namespace_ << ", "
                      << "service: " << config_.service << ", "
                      << "appId: " << config_.appId << std::endl;

            std::unique_ptr<ClientReader<ServerStreamEvent>> reader(
                stub->WatchProperties(&context, request));

            if (!reader) {
                std::cerr << "AgentChannelClient: Failed to create stream" << std::endl;
                std::this_thread::sleep_for(RECONNECT_DELAY);
                continue;
            }
            std::cout << "AgentChannelClient: Stream created successfully" << std::endl;

            std::atomic<bool> is_reading{true};
            std::thread read_thread(&AgentChannelClient::RunStreamSession, this, reader.get(), std::ref(is_reading));

            while (running_.load() && is_reading.load()) {
                std::this_thread::sleep_for(POLL_INTERVAL_MS);
            }
            is_reading.store(false);
            context.TryCancel();
            if (read_thread.joinable()) {
                read_thread.join();
            }

            grpc::Status status = reader->Finish();
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

void AgentChannelClient::RunStreamSession(void* stream_ptr, std::atomic<bool>& is_reading)
{
    auto* stream = static_cast<ClientReaderInterface<ServerStreamEvent>*>(stream_ptr);
    ServerStreamEvent event;

    while (is_reading.load() && stream->Read(&event)) {
        if (event.has_initevent()) {
            HandleInitEvent(event.initevent());
        } else if (event.has_updateevent()) {
            HandlePropertyUpdate(event.updateevent());
        }
    }
    is_reading.store(false);
    std::cout << "AgentChannelClient: Read loop finished" << std::endl;
}

void AgentChannelClient::HandleInitEvent(const com::fyordo::cms::ServerInitEvent& init_event)
{
    std::cout << "AgentChannelClient: Received ServerInitEvent - "
              << "lastModifiedMs: " << init_event.lastmodifiedms() << ", "
              << "properties count: " << init_event.properties_size() << std::endl;

    if (!config_.propertiesJsonPath.empty() && !WritePropertiesToFile(init_event)) {
        std::cerr << "AgentChannelClient: Failed to write initial properties to file" << std::endl;
    }
}

void AgentChannelClient::HandlePropertyUpdate(const com::fyordo::cms::ServerPropertyUpdateEvent& update_event)
{
    std::cout << "AgentChannelClient: Received ServerPropertyUpdateEvent - "
              << "key: " << update_event.property().key() << ", "
              << "lastModifiedMs: " << update_event.lastmodifiedms() << std::endl;

    if (config_.propertiesJsonPath.empty()) {
        std::cerr << "AgentChannelClient: Skipping property update (CMS_PROPERTIES_FILE not set)" << std::endl;
        return;
    }
    if (!ApplyPropertyUpdateToFile(update_event.property().key(), update_event.property().value())) {
        std::cerr << "AgentChannelClient: Failed to apply property update for key "
                  << update_event.property().key() << std::endl;
        return;
    }

    SendUpdateToUnixSocket(update_event.property().key(), update_event.property().value());
}

bool AgentChannelClient::ApplyPropertyUpdateToFile(const std::string& key, const std::string& value)
{
    nlohmann::json properties_json;
    {
        std::ifstream in(config_.propertiesJsonPath);
        if (in.good()) {
            try {
                in >> properties_json;
            } catch (const nlohmann::json::exception&) {
                properties_json = nlohmann::json::object();
            }
        }
    }
    // Property.value is bytes in proto; we store as string. For valid UTF-8 the JSON is correct.
    properties_json[key] = value;

    if (!WriteJsonToPath(properties_json)) {
        return false;
    }
    std::cout << "AgentChannelClient: Updated key " << key << " in " << config_.propertiesJsonPath << std::endl;
    return true;
}

bool AgentChannelClient::WritePropertiesToFile(const com::fyordo::cms::ServerInitEvent& init_event)
{
    nlohmann::json properties_json;
    for (const auto& prop : init_event.properties()) {
        properties_json[prop.key()] = prop.value();
    }
    if (!WriteJsonToPath(properties_json)) {
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
    std::lock_guard<std::mutex> lock(file_write_mutex_);
    AGENT_STATE.store(AgentState::WRITING);

    const std::string tmp_path = config_.propertiesJsonPath + ".tmp";
    {
        std::ofstream file(tmp_path);
        if (!file) {
            std::cerr << "AgentChannelClient: Failed to open file for writing: " << config_.propertiesJsonPath
                      << std::endl;
            AGENT_STATE.store(AgentState::LISTENING);
            return false;
        }
        file << j.dump();
        file.flush();
        if (!file.good()) {
            std::cerr << "AgentChannelClient: Write or flush failed: " << tmp_path << std::endl;
            AGENT_STATE.store(AgentState::LISTENING);
            return false;
        }
    }
    if (std::rename(tmp_path.c_str(), config_.propertiesJsonPath.c_str()) != 0) {
        std::cerr << "AgentChannelClient: Failed to rename temp file to " << config_.propertiesJsonPath
                  << std::endl;
        std::remove(tmp_path.c_str());
        AGENT_STATE.store(AgentState::LISTENING);
        return false;
    }
    AGENT_STATE.store(AgentState::LISTENING);
    return true;
}

void AgentChannelClient::SendUpdateToUnixSocket(const std::string& key, const std::string& value)
{
    if (config_.unixSocketPath.empty()) {
        return;
    }

    int sock_fd = ::socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock_fd == -1) {
        std::cerr << "AgentChannelClient: Failed to create UNIX socket" << std::endl;
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
        std::cerr << "AgentChannelClient: Failed to connect to UNIX socket at "
                  << config_.unixSocketPath << std::endl;
        ::close(sock_fd);
        return;
    }

    const uint32_t key_len = static_cast<uint32_t>(key.size());
    const uint32_t value_len = static_cast<uint32_t>(value.size());

    uint32_t key_len_be = htonl(key_len);
    uint32_t value_len_be = htonl(value_len);

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

    if (!send_all(&key_len_be, sizeof(key_len_be)) ||
        !send_all(key.data(), key.size()) ||
        !send_all(&value_len_be, sizeof(value_len_be)) ||
        !send_all(value.data(), value.size())) {
        std::cerr << "AgentChannelClient: Failed to send update to UNIX socket at "
                  << config_.unixSocketPath << std::endl;
    }

    ::close(sock_fd);
}

bool AgentChannelClient::WaitForConnected(grpc::Channel* channel)
{
    auto deadline = std::chrono::system_clock::now() + CONNECTION_TIMEOUT;
    if (channel->WaitForConnected(deadline)) {
        AGENT_STATE.store(AgentState::LISTENING);
        std::cout << "AgentChannelClient: Connected to server at " << server_address_ << std::endl;
        return true;
    }
    std::cerr << "AgentChannelClient: Failed to connect to server at " << server_address_ << std::endl;
    return false;
}
