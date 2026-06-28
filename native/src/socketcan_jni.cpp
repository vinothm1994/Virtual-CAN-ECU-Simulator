// JNI bridge over Linux SocketCAN. Kotlin side: com.vecu.can.SocketCanNative.
//
// recvFrame packs a frame the same way DbcNative.encode does (13 bytes):
//   [0..3] can_id big-endian, bit31 set when extended
//   [4]    dlc
//   [5..12] data

#include <jni.h>

#include <cerrno>
#include <cstdint>
#include <cstring>

#include <linux/can.h>
#include <linux/can/raw.h>
#include <net/if.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>

namespace {
constexpr uint32_t kExtendedFlag = 0x80000000u;
}

extern "C" {

// Returns a non-negative fd, or a negative errno on failure.
JNIEXPORT jlong JNICALL Java_com_vecu_can_SocketCanNative_openIface(JNIEnv* env, jobject,
                                                                   jstring jiface) {
    const char* iface = env->GetStringUTFChars(jiface, nullptr);
    int fd = socket(PF_CAN, SOCK_RAW, CAN_RAW);
    if (fd < 0) {
        int e = errno;
        env->ReleaseStringUTFChars(jiface, iface);
        return -e;
    }

    struct ifreq ifr;
    std::memset(&ifr, 0, sizeof(ifr));
    std::strncpy(ifr.ifr_name, iface, IFNAMSIZ - 1);
    env->ReleaseStringUTFChars(jiface, iface);
    if (ioctl(fd, SIOCGIFINDEX, &ifr) < 0) {
        int e = errno;
        close(fd);
        return -e;
    }

    struct sockaddr_can addr;
    std::memset(&addr, 0, sizeof(addr));
    addr.can_family = AF_CAN;
    addr.can_ifindex = ifr.ifr_ifindex;
    if (bind(fd, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr)) < 0) {
        int e = errno;
        close(fd);
        return -e;
    }
    return fd;
}

JNIEXPORT void JNICALL Java_com_vecu_can_SocketCanNative_closeFd(JNIEnv*, jobject, jlong fd) {
    if (fd >= 0) close(static_cast<int>(fd));
}

// Returns 0 on success or -errno on failure.
JNIEXPORT jint JNICALL Java_com_vecu_can_SocketCanNative_sendFrame(JNIEnv* env, jobject, jlong fd,
                                                                  jint canId, jboolean eff,
                                                                  jbyteArray data, jint dlc) {
    struct can_frame frame;
    std::memset(&frame, 0, sizeof(frame));
    uint32_t id = static_cast<uint32_t>(canId);
    frame.can_id = eff ? ((id & CAN_EFF_MASK) | CAN_EFF_FLAG) : (id & CAN_SFF_MASK);
    if (dlc < 0) dlc = 0;
    if (dlc > 8) dlc = 8;
    frame.can_dlc = static_cast<__u8>(dlc);
    if (data) {
        jsize n = env->GetArrayLength(data);
        if (n > dlc) n = dlc;
        env->GetByteArrayRegion(data, 0, n, reinterpret_cast<jbyte*>(frame.data));
    }

    ssize_t w = write(static_cast<int>(fd), &frame, sizeof(frame));
    if (w != static_cast<ssize_t>(sizeof(frame))) return -errno;
    return 0;
}

// Blocks up to timeoutMs for one frame. Returns a 13-byte packed array, or null
// on timeout or error.
JNIEXPORT jbyteArray JNICALL Java_com_vecu_can_SocketCanNative_recvFrame(JNIEnv* env, jobject,
                                                                        jlong fd, jint timeoutMs) {
    struct pollfd pfd;
    pfd.fd = static_cast<int>(fd);
    pfd.events = POLLIN;
    int pr = poll(&pfd, 1, timeoutMs);
    if (pr <= 0) return nullptr;  // timeout or error
    if (!(pfd.revents & POLLIN)) return nullptr;

    struct can_frame frame;
    ssize_t r = read(static_cast<int>(fd), &frame, sizeof(frame));
    if (r < static_cast<ssize_t>(sizeof(frame))) return nullptr;

    const bool ext = (frame.can_id & CAN_EFF_FLAG) != 0;
    uint32_t id = ext ? (frame.can_id & CAN_EFF_MASK) : (frame.can_id & CAN_SFF_MASK);
    uint32_t rawId = id | (ext ? kExtendedFlag : 0u);

    jbyte packed[13];
    packed[0] = static_cast<jbyte>((rawId >> 24) & 0xFF);
    packed[1] = static_cast<jbyte>((rawId >> 16) & 0xFF);
    packed[2] = static_cast<jbyte>((rawId >> 8) & 0xFF);
    packed[3] = static_cast<jbyte>(rawId & 0xFF);
    packed[4] = static_cast<jbyte>(frame.can_dlc > 8 ? 8 : frame.can_dlc);
    std::memcpy(packed + 5, frame.data, 8);

    jbyteArray out = env->NewByteArray(13);
    if (!out) return nullptr;
    env->SetByteArrayRegion(out, 0, 13, packed);
    return out;
}

}  // extern "C"
