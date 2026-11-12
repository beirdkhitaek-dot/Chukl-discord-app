cmake_minimum_required(VERSION 3.22.1)
project(chukl_discord CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The GitHub Actions workflow extracts the SDK headers and libs into ./discord
set(DISCORD_DIR ${CMAKE_CURRENT_SOURCE_DIR}/discord)

add_library(discord_partner_sdk SHARED IMPORTED)
set_target_properties(discord_partner_sdk PROPERTIES
    IMPORTED_LOCATION ${DISCORD_DIR}/lib/${ANDROID_ABI}/libdiscord_partner_sdk.so)

add_library(chukl_discord SHARED chukl_discord.cpp)
target_include_directories(chukl_discord PRIVATE ${DISCORD_DIR}/include)
target_link_libraries(chukl_discord discord_partner_sdk log)
