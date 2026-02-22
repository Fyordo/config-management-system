#pragma once

#include <memory>
#include <string>
#include <thread>
#include <atomic>

#include "grpc_starter.h"
#include "AgentChannelService.pb.h"

class AgentChannelClient {
public:
    AgentChannelClient(const AgentConfig& config, const std::string& server_address);
    ~AgentChannelClient();

    AgentChannelClient(const AgentChannelClient&) = delete;
    AgentChannelClient& operator=(const AgentChannelClient&) = delete;
    AgentChannelClient(AgentChannelClient&&) = delete;
    AgentChannelClient& operator=(AgentChannelClient&&) = delete;

    void Start();
    void Stop();

private:
    static constexpr std::chrono::seconds RECONNECT_DELAY{5};
    static constexpr std::chrono::seconds CONNECTION_TIMEOUT{5};

    void Run();
    void RunStreamSession(void* stream, std::atomic<bool>& is_reading);
    void HandleInitEvent(const com::fyordo::cms::ServerInitEvent& init_event);
    void HandlePropertyUpdate(const com::fyordo::cms::ServerPropertyUpdateEvent& update_event);
    bool WritePropertiesToFile(const com::fyordo::cms::ServerInitEvent& init_event);
    bool WaitForConnected(grpc::Channel* channel);

    AgentConfig config_;
    std::string server_address_;
    std::atomic<bool> running_;
    std::thread client_thread_;
};
