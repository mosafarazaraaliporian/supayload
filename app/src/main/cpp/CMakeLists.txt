cmake_minimum_required(VERSION 3.22.1)

project("installer")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -s")

add_library(installer SHARED installer.cpp)

target_link_libraries(installer
    android
    log
)

