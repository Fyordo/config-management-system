cmake_minimum_required(VERSION 3.20)
project(cmsagent CXX)

find_package(Protobuf REQUIRED)

find_library(GRPC_GRPCPP_LIBRARY NAMES grpc++)
find_library(GRPC_GRPC_LIBRARY NAMES grpc)
find_path(GRPC_INCLUDE_DIR grpcpp/grpcpp.h)

if(NOT GRPC_GRPCPP_LIBRARY OR NOT GRPC_INCLUDE_DIR)
    message(FATAL_ERROR "gRPC not found. Install libgrpc++-dev.")
endif()

find_program(GRPC_CPP_PLUGIN grpc_cpp_plugin)
if(NOT GRPC_CPP_PLUGIN)
    message(FATAL_ERROR "grpc_cpp_plugin not found. Install grpc-proto package.")
endif()

set(PROTO_DIR "${CMAKE_CURRENT_SOURCE_DIR}/proto")
set(GENERATED_PROTO_DIR "${CMAKE_CURRENT_SOURCE_DIR}/build/proto")

file(MAKE_DIRECTORY "${GENERATED_PROTO_DIR}")

set(PROTO_FILES
    "${PROTO_DIR}/ping.proto"
)

set(PROTO_SRCS)
set(PROTO_HDRS)
foreach(PROTO_FILE ${PROTO_FILES})
    get_filename_component(PROTO_FILENAME ${PROTO_FILE} NAME_WE)
    set(GENERATED_SRC "${GENERATED_PROTO_DIR}/${PROTO_FILENAME}.pb.cc")
    set(GENERATED_HDR "${GENERATED_PROTO_DIR}/${PROTO_FILENAME}.pb.h")
    set(GENERATED_GRPC_SRC "${GENERATED_PROTO_DIR}/${PROTO_FILENAME}.grpc.pb.cc")
    set(GENERATED_GRPC_HDR "${GENERATED_PROTO_DIR}/${PROTO_FILENAME}.grpc.pb.h")

    add_custom_command(
        OUTPUT ${GENERATED_SRC} ${GENERATED_HDR} ${GENERATED_GRPC_SRC} ${GENERATED_GRPC_HDR}
        COMMAND ${Protobuf_PROTOC_EXECUTABLE}
        ARGS --proto_path=${PROTO_DIR}
             --cpp_out=${GENERATED_PROTO_DIR}
             --grpc_out=${GENERATED_PROTO_DIR}
             --plugin=protoc-gen-grpc=${GRPC_CPP_PLUGIN}
             ${PROTO_FILE}
        DEPENDS ${PROTO_FILE}
    )

    list(APPEND PROTO_SRCS ${GENERATED_SRC} ${GENERATED_GRPC_SRC})
    list(APPEND PROTO_HDRS ${GENERATED_HDR} ${GENERATED_GRPC_HDR})
endforeach()

add_custom_target(generate_proto ALL DEPENDS ${PROTO_SRCS})

add_executable(cmsagent main.cpp ${PROTO_SRCS})

add_dependencies(cmsagent generate_proto)

target_include_directories(cmsagent
    PRIVATE
        ${Protobuf_INCLUDE_DIRS}
        ${GRPC_INCLUDE_DIR}
        ${GENERATED_PROTO_DIR}
)

target_link_libraries(cmsagent
    PRIVATE
        ${Protobuf_LIBRARIES}
        ${GRPC_GRPCPP_LIBRARY}
        ${GRPC_GRPC_LIBRARY}
)