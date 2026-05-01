#pragma once

#include <memory>
#include <string>

#include <grpcpp/grpcpp.h>

struct TlsConfig {
    bool enabled = false;
    std::string caCertPath;
    std::string clientCertPath;
    std::string clientKeyPath;

    bool isValid() const;
};

TlsConfig BuildTlsConfigFromEnv();
void PrintTlsConfig(const TlsConfig& config);
std::shared_ptr<grpc::ChannelCredentials> CreateChannelCredentials(const TlsConfig& config);
