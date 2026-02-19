#pragma once

#include <memory>
#include <string>
#include <thread>
#include <atomic>
#include <grpcpp/grpcpp.h>

#include "grpc_starter.h"

// Forward declarations
namespace com {
namespace fyordo {
namespace cms {
class ServerStreamEvent;
}  // namespace cms
}  // namespace fyordo
}  // namespace com

class AgentChannelClient {
public:
    AgentChannelClient(const AgentConfig& config, const std::string& server_address);
    ~AgentChannelClient();
    
    // Delete copy and move operations
    AgentChannelClient(const AgentChannelClient&) = delete;
    AgentChannelClient& operator=(const AgentChannelClient&) = delete;
    AgentChannelClient(AgentChannelClient&&) = delete;
    AgentChannelClient& operator=(AgentChannelClient&&) = delete;
    
    void Start();
    void Stop();
    
private:
    // Configuration constants
    static constexpr std::chrono::seconds RECONNECT_DELAY{5};
    static constexpr std::chrono::seconds CONNECTION_TIMEOUT{5};
    
    void Run();
    // Use void* to avoid forward declaration issues with template aliases
    // Implementation will cast to proper type in .cpp file
    void RunReadLoop(void* stream, std::atomic<bool>& reading);
    bool ConnectToServer(std::shared_ptr<grpc::Channel>& channel);

    AgentConfig config_;
    std::string server_address_;
    std::atomic<bool> running_;
    std::thread client_thread_;
};
