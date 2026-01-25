#pragma once

#include <memory>
#include <string>
#include <thread>
#include <atomic>

#include "grpc_starter.h"

class AgentChannelClient {
public:
    AgentChannelClient(const AgentConfig& config, const std::string& server_address);
    ~AgentChannelClient();
    
    void Start();
    void Stop();
    
private:
    void Run();

    bool ConnectToServer(std::shared_ptr<grpc_impl::Channel> &channel);

    AgentConfig config_;
    std::string server_address_;
    std::atomic<bool> running_;
    std::thread client_thread_;
};
