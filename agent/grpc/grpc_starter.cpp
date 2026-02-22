#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <cstdlib>
#include <thread>
#include <chrono>
#include <csignal>
#include <atomic>

#include <grpcpp/grpcpp.h>
#include "agent_channel_client.h"
#include "grpc_starter.h"

std::atomic<bool> g_shutdown_requested{false};

void SignalHandler(int signal) {
    std::cout << "\nReceived signal " << signal << ", initiating shutdown..." << std::endl;
    g_shutdown_requested.store(true);
}

void SetupSignalHandlers() {
    std::signal(SIGINT, SignalHandler);
    std::signal(SIGTERM, SignalHandler);
}

void RunServer(int, char**) {
    AgentConfig config = GetAndValidateConfigFromEnv();
    
    std::cout << "Agent configuration:" << std::endl;
    std::cout << "  CMS_NAMESPACE: " << config.namespace_ << std::endl;
    std::cout << "  CMS_SERVICE: " << config.service << std::endl;
    std::cout << "  CMS_APPID: " << config.appId << std::endl;
    std::cout << "  CMS_SERVER_HOST: " << config.cmsServerHost << std::endl;
    if (!config.propertiesJsonPath.empty()) {
        std::cout << "  CMS_PROPERTIES_FILE: " << config.propertiesJsonPath << std::endl;
    }
    
    SetupSignalHandlers();
    
    AgentChannelClient client(config, config.cmsServerHost);
    client.Start();
    std::cout << "AgentChannelClient: Started, connecting to " << config.cmsServerHost << std::endl;
    
    std::cout << "Agent running, press Ctrl+C to stop..." << std::endl;
    while (!g_shutdown_requested.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    
    std::cout << "Shutting down agent..." << std::endl;
    client.Stop();
    std::cout << "Agent stopped successfully" << std::endl;
}

AgentConfig GetAndValidateConfigFromEnv() {
    AgentConfig config;

    const char* namespace_env = std::getenv("CMS_NAMESPACE");
    const char* service_env = std::getenv("CMS_SERVICE");
    const char* appid_env = std::getenv("CMS_APPID");
    const char* cmsServerHost_env = std::getenv("CMS_SERVER_HOST");
    const char* propertiesFile_env = std::getenv("CMS_PROPERTIES_FILE");

    if (namespace_env != nullptr) {
        config.namespace_ = std::string(namespace_env);
    }
    
    if (service_env != nullptr) {
        config.service = std::string(service_env);
    }
    
    if (appid_env != nullptr) {
        config.appId = std::string(appid_env);
    }
    
    if (cmsServerHost_env != nullptr) {
        config.cmsServerHost = std::string(cmsServerHost_env);
    }

    if (propertiesFile_env != nullptr) {
        config.propertiesJsonPath = std::string(propertiesFile_env);
    }

    if (!config.IsValid()) {
        std::cerr << "Error: Required environment variables are not set:" << std::endl;
        if (config.namespace_.empty()) std::cerr << "  - CMS_NAMESPACE" << std::endl;
        if (config.service.empty()) std::cerr << "  - CMS_SERVICE" << std::endl;
        if (config.appId.empty()) std::cerr << "  - CMS_APPID" << std::endl;
        if (config.cmsServerHost.empty()) std::cerr << "  - CMS_SERVER_HOST" << std::endl;
        std::cerr << "Please set all required environment variables before starting the agent." << std::endl;
        throw std::runtime_error("Invalid configuration");
    }

    return config;
}