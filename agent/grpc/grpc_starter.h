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
    int ack_interval_seconds;
    int max_failed_queue_size;
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
