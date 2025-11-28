#pragma once

#include <grpcpp/grpcpp.h>

#include "ping.grpc.pb.h"
#include "ping.pb.h"

class PingServiceImpl final : public com::fyorlando::cms::PingService::Service {
public:
    grpc::Status Ping(grpc::ServerContext* context,
                      const com::fyorlando::cms::PingReq* request,
                      com::fyorlando::cms::PongResp* reply) override;
};

