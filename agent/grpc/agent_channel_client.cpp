#include "agent_channel_client.h"

#include <cerrno>
#include <cstring>
#include <chrono>
#include <fstream>
#include <iostream>
#include <grpcpp/grpcpp.h>
#include <nlohmann/json.hpp>

#include "AgentChannelService.grpc.pb.h"
#include "grpc_starter.h"

using grpc::ClientContext;
using grpc::ClientReader;
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
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
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
    auto* stream = static_cast<grpc::ClientReaderInterface<ServerStreamEvent>*>(stream_ptr);
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

    if (!config_.propertiesJsonPath.empty()) {
        WritePropertiesToFile(init_event);
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
    ApplyPropertyUpdateToFile(update_event.property().key(), update_event.property().value());
}

bool AgentChannelClient::ApplyPropertyUpdateToFile(const std::string& key, const std::string& value)
{
    AGENT_STATE.store(AgentState::WRITING);

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

    properties_json[key] = value;

    std::ofstream file(config_.propertiesJsonPath);
    if (!file) {
        const char* error_message = (errno != 0) ? std::strerror(errno) : "unknown error";
        std::cerr << "AgentChannelClient: Failed to open " << config_.propertiesJsonPath
                  << " for writing: " << error_message << std::endl;
        AGENT_STATE.store(AgentState::LISTENING);
        return false;
    }
    file << properties_json.dump();
    file.flush();
    std::cout << "AgentChannelClient: Updated key " << key << " in " << config_.propertiesJsonPath << std::endl;
    AGENT_STATE.store(AgentState::LISTENING);
    return true;
}

bool AgentChannelClient::WritePropertiesToFile(const com::fyordo::cms::ServerInitEvent& init_event)
{
    AGENT_STATE.store(AgentState::WRITING);

    nlohmann::json properties_json;
    for (const auto& prop : init_event.properties()) {
        properties_json[prop.key()] = prop.value();
    }

    std::ofstream file(config_.propertiesJsonPath);
    if (!file) {
        const char* error_message = (errno != 0) ? std::strerror(errno) : "unknown error";
        std::cerr << "AgentChannelClient: Failed to open " << config_.propertiesJsonPath
                  << " for writing: " << error_message
                  << " (use a path writable by the process, e.g. /app/application.json)"
                  << std::endl;
        AGENT_STATE.store(AgentState::LISTENING);
        return false;
    }
    file << properties_json.dump();
    std::cout << "AgentChannelClient: Wrote " << init_event.properties_size()
              << " properties to " << config_.propertiesJsonPath << std::endl;
    AGENT_STATE.store(AgentState::LISTENING);
    return true;
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
