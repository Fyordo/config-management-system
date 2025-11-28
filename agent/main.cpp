#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>

#include <grpcpp/grpcpp.h>
#include "grpc_starter.h"

int main(int argc, char** argv) {
    RunServer(argc, argv);
    return 0;
}