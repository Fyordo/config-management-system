#include <iostream>
#include <memory>
#include <string>

#include <grpcpp/grpcpp.h>
#include "ping.pb.h"
#include "ping.grpc.pb.h"

using grpc::Server;
using grpc::ServerBuilder;
using grpc::ServerContext;
using grpc::Status;
using com::fyorlando::cms::PingReq;
using com::fyorlando::cms::PongResp;
using com::fyorlando::cms::PingService;

// Реализация сервиса
class PingServiceImpl final : public PingService::Service {
public:
    Status Ping(ServerContext* context,
                const PingReq* request,
                PongResp* reply) override {
        std::string prefix("Hello ");
        std::cout << "ping " << request->str() << std::endl;
        reply->set_body(prefix + request->str());
        return Status::OK;
    }
};

void RunServer() {
    std::string server_address("0.0.0.0:50051");
    PingServiceImpl pingService;

    ServerBuilder builder;
    builder.AddListeningPort(server_address, grpc::InsecureServerCredentials());
    builder.RegisterService(&pingService);

    std::unique_ptr<Server> server(builder.BuildAndStart());
    std::cout << "Agent listening on " << server_address << std::endl;

    server->Wait();
}

int main(int argc, char** argv) {
    RunServer();
    return 0;
}