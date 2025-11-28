#include "ping_service.h"

#include <iostream>
#include <string>

grpc::Status PingServiceImpl::Ping(grpc::ServerContext* context,
                                   const com::fyorlando::cms::PingReq* request,
                                   com::fyorlando::cms::PongResp* reply) {
    (void)context;  // unused
    std::string prefix("Hello ");
    std::cout << "ping " << request->str() << std::endl;
    reply->set_body(prefix + request->str());
    return grpc::Status::OK;
}

