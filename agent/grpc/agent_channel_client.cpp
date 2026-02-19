#include "agent_channel_client.h"

#include <iostream>
#include <chrono>
#include <functional>
#include <grpcpp/grpcpp.h>
#include "AgentChannelService.grpc.pb.h"
#include "AgentChannelService.pb.h"

using grpc::ClientContext;
using grpc::ClientReaderWriter;
using com::fyordo::cms::AgentChannelService;
using com::fyordo::cms::AgentStreamEvent;
using com::fyordo::cms::ServerStreamEvent;
using com::fyordo::cms::AgentConnectEvent;
using com::fyordo::cms::AgentHeartbeatEvent;

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
            std::shared_ptr<ClientReaderWriter<AgentStreamEvent, ServerStreamEvent>> stream(
                stub->WatchProperties(&context)
            );
            
            AgentStreamEvent connect_event;
            AgentConnectEvent* connect = connect_event.mutable_connect();
            connect->set_namespace_(config_.namespace_);
            connect->set_service(config_.service);
            connect->set_appid(config_.appId);
            
            if (!stream->Write(connect_event)) {
                std::cerr << "AgentChannelClient: Failed to send connect event" << std::endl;
                context.TryCancel();
                grpc::Status status = stream->Finish();
                std::cerr << "AgentChannelClient: Stream finished with status: " 
                          << status.error_code() << " - " << status.error_message() << std::endl;
                std::this_thread::sleep_for(RECONNECT_DELAY);
                continue;
            }
            
            std::cout << "AgentChannelClient: Sent connect event - "
                      << "namespace: " << config_.namespace_ << ", "
                      << "service: " << config_.service << ", "
                      << "appId: " << config_.appId << std::endl;

            std::atomic<bool> reading(true);
            std::thread read_thread(&AgentChannelClient::RunReadLoop, this, std::ref(*stream), std::ref(reading));
            
            auto last_heartbeat = std::chrono::steady_clock::now();
            
            while (running_.load() && reading.load()) {
                auto now = std::chrono::steady_clock::now();
                if (now - last_heartbeat >= HEARTBEAT_INTERVAL) {
                    AgentStreamEvent heartbeat_event;
                    AgentHeartbeatEvent* heartbeat = heartbeat_event.mutable_heartbeat();
                    heartbeat->set_timestampms(
                        std::chrono::duration_cast<std::chrono::milliseconds>(
                            std::chrono::system_clock::now().time_since_epoch()).count());
                    
                    if (!stream->Write(heartbeat_event)) {
                        std::cerr << "AgentChannelClient: Failed to send heartbeat" << std::endl;
                        break;
                    }
                    
                    last_heartbeat = now;
                }
                
                std::this_thread::sleep_for(HEARTBEAT_CHECK_INTERVAL);
            }

            reading.store(false);
            context.TryCancel();

            if (read_thread.joinable()) {
                read_thread.join();
            }

            grpc::Status status = stream->Finish();
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
    grpc::ClientReaderWriter<com::fyordo::cms::AgentStreamEvent, 
                             com::fyordo::cms::ServerStreamEvent>& stream,
    std::atomic<bool>& reading)
{
    ServerStreamEvent server_event;
    while (reading.load() && stream.Read(&server_event)) {
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
