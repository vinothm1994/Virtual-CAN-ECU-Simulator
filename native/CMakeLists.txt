cmake_minimum_required(VERSION 3.16)
project(vecunative LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# --- dbcppp: vendored prebuilt library, in-tree (native/prebuilt) ---
# Self-contained: no external prefix required. Headers are
# arch-neutral (shared); the .so is picked per target arch so the same tree
# builds on an x86_64 desktop and an aarch64 Pi.
# Override with -DDBCPPP_PREFIX=/path/to/prefix to use a different copy.
set(DBCPPP_PREFIX "${CMAKE_SOURCE_DIR}/prebuilt"
    CACHE PATH "Prefix with dbcppp headers (include/) + per-arch libs (linux-*/)")
get_filename_component(DBCPPP_PREFIX "${DBCPPP_PREFIX}" ABSOLUTE)

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
    set(DBCPPP_ARCH "linux-aarch64")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64|amd64")
    set(DBCPPP_ARCH "linux-x86_64")
else()
    message(FATAL_ERROR "no vendored dbcppp for arch '${CMAKE_SYSTEM_PROCESSOR}' "
                        "(add native/prebuilt/linux-<arch>/libdbcppp.so)")
endif()

find_path(DBCPPP_INCLUDE_DIR dbcppp/Network.h HINTS "${DBCPPP_PREFIX}/include" REQUIRED)
find_library(DBCPPP_LIB dbcppp
    HINTS "${DBCPPP_PREFIX}/${DBCPPP_ARCH}" NO_DEFAULT_PATH REQUIRED)
get_filename_component(DBCPPP_LIB_DIR "${DBCPPP_LIB}" DIRECTORY)
# Every libdbcppp.so* (SONAME file + symlink) to place beside the JNI lib.
file(GLOB DBCPPP_RUNTIME "${DBCPPP_PREFIX}/${DBCPPP_ARCH}/libdbcppp.so*")
message(STATUS "dbcppp: ${DBCPPP_LIB} (arch ${DBCPPP_ARCH})")

# --- JNI: from the JDK that drives the build (system JDK 11) ---
find_package(JNI REQUIRED)

add_library(vecunative SHARED
    src/dbc_jni.cpp
    src/socketcan_jni.cpp)

target_include_directories(vecunative PRIVATE
    ${DBCPPP_INCLUDE_DIR}
    ${JNI_INCLUDE_DIRS})

target_link_libraries(vecunative PRIVATE ${DBCPPP_LIB})

# Runtime search path: look next to the JNI lib first ($ORIGIN), then the
# in-tree prefix. So the JVM loads libdbcppp.so with no LD_LIBRARY_PATH, and the
# build output dir is relocatable (e.g. bundled into a package).
set_target_properties(vecunative PROPERTIES
    BUILD_RPATH "$ORIGIN;${DBCPPP_LIB_DIR}"
    INSTALL_RPATH "$ORIGIN;${DBCPPP_LIB_DIR}")

# Copy libdbcppp.so* next to libvecunative.so so $ORIGIN resolves it.
add_custom_command(TARGET vecunative POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different ${DBCPPP_RUNTIME} $<TARGET_FILE_DIR:vecunative>
    COMMENT "Bundling libdbcppp.so next to libvecunative.so")
