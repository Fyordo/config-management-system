#pragma once

#include <string>
#include <atomic>

struct AgentConfig {
    std::string ns;
    std::string service;
    std::string appId;
    std::string cmsServerHost;
    std::string propertiesJsonPath;
    std::string unixSocketPath;
    std::string cmsRevisionFilePath;

    bool isValid();
};

class GrpcServerStarter {
public:
    void RunServer();
private:
    AgentConfig BuildConfig();
    void SetupSignalHandlers();
};