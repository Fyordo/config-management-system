#include <chrono>
#include <csignal>
#include <cerrno>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>
#include <unistd.h>

#include "agent_channel_client.h"
#include "grpc_starter.h"

std::atomic<bool> g_shutdown_requested{false};
int g_shutdown_pipe[2] = {-1, -1};

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
        if (config.cmsServerHost.empty()) std::cerr << "  - CMS_SERVER_HOST" << std::endl;
        std::cerr << "Please set all required environment variables before starting the agent." << std::endl;
    }

    void PrintAgentConfig(const AgentConfig& config)
    {
        std::cout << "Agent configuration:" << std::endl;
        std::cout << "  CMS_NAMESPACE: " << config.ns << std::endl;
        std::cout << "  CMS_SERVICE: " << config.service << std::endl;
        std::cout << "  CMS_APPID: " << config.appId << std::endl;
        std::cout << "  CMS_SERVER_HOST: " << config.cmsServerHost << std::endl;
        PrintTlsConfig(config.tls);
        if (!config.propertiesJsonPath.empty()) {
            std::cout << "  CMS_PROPERTIES_FILE: " << config.propertiesJsonPath << std::endl;
        }
        if (!config.cmsRevisionFilePath.empty()) {
            std::cout << "  CMS_REVISION_FILE: " << config.cmsRevisionFilePath << std::endl;
        }
    }

}

void SignalHandler(int)
{
    g_shutdown_requested.store(true);
    if (g_shutdown_pipe[1] != -1) {
        const char dummy = 1;
        ::write(g_shutdown_pipe[1], &dummy, 1);
    }
}

void GrpcServerStarter::SetupSignalHandlers()
{
    if (::pipe(g_shutdown_pipe) != 0) {
        std::cerr << "Failed to create shutdown pipe, falling back to polling" << std::endl;
    }
    std::signal(SIGINT, SignalHandler);
    std::signal(SIGTERM, SignalHandler);
}

void GrpcServerStarter::RunServer()
{
    AgentConfig config = BuildConfig();
    PrintAgentConfig(config);
    SetupSignalHandlers();

    AgentChannelClient client(config, config.cmsServerHost);
    client.Start();
    std::cout << "AgentChannelClient: Started, connecting to " << config.cmsServerHost << std::endl;
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
    config.cmsServerHost = GetEnvOrDefault("CMS_SERVER_HOST", "");
    config.propertiesJsonPath = GetEnvOrDefault("CMS_PROPERTIES_FILE", "");
    config.unixSocketPath = GetEnvOrDefault("CMS_UNIX_SOCKET_PATH", "");
    config.cmsRevisionFilePath = GetEnvOrDefault("CMS_REVISION_FILE", "");
    config.tls = BuildTlsConfigFromEnv();

    if (!config.isValid()) {
        PrintMissingEnvErrors(config);
        throw std::runtime_error("Invalid configuration");
    }
    return config;
}

bool AgentConfig::isValid() const
{
    if (ns.empty() || service.empty() || appId.empty()) return false;
    if (!tls.isValid()) return false;
    return true;
}
