#pragma once

#include <string>
#include <atomic>

struct AgentConfig {
    std::string namespace_;
    std::string service;
    std::string appId;
    std::string cmsServerHost;
    
    bool IsValid() const {
        return !namespace_.empty() && !service.empty() && !appId.empty() && !cmsServerHost.empty();
    }
};

extern std::atomic<bool> g_shutdown_requested;

void RunServer(int argc, char** argv);
AgentConfig GetAndValidateConfigFromEnv();
void SetupSignalHandlers();