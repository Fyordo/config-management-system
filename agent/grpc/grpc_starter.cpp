#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>

#include <grpcpp/grpcpp.h>
#include "impl/ping_service.h"
#include "grpc_starter.h"

using grpc::Server;
using grpc::ServerBuilder;

const std::string& address("0.0.0.0");

void RunServer(int argc, char** argv) {
    PingServiceImpl service;
    const std::string port = getPortForGrpcOrDefault(argc, argv);

    ServerBuilder builder;
    builder.AddListeningPort(address + ":" +port, grpc::InsecureServerCredentials());
    builder.RegisterService(&service);

    std::unique_ptr<Server> server(builder.BuildAndStart());
    std::cout << "Agent listening on " << address << " port " << port << std::endl;

    server->Wait();
}

std::string getPortForGrpcOrDefault(int argc, char** argv) {
    std::string port = "50051";
    if (argc > 1) {
        try {
            int port_value = std::stoi(argv[1]);
            if (port_value <= 0 || port_value > 65535) {
                std::cerr << "Port must be between 1 and 65535" << std::endl;
            }
        } catch (const std::invalid_argument&) {
            std::cerr << "Port argument must be a number" << std::endl;
        } catch (const std::out_of_range&) {
            std::cerr << "Port argument is out of valid range" << std::endl;
        }

        port = argv[1];
    }

    return port;
}