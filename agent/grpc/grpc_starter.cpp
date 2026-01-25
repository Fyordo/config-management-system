#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <cstdlib>
#include <thread>
#include <chrono>

#include <grpcpp/grpcpp.h>
#include "agent_channel_client.h"
#include "grpc_starter.h"

void RunServer(int argc, char** argv) {
    AgentConfig config = GetAndValidateConfigFromEnv();
    
    std::cout << "Agent configuration:" << std::endl;
    std::cout << "  CMS_NAMESPACE: " << config.namespace_ << std::endl;
    std::cout << "  CMS_SERVICE: " << config.service << std::endl;
    std::cout << "  CMS_APPID: " << config.appId << std::endl;
    std::cout << "  CMS_SERVER_HOST: " << config.cmsServerHost << std::endl;
    
    AgentChannelClient client(config, config.cmsServerHost);
    client.Start();
    std::cout << "AgentChannelClient: Started, connecting to " << config.cmsServerHost << std::endl;
    
    std::cout << "Agent running, waiting for shutdown signal..." << std::endl;
    while (true) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    
    client.Stop();
}

AgentConfig GetAndValidateConfigFromEnv() {
    AgentConfig config;
    
    const char* namespace_env = std::getenv("CMS_NAMESPACE");
    const char* service_env = std::getenv("CMS_SERVICE");
    const char* appid_env = std::getenv("CMS_APPID");
    const char* cmsServerHost_env = std::getenv("CMS_SERVER_HOST");
    
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
    
    if (!config.IsValid()) {
        std::cerr << "Error: Required environment variables are not set:" << std::endl;
        if (config.namespace_.empty()) std::cerr << "  - CMS_NAMESPACE" << std::endl;
        if (config.service.empty()) std::cerr << "  - CMS_SERVICE" << std::endl;
        if (config.appId.empty()) std::cerr << "  - CMS_APPID" << std::endl;
        if (config.cmsServerHost.empty()) std::cerr << "  - CMS_SERVER_HOST" << std::endl;
        std::cerr << "Please set all required environment variables before starting the agent." << std::endl;
        exit(1);
    }

    return config;
}