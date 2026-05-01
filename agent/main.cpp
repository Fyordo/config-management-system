#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>

#include <grpcpp/grpcpp.h>
#include "grpc_starter.h"

int main(int argc, char** argv) {
    try {
        std::unique_ptr<GrpcServerStarter> starter = std::make_unique<GrpcServerStarter>();
        starter->RunServer();
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "Fatal error: " << e.what() << std::endl;
        return 1;
    } catch (...) {
        std::cerr << "Fatal error: Unknown exception" << std::endl;
        return 2;
    }
}