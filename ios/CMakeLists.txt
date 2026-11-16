cmake_minimum_required(VERSION 3.21)

project(AssaultCubeIOSBootstrap VERSION 0.1 LANGUAGES OBJC)

if(NOT CMAKE_SYSTEM_NAME STREQUAL "iOS")
  message(FATAL_ERROR "Configure with -DCMAKE_SYSTEM_NAME=iOS")
endif()

set(CMAKE_OSX_DEPLOYMENT_TARGET "17.0" CACHE STRING "Minimum iOS version")
set(CMAKE_OSX_ARCHITECTURES "arm64" CACHE STRING "Device architecture")

find_library(UIKIT_FRAMEWORK UIKit REQUIRED)

configure_file(
  "${CMAKE_CURRENT_SOURCE_DIR}/Info.plist.in"
  "${CMAKE_CURRENT_BINARY_DIR}/Info.plist"
  @ONLY
)

add_executable(AssaultCubeIOSBootstrap MACOSX_BUNDLE main.m)
target_link_libraries(AssaultCubeIOSBootstrap PRIVATE "${UIKIT_FRAMEWORK}")
target_compile_options(AssaultCubeIOSBootstrap PRIVATE -fobjc-arc)

set_target_properties(AssaultCubeIOSBootstrap PROPERTIES
  MACOSX_BUNDLE_INFO_PLIST "${CMAKE_CURRENT_BINARY_DIR}/Info.plist"
  MACOSX_BUNDLE_BUNDLE_NAME "AssaultCube iOS Bootstrap"
  MACOSX_BUNDLE_BUNDLE_VERSION "1"
  MACOSX_BUNDLE_GUI_IDENTIFIER "net.cubers.assaultcube.ios.bootstrap"
  XCODE_ATTRIBUTE_CODE_SIGNING_ALLOWED "NO"
  XCODE_ATTRIBUTE_CODE_SIGNING_REQUIRED "NO"
  XCODE_ATTRIBUTE_DEVELOPMENT_TEAM ""
  XCODE_ATTRIBUTE_TARGETED_DEVICE_FAMILY "1,2"
  XCODE_ATTRIBUTE_SUPPORTED_PLATFORMS "iphoneos"
  XCODE_ATTRIBUTE_ENABLE_BITCODE "NO"
)
