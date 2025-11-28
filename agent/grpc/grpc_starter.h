#pragma once

#include <string>

void RunServer(int argc, char** argv);

std::string getPortForGrpcOrDefault(int argc, char** argv);