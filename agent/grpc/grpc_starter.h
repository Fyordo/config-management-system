#pragma once

#include <string>

struct AgentConfig {
    std::string namespace_;
    std::string service;
    std::string appId;
    std::string cmsServerHost;
    
    bool IsValid() const {
        return !namespace_.empty() && !service.empty() && !appId.empty() && !cmsServerHost.empty();
    }
};

void RunServer(int argc, char** argv);
AgentConfig GetAndValidateConfigFromEnv();