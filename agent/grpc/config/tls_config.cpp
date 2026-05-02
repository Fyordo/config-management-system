#include "tls_config.h"

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <stdexcept>

namespace {

    std::string GetEnvOrDefault(const char* name, const std::string& default_value)
    {
        const char* value = std::getenv(name);
        return (value != nullptr && value[0] != '\0') ? std::string(value) : default_value;
    }
    
    bool ParseBoolEnv(const char* name, bool default_value)
    {
        const char* value = std::getenv(name);
        if (value == nullptr || value[0] == '\0') return default_value;
        std::string s(value);
        return s == "true" || s == "1" || s == "yes" || s == "TRUE" || s == "YES";
    }
    
    std::string ReadFileContent(const std::string& path)
    {
        std::ifstream file(path);
        if (!file.good()) return {};
        return std::string(std::istreambuf_iterator<char>(file),
                        std::istreambuf_iterator<char>());
    }

    grpc::SslCredentialsOptions BuildSslOptionsOrThrow(const TlsConfig& config)
    {
        grpc::SslCredentialsOptions ssl_options;

        if (!config.caCertPath.empty()) {
            std::string ca_pem = ReadFileContent(config.caCertPath);
            if (ca_pem.empty()) {
                throw std::runtime_error(
                    "TLS enabled: failed to read CA certificate file: " + config.caCertPath
                );
            }
            ssl_options.pem_root_certs = std::move(ca_pem);
        }

        if (!config.clientCertPath.empty() && !config.clientKeyPath.empty()) {
            std::string cert_pem = ReadFileContent(config.clientCertPath);
            std::string key_pem = ReadFileContent(config.clientKeyPath);
            if (cert_pem.empty()) {
                throw std::runtime_error(
                    "TLS enabled: failed to read client certificate file: " + config.clientCertPath
                );
            }
            if (key_pem.empty()) {
                throw std::runtime_error(
                    "TLS enabled: failed to read client private key file: " + config.clientKeyPath
                );
            }
            ssl_options.pem_cert_chain = std::move(cert_pem);
            ssl_options.pem_private_key = std::move(key_pem);
        }

        return ssl_options;
    }

}

TlsConfig BuildTlsConfigFromEnv()
{
    TlsConfig config;
    config.enabled = ParseBoolEnv("CMS_TLS", false);
    config.caCertPath = GetEnvOrDefault("CMS_TLS_CA_CERT", "");
    config.clientCertPath = GetEnvOrDefault("CMS_TLS_CLIENT_CERT", "");
    config.clientKeyPath = GetEnvOrDefault("CMS_TLS_CLIENT_KEY", "");
    return config;
}

void PrintTlsConfig(const TlsConfig& config)
{
    std::cout << "  CMS_TLS: " << (config.enabled ? "true" : "false") << std::endl;
    if (!config.caCertPath.empty()) {
        std::cout << "  CMS_TLS_CA_CERT: " << config.caCertPath << std::endl;
    }
    if (!config.clientCertPath.empty()) {
        std::cout << "  CMS_TLS_CLIENT_CERT: " << config.clientCertPath << std::endl;
    }
    if (!config.clientKeyPath.empty()) {
        std::cout << "  CMS_TLS_CLIENT_KEY: " << config.clientKeyPath << std::endl;
    }
}

bool TlsConfig::isValid() const
{
    if (!enabled) return true;
    if (!clientCertPath.empty() != !clientKeyPath.empty()) return false;
    return true;
}

std::shared_ptr<grpc::ChannelCredentials> CreateChannelCredentials(const TlsConfig& config)
{
    if (!config.enabled) {
        return grpc::InsecureChannelCredentials();
    }

    grpc::SslCredentialsOptions ssl_options = BuildSslOptionsOrThrow(config);

    std::cout << "CreateChannelCredentials: Using TLS"
              << (ssl_options.pem_cert_chain.empty() ? "" : " with client certificate (mTLS)")
              << (ssl_options.pem_root_certs.empty() ? " (system CA roots)" : " (custom CA)")
              << std::endl;

    return grpc::SslCredentials(ssl_options);
}
