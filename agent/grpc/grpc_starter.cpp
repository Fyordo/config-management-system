#include <chrono>
#include <csignal>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>

#include "agent_channel_client.h"
#include "grpc_starter.h"

std::atomic<bool> g_shutdown_requested{false};
std::atomic<AgentState> AGENT_STATE{AgentState::CONNECT};

namespace {

    constexpr std::chrono::milliseconds MAIN_LOOP_POLL_MS{100};

    std::string GetEnvOrDefault(const char* name, const std::string& default_value)
    {
        const char* value = std::getenv(name);
        return (value != nullptr && value[0] != '\0') ? std::string(value) : default_value;
    }

    void PrintMissingEnvErrors(const AgentConfig& config)
    {
        std::cerr << "Error: Required environment variables are not set:" << std::endl;
        if (config.namespace_.empty()) std::cerr << "  - CMS_NAMESPACE" << std::endl;
        if (config.service.empty()) std::cerr << "  - CMS_SERVICE" << std::endl;
        if (config.appId.empty()) std::cerr << "  - CMS_APPID" << std::endl;
        if (config.cmsServerHost.empty()) std::cerr << "  - CMS_SERVER_HOST" << std::endl;
        std::cerr << "Please set all required environment variables before starting the agent." << std::endl;
    }

    void PrintAgentConfig(const AgentConfig& config)
    {
        std::cout << "Agent configuration:" << std::endl;
        std::cout << "  CMS_NAMESPACE: " << config.namespace_ << std::endl;
        std::cout << "  CMS_SERVICE: " << config.service << std::endl;
        std::cout << "  CMS_APPID: " << config.appId << std::endl;
        std::cout << "  CMS_SERVER_HOST: " << config.cmsServerHost << std::endl;
        if (!config.propertiesJsonPath.empty()) {
            std::cout << "  CMS_PROPERTIES_FILE: " << config.propertiesJsonPath << std::endl;
        }
    }

}

void SignalHandler(int signal)
{
    std::cout << "\nReceived signal " << signal << ", initiating shutdown..." << std::endl;
    g_shutdown_requested.store(true);
}

void SetupSignalHandlers()
{
    std::signal(SIGINT, SignalHandler);
    std::signal(SIGTERM, SignalHandler);
}

void RunServer(int argc, char** argv)
{
    (void)argc;
    (void)argv;
    AGENT_STATE.store(AgentState::CONNECT);
    AgentConfig config = GetAndValidateConfigFromEnv();
    PrintAgentConfig(config);
    SetupSignalHandlers();

    AgentChannelClient client(config, config.cmsServerHost);
    client.Start();
    std::cout << "AgentChannelClient: Started, connecting to " << config.cmsServerHost << std::endl;
    std::cout << "Agent running, press Ctrl+C to stop..." << std::endl;

    while (!g_shutdown_requested.load()) {
        std::this_thread::sleep_for(MAIN_LOOP_POLL_MS);
    }

    std::cout << "Shutting down agent..." << std::endl;
    client.Stop();
    std::cout << "Agent stopped successfully" << std::endl;
}

AgentConfig GetAndValidateConfigFromEnv()
{
    AgentConfig config;
    config.namespace_ = GetEnvOrDefault("CMS_NAMESPACE", "");
    config.service = GetEnvOrDefault("CMS_SERVICE", "");
    config.appId = GetEnvOrDefault("CMS_APPID", "");
    config.cmsServerHost = GetEnvOrDefault("CMS_SERVER_HOST", "");
    config.propertiesJsonPath = GetEnvOrDefault("CMS_PROPERTIES_FILE", "");

    if (!config.IsValid()) {
        PrintMissingEnvErrors(config);
        throw std::runtime_error("Invalid configuration");
    }
    return config;
}
