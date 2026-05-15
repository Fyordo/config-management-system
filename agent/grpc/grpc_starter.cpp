#include <chrono>
#include <csignal>
#include <cerrno>
#include <cstdlib>
#include <fcntl.h>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>
#include <unistd.h>

#include "agent_channel_client.h"
#include "grpc_starter.h"
#include "tls_config.h"

std::atomic<bool> g_shutdown_requested{false};
int g_shutdown_pipe[2] = {-1, -1};
void SignalHandler(int);

namespace {

    std::string GetEnvOrDefault(const char* name, const std::string& default_value)
    {
        const char* value = std::getenv(name);
        return (value != nullptr && value[0] != '\0') ? std::string(value) : default_value;
    }

    void PrintMissingEnvErrors(const AgentConfig& config)
    {
        std::cerr << "Error: Required environment variables are not set:" << std::endl;
        if (config.ns.empty()) std::cerr << "  - CMS_NAMESPACE" << std::endl;
        if (config.service.empty()) std::cerr << "  - CMS_SERVICE" << std::endl;
        if (config.appId.empty()) std::cerr << "  - CMS_APPID" << std::endl;
        if (config.cms_server_host.empty()) std::cerr << "  - CMS_SERVER_HOST" << std::endl;
        std::cerr << "Please set all required environment variables before starting the agent." << std::endl;
    }

    void PrintAgentConfig(const AgentConfig& config)
    {
        std::cout << "Agent configuration:" << std::endl;
        std::cout << "  CMS_NAMESPACE: " << config.ns << std::endl;
        std::cout << "  CMS_SERVICE: " << config.service << std::endl;
        std::cout << "  CMS_APPID: " << config.appId << std::endl;
        std::cout << "  CMS_SERVER_HOST: " << config.cms_server_host << std::endl;
        PrintTlsConfig(config.tls);
        if (!config.properties_json_path.empty()) {
            std::cout << "  CMS_PROPERTIES_FILE: " << config.properties_json_path << std::endl;
        }
        std::cout << "  CMS_ACK_INTERVAL_SECONDS: " << config.ack_interval_seconds << std::endl;
        std::cout << "  CMS_MAX_FAILED_QUEUE_SIZE: " << config.max_failed_queue_size << std::endl;
    }

    void RegisterSignalOrThrow(int signo)
    {
        struct sigaction sa {};
        sa.sa_handler = SignalHandler;
        sigemptyset(&sa.sa_mask);
        sigaddset(&sa.sa_mask, SIGINT);
        sigaddset(&sa.sa_mask, SIGTERM);
        sa.sa_flags = SA_RESTART;

        if (::sigaction(signo, &sa, nullptr) != 0) {
            throw std::runtime_error("Failed to register signal handler via sigaction");
        }
    }

}

void SignalHandler(int)
{
    g_shutdown_requested.store(true);
    if (g_shutdown_pipe[1] != -1) {
        const char dummy = 1;
        if (::write(g_shutdown_pipe[1], &dummy, 1) == -1 && errno != EAGAIN && errno != EWOULDBLOCK) {
            // Non-blocking write end: EAGAIN means buffer full; shutdown flag is already set.
        }
    }
}

void GrpcServerStarter::SetupSignalHandlers()
{
    if (::pipe(g_shutdown_pipe) != 0) {
        std::cerr << "Failed to create shutdown pipe, falling back to polling" << std::endl;
    } else {
        const int wfd = g_shutdown_pipe[1];
        int flags = ::fcntl(wfd, F_GETFL, 0);
        if (flags == -1 || ::fcntl(wfd, F_SETFL, flags | O_NONBLOCK) == -1) {
            std::cerr << "Failed to set shutdown pipe write end non-blocking, closing pipe" << std::endl;
            ::close(g_shutdown_pipe[0]);
            ::close(g_shutdown_pipe[1]);
            g_shutdown_pipe[0] = -1;
            g_shutdown_pipe[1] = -1;
        }
    }
    RegisterSignalOrThrow(SIGINT);
    RegisterSignalOrThrow(SIGTERM);
}

void GrpcServerStarter::RunServer()
{
    AgentConfig config = BuildConfig();
    PrintAgentConfig(config);
    SetupSignalHandlers();

    AgentChannelClient client(config);
    client.Start();
    std::cout << "AgentChannelClient: Started, connecting to " << config.cms_server_host << std::endl;
    std::cout << "Agent running, press Ctrl+C to stop..." << std::endl;

    if (g_shutdown_pipe[0] != -1) {
        char dummy;
        while (::read(g_shutdown_pipe[0], &dummy, 1) == -1 && errno == EINTR) {
        }
        ::close(g_shutdown_pipe[0]);
        ::close(g_shutdown_pipe[1]);
        g_shutdown_pipe[0] = -1;
        g_shutdown_pipe[1] = -1;
    } else {
        while (!g_shutdown_requested.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }

    std::cout << "Shutting down agent..." << std::endl;
    client.Stop();
    std::cout << "Agent stopped successfully" << std::endl;
}

AgentConfig GrpcServerStarter::BuildConfig()
{
    AgentConfig config;
    config.ns = GetEnvOrDefault("CMS_NAMESPACE", "");
    config.service = GetEnvOrDefault("CMS_SERVICE", "");
    config.appId = GetEnvOrDefault("CMS_APPID", "");
    config.cms_server_host = GetEnvOrDefault("CMS_SERVER_HOST", "");

    config.properties_json_path = GetEnvOrDefault("CMS_PROPERTIES_FILE", "");
    config.unix_socket_path = GetEnvOrDefault("CMS_UNIX_SOCKET_PATH", "");
    
    std::string ack_interval_str = GetEnvOrDefault("CMS_ACK_INTERVAL_SECONDS", "10");
    try {
        config.ack_interval_seconds = std::stoi(ack_interval_str);
    } catch (...) {
        config.ack_interval_seconds = 10;
    }

    std::string max_failed_str = GetEnvOrDefault("CMS_MAX_FAILED_QUEUE_SIZE", "100");
    try {
        config.max_failed_queue_size = std::stoi(max_failed_str);
    } catch (...) {
        config.max_failed_queue_size = 100;
    }

    config.tls = BuildTlsConfigFromEnv();

    if (!config.isValid()) {
        PrintMissingEnvErrors(config);
        throw std::runtime_error("Invalid configuration");
    }
    if (config.tls.enabled) {
        (void)CreateChannelCredentials(config.tls);
    }
    return config;
}

bool AgentConfig::isValid() const
{
    if (ns.empty() || service.empty() || appId.empty()) return false;
    if (cms_server_host.empty()) return false;
    if (!tls.isValid()) return false;
    return true;
}
