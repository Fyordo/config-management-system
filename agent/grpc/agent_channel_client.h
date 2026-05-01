#pragma once

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
#include <cstring>
#include <cerrno>

#include "AgentChannelService.grpc.pb.h"
#include "grpc_starter.h"

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

    static constexpr std::chrono::seconds ACK_INTERVAL{10};

    static constexpr int KEEPALIVE_TIME_MS       = 10'000;
    static constexpr int KEEPALIVE_TIMEOUT_MS    =  5'000;

    void Run();
    void RunStreamSession(grpc::ClientReaderWriterInterface<com::fyordo::cms::AgentStreamEvent, com::fyordo::cms::ServerStreamEvent>* stream, std::atomic<bool>& is_reading);
    void SendAck(grpc::ClientReaderWriterInterface<com::fyordo::cms::AgentStreamEvent, com::fyordo::cms::ServerStreamEvent>* stream, std::atomic<bool>& is_reading, std::atomic<bool>& is_writing);
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
    nlohmann::json properties_cache_;
    std::mutex properties_write_mutex_;
    std::mutex revision_write_mutex_;
    std::mutex stream_mutex_;
    std::condition_variable stream_cv_;

    std::shared_ptr<grpc::Channel> channel_;
    std::unique_ptr<com::fyordo::cms::AgentChannelService::Stub> stub_;
};
