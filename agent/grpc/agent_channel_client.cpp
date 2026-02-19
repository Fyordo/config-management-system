#include "agent_channel_client.h"

#include <iostream>
#include <chrono>
#include <functional>
#include <grpcpp/grpcpp.h>
#include "AgentChannelService.grpc.pb.h"
#include "AgentChannelService.pb.h"

using grpc::ClientContext;
using grpc::ClientReader;
using com::fyordo::cms::AgentChannelService;
using com::fyordo::cms::ServerStreamEvent;
using com::fyordo::cms::AgentConnectRequest;

AgentChannelClient::AgentChannelClient(const AgentConfig& config, const std::string& server_address)
    : config_(config)
    , server_address_(server_address)
    , running_(false)
{
}

AgentChannelClient::~AgentChannelClient() {
    Stop();
}

void AgentChannelClient::Start() {
    bool expected = false;
    if (!running_.compare_exchange_strong(expected, true)) {
        std::cerr << "AgentChannelClient: Already running" << std::endl;
        return;
    }
    
    client_thread_ = std::thread(&AgentChannelClient::Run, this);
}

void AgentChannelClient::Stop() {
    bool expected = true;
    if (!running_.compare_exchange_strong(expected, false)) {
        return;
    }
    
    if (client_thread_.joinable()) {
        client_thread_.join();
    }
}

void AgentChannelClient::Run() {
    while (running_.load()) {
        try {
            auto channel = grpc::CreateChannel(server_address_, grpc::InsecureChannelCredentials());
            auto stub = AgentChannelService::NewStub(channel);

            bool connected = ConnectToServer(channel);
            if (!connected){
                std::this_thread::sleep_for(RECONNECT_DELAY);
                continue;
            }

            ClientContext context;
            
            // Create connect request
            AgentConnectRequest connect_request;
            connect_request.set_namespace_(config_.namespace_);
            connect_request.set_service(config_.service);
            connect_request.set_appid(config_.appId);
            
            std::cout << "AgentChannelClient: Connecting to server - "
                      << "namespace: " << config_.namespace_ << ", "
                      << "service: " << config_.service << ", "
                      << "appId: " << config_.appId << std::endl;
            
            // Create stream reader
            std::unique_ptr<ClientReader<ServerStreamEvent>> reader(
                stub->WatchProperties(&context, connect_request)
            );
            
            if (!reader) {
                std::cerr << "AgentChannelClient: Failed to create stream" << std::endl;
                std::this_thread::sleep_for(RECONNECT_DELAY);
                continue;
            }
            
            std::cout << "AgentChannelClient: Stream created successfully" << std::endl;

            std::atomic<bool> reading(true);
            std::thread read_thread(&AgentChannelClient::RunReadLoop, this, reader.get(), std::ref(reading));
            
            // Wait for read thread to finish
            while (running_.load() && reading.load()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }

            reading.store(false);
            context.TryCancel();

            if (read_thread.joinable()) {
                read_thread.join();
            }

            grpc::Status status = reader->Finish();
            if (!status.ok()) {
                std::cerr << "AgentChannelClient: Stream finished with error: " 
                          << status.error_code() << " - " << status.error_message() << std::endl;
            } else {
                std::cout << "AgentChannelClient: Disconnected from server gracefully" << std::endl;
            }
            
        } catch (const std::exception& e) {
            std::cerr << "AgentChannelClient: Exception: " << e.what() << std::endl;
        }
        
        if (running_.load()) {
            std::cout << "AgentChannelClient: Reconnecting in " << RECONNECT_DELAY.count() 
                      << " seconds..." << std::endl;
            std::this_thread::sleep_for(RECONNECT_DELAY);
        }
    }
}

void AgentChannelClient::RunReadLoop(
    void* stream_ptr,
    std::atomic<bool>& reading)
{
    // Cast to the actual type - we know it's ClientReader<ServerStreamEvent>*
    // ClientReader inherits from ClientReaderInterface, so we can use the interface
    auto* stream = static_cast<grpc::ClientReaderInterface<ServerStreamEvent>*>(stream_ptr);
    
    ServerStreamEvent server_event;
    while (reading.load() && stream->Read(&server_event)) {
        if (server_event.has_initevent()) {
            const auto& init = server_event.initevent();
            std::cout << "AgentChannelClient: Received ServerInitEvent - "
                      << "lastModifiedMs: " << init.lastmodifiedms() << ", "
                      << "properties count: " << init.properties_size() << std::endl;
        } else if (server_event.has_updateevent()) {
            const auto& update = server_event.updateevent();
            std::cout << "AgentChannelClient: Received ServerPropertyUpdateEvent - "
                      << "key: " << update.property().key() << ", "
                      << "lastModifiedMs: " << update.lastmodifiedms() << std::endl;
        }
    }
    reading.store(false);
    std::cout << "AgentChannelClient: Read loop finished" << std::endl;
}

bool AgentChannelClient::ConnectToServer(std::shared_ptr<grpc::Channel>& channel)
{
    if (channel->WaitForConnected(std::chrono::system_clock::now() + CONNECTION_TIMEOUT))
    {
        std::cout << "AgentChannelClient: Connected to server at " << server_address_ << std::endl;
        return true;
    }
    else
    {
        std::cerr << "AgentChannelClient: Failed to connect to server at " << server_address_ << std::endl;
        return false;
    }
}
