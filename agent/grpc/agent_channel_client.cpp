#include "agent_channel_client.h"

#include <iostream>
#include <chrono>
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
    if (running_.load()) {
        std::cerr << "AgentChannelClient: Already running" << std::endl;
        return;
    }
    
    running_.store(true);
    client_thread_ = std::thread(&AgentChannelClient::Run, this);
}

void AgentChannelClient::Stop() {
    if (!running_.load()) {
        return;
    }
    
    running_.store(false);
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
                std::this_thread::sleep_for(std::chrono::seconds(5));
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
                stream->WritesDone();
                stream->Finish();
                std::this_thread::sleep_for(std::chrono::seconds(5));
                continue;
            }
            
            std::cout << "AgentChannelClient: Sent connect event - "
                      << "namespace: " << config_.namespace_ << ", "
                      << "service: " << config_.service << ", "
                      << "appId: " << config_.appId << std::endl;
            
            std::atomic<bool> reading(true);
            std::thread read_thread([&stream, &reading]() {
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
            });
            
            auto last_heartbeat = std::chrono::steady_clock::now();
            const auto heartbeat_interval = std::chrono::seconds(30);
            
            while (running_.load()) {
                auto now = std::chrono::steady_clock::now();
                if (now - last_heartbeat >= heartbeat_interval) {
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
                
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
            
            reading.store(false);
            stream->WritesDone();
            stream->Finish();
            read_thread.join();
            
            std::cout << "AgentChannelClient: Disconnected from server" << std::endl;
            
        } catch (const std::exception& e) {
            std::cerr << "AgentChannelClient: Exception: " << e.what() << std::endl;
        }
        
        if (running_.load()) {
            std::cout << "AgentChannelClient: Reconnecting in 5 seconds..." << std::endl;
            std::this_thread::sleep_for(std::chrono::seconds(5));
        }
    }
}

bool AgentChannelClient::ConnectToServer(std::shared_ptr<grpc_impl::Channel> &channel)
{
    if (channel->WaitForConnected(std::chrono::system_clock::now() + std::chrono::seconds(5)))
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
