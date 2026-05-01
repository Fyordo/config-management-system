#pragma once

#include <string>
#include <atomic>

#include "tls_config.h"

struct AgentConfig {
    std::string ns;
    std::string service;
    std::string appId;
    std::string cmsServerHost;
    std::string propertiesJsonPath;
    std::string unixSocketPath;
    std::string cmsRevisionFilePath;
    TlsConfig tls;

    bool isValid() const;
};

class GrpcServerStarter {
public:
    void RunServer();
private:
    AgentConfig BuildConfig();
    void SetupSignalHandlers();
};