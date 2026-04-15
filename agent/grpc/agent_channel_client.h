#pragma once

#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <atomic>

#include <grpcpp/grpcpp.h>
#include <nlohmann/json.hpp>
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
    static constexpr std::chrono::milliseconds POLL_INTERVAL_MS{100};
    static constexpr std::chrono::seconds ACK_INTERVAL{10};

    static constexpr int KEEPALIVE_TIME_MS       = 10'000;
    static constexpr int KEEPALIVE_TIMEOUT_MS    =  5'000;

    void Run();
    void RunStreamSession(void* stream, std::atomic<bool>& is_reading);
    void SendAckToServer(void* stream, int64_t revision);
    void HandleInitEvent(const com::fyordo::cms::ServerInitEvent& init_event);
    void HandlePropertyUpdate(const com::fyordo::cms::ServerPropertyUpdateEvent& update_event);
    bool WritePropertiesToFile(const com::fyordo::cms::ServerInitEvent& init_event);
    bool ApplyPropertyUpdateToFile(const std::string& key, const std::string& value);
    void SendUpdateToUnixSocket(const com::fyordo::cms::Property& property);
    bool WaitForConnected(grpc::Channel* channel);
    bool WriteJsonToPath(const nlohmann::json& j);
    bool WriteRevisionToFile(int64_t revision);
    int64_t ReadRevisionFromFile();

    AgentConfig config_;
    std::string server_address_;
    std::atomic<bool> running_;
    std::atomic<int64_t> current_revision_;
    std::thread client_thread_;
    std::mutex file_write_mutex_;
};
