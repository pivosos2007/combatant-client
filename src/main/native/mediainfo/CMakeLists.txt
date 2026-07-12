cmake_minimum_required(VERSION 3.24)
project(CombatantMediaInfo)

find_package(JNI REQUIRED)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(MediaPlayerInfo SHARED main.cpp main.h)
target_include_directories(MediaPlayerInfo PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(MediaPlayerInfo WindowsApp.lib JNI::JNI)
