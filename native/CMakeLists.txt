cmake_minimum_required(VERSION 3.16)
project(vecunative LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# --- dbcppp: vendored prebuilt library, in-tree (native/prebuilt) ---
# Self-contained: no external prefix required. Headers are arch-neutral (shared);
# the library is picked per OS+arch so the same tree builds on an x86_64 Linux
# desktop, an aarch64 Pi, and (cross-)builds for Windows x86_64.
# Override with -DDBCPPP_PREFIX=/path/to/prefix to use a different copy.
set(DBCPPP_PREFIX "${CMAKE_SOURCE_DIR}/prebuilt"
    CACHE PATH "Prefix with dbcppp headers (include/) + per-platform libs")
get_filename_component(DBCPPP_PREFIX "${DBCPPP_PREFIX}" ABSOLUTE)

if(WIN32)
    set(DBCPPP_OSARCH "windows-x86_64")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
    set(DBCPPP_OSARCH "linux-aarch64")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64|amd64")
    set(DBCPPP_OSARCH "linux-x86_64")
else()
    message(FATAL_ERROR "no vendored dbcppp for '${CMAKE_SYSTEM_PROCESSOR}' "
                        "(add native/prebuilt/<os>-<arch>/)")
endif()

# CMAKE_FIND_ROOT_PATH_BOTH so cross builds (e.g. MinGW) still see the in-tree
# prefix, which lives outside the toolchain sysroot.
find_path(DBCPPP_INCLUDE_DIR dbcppp/Network.h
    HINTS "${DBCPPP_PREFIX}/include" NO_DEFAULT_PATH CMAKE_FIND_ROOT_PATH_BOTH REQUIRED)
find_library(DBCPPP_LIB dbcppp
    HINTS "${DBCPPP_PREFIX}/${DBCPPP_OSARCH}" NO_DEFAULT_PATH CMAKE_FIND_ROOT_PATH_BOTH REQUIRED)
get_filename_component(DBCPPP_LIB_DIR "${DBCPPP_LIB}" DIRECTORY)
# Runtime libraries to place beside the JNI lib (SONAME .so + symlink, or .dll).
file(GLOB DBCPPP_RUNTIME
    "${DBCPPP_PREFIX}/${DBCPPP_OSARCH}/libdbcppp.so*"
    "${DBCPPP_PREFIX}/${DBCPPP_OSARCH}/libdbcppp.dll")
message(STATUS "dbcppp: ${DBCPPP_LIB} (${DBCPPP_OSARCH})")

# --- JNI headers ---
# Normally from the JDK that drives the build. For cross-compiling (e.g. a
# Windows DLL from Linux) pass -DVECU_JNI_INCLUDE="<jdk>/include;<jdk>/include/win32".
if(DEFINED VECU_JNI_INCLUDE)
    set(JNI_INCLUDE_DIRS ${VECU_JNI_INCLUDE})
else()
    find_package(JNI REQUIRED)
endif()

# CAN transport is OS-specific: SocketCAN on Linux, PCAN-Basic on Windows.
if(WIN32)
    set(CAN_SOURCE src/pcan_jni.cpp)
else()
    set(CAN_SOURCE src/socketcan_jni.cpp)
endif()

add_library(vecunative SHARED src/dbc_jni.cpp ${CAN_SOURCE})

target_include_directories(vecunative PRIVATE
    ${DBCPPP_INCLUDE_DIR}
    ${JNI_INCLUDE_DIRS})

target_link_libraries(vecunative PRIVATE ${DBCPPP_LIB})

if(WIN32)
    # Emit vecunative.dll (not libvecunative.dll); the dbcppp dependency is
    # resolved at load time by NativeLoader (loads dbcppp.dll first).
    set_target_properties(vecunative PROPERTIES PREFIX "")
else()
    # Look next to the JNI lib first ($ORIGIN), then the in-tree prefix, so the
    # JVM loads libdbcppp.so with no LD_LIBRARY_PATH and the dir is relocatable.
    set_target_properties(vecunative PROPERTIES
        BUILD_RPATH "$ORIGIN;${DBCPPP_LIB_DIR}"
        INSTALL_RPATH "$ORIGIN;${DBCPPP_LIB_DIR}")
endif()

# Bundle the dbcppp runtime lib next to the JNI lib.
add_custom_command(TARGET vecunative POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different ${DBCPPP_RUNTIME} $<TARGET_FILE_DIR:vecunative>
    COMMENT "Bundling dbcppp runtime next to the JNI library")
