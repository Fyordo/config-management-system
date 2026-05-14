#pragma once

#include <string>

#include "tls_config.h"

struct AgentConfig {
    std::string ns;
    std::string service;
    std::string appId;
    std::string cms_server_host;
    std::string properties_json_path;
    std::string unix_socket_path;
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
