// JNI bridge over PEAK's PCAN-Basic API (Windows). Kotlin side:
// com.vecu.can.PcanNative. Compiled only on Windows (see CMakeLists).
//
// PCANBasic.dll is loaded dynamically at runtime (it ships with the PEAK driver
// install), so building this needs no PEAK SDK. Only the minimal API subset we
// use is declared here.
//
// recvFrame packs a frame the same 13-byte way as the SocketCAN/dbcppp paths:
//   [0..3] can_id big-endian (bit31=extended), [4] dlc, [5..12] data.

#include <jni.h>

#include <windows.h>

#include <cstdint>
#include <cstring>
#include <unordered_map>

namespace {

// --- Minimal PCAN-Basic types/constants (from PCANBasic.h) ---
using TPCANHandle = uint16_t;
using TPCANStatus = uint32_t;
using TPCANBaudrate = uint16_t;
using TPCANType = uint32_t;
using TPCANParameter = uint8_t;
using TPCANMessageType = uint8_t;

#pragma pack(push, 1)
struct TPCANMsg {
    uint32_t ID;
    TPCANMessageType MSGTYPE;
    uint8_t LEN;
    uint8_t DATA[8];
};
struct TPCANTimestamp {
    uint32_t millis;
    uint16_t millis_overflow;
    uint16_t micros;
};
#pragma pack(pop)

constexpr TPCANStatus PCAN_ERROR_OK = 0x00000;
constexpr TPCANStatus PCAN_ERROR_QRCVEMPTY = 0x00020;
constexpr TPCANStatus PCAN_ERROR_ILLOPERATION = 0x80000;  // our "DLL missing" sentinel
constexpr TPCANMessageType PCAN_MESSAGE_EXTENDED = 0x02;
constexpr TPCANMessageType PCAN_MESSAGE_STATUS = 0x80;
constexpr TPCANMessageType PCAN_MESSAGE_ERRFRAME = 0x40;
constexpr TPCANParameter PCAN_RECEIVE_EVENT = 0x03;

using fnInitialize = TPCANStatus(__stdcall*)(TPCANHandle, TPCANBaudrate, TPCANType, DWORD, WORD);
using fnUninitialize = TPCANStatus(__stdcall*)(TPCANHandle);
using fnRead = TPCANStatus(__stdcall*)(TPCANHandle, TPCANMsg*, TPCANTimestamp*);
using fnWrite = TPCANStatus(__stdcall*)(TPCANHandle, TPCANMsg*);
using fnGetValue = TPCANStatus(__stdcall*)(TPCANHandle, TPCANParameter, void*, DWORD);
using fnGetErrorText = TPCANStatus(__stdcall*)(TPCANStatus, WORD, char*);

struct Pcan {
    HMODULE dll = nullptr;
    fnInitialize Initialize = nullptr;
    fnUninitialize Uninitialize = nullptr;
    fnRead Read = nullptr;
    fnWrite Write = nullptr;
    fnGetValue GetValue = nullptr;
    fnGetErrorText GetErrorText = nullptr;
    std::unordered_map<TPCANHandle, HANDLE> rxEvents;  // per-channel receive event
};

Pcan g;

// Loads PCANBasic.dll (from the PEAK driver install, resolved via the system
// search path) once. Returns false if the driver is not present.
bool ensureDll() {
    if (g.dll) return true;
    g.dll = LoadLibraryA("PCANBasic.dll");
    if (!g.dll) return false;
    g.Initialize = reinterpret_cast<fnInitialize>(GetProcAddress(g.dll, "CAN_Initialize"));
    g.Uninitialize = reinterpret_cast<fnUninitialize>(GetProcAddress(g.dll, "CAN_Uninitialize"));
    g.Read = reinterpret_cast<fnRead>(GetProcAddress(g.dll, "CAN_Read"));
    g.Write = reinterpret_cast<fnWrite>(GetProcAddress(g.dll, "CAN_Write"));
    g.GetValue = reinterpret_cast<fnGetValue>(GetProcAddress(g.dll, "CAN_GetValue"));
    g.GetErrorText = reinterpret_cast<fnGetErrorText>(GetProcAddress(g.dll, "CAN_GetErrorText"));
    return g.Initialize && g.Uninitialize && g.Read && g.Write && g.GetValue;
}

}  // namespace

extern "C" {

// Returns PCAN_ERROR_OK (0) on success, or a PCAN status / sentinel on failure.
JNIEXPORT jint JNICALL Java_com_vecu_can_PcanNative_openChannel(JNIEnv*, jobject, jint channel,
                                                               jint baudrate) {
    if (!ensureDll()) return static_cast<jint>(PCAN_ERROR_ILLOPERATION);
    const auto ch = static_cast<TPCANHandle>(channel);
    TPCANStatus st = g.Initialize(ch, static_cast<TPCANBaudrate>(baudrate), 0, 0, 0);
    if (st != PCAN_ERROR_OK) return static_cast<jint>(st);
    // Ask PCAN for a Win32 event that is signalled when a frame arrives, so
    // receive() can block efficiently instead of spinning.
    HANDLE ev = nullptr;
    if (g.GetValue(ch, PCAN_RECEIVE_EVENT, &ev, sizeof(ev)) == PCAN_ERROR_OK && ev) {
        g.rxEvents[ch] = ev;
    }
    return 0;
}

JNIEXPORT void JNICALL Java_com_vecu_can_PcanNative_closeChannel(JNIEnv*, jobject, jint channel) {
    const auto ch = static_cast<TPCANHandle>(channel);
    if (g.Uninitialize) g.Uninitialize(ch);
    g.rxEvents.erase(ch);
}

// Returns PCAN_ERROR_OK (0) or a PCAN status.
JNIEXPORT jint JNICALL Java_com_vecu_can_PcanNative_sendFrame(JNIEnv* env, jobject, jint channel,
                                                             jint canId, jboolean extended,
                                                             jbyteArray data, jint dlc) {
    if (!g.Write) return static_cast<jint>(PCAN_ERROR_ILLOPERATION);
    TPCANMsg msg;
    std::memset(&msg, 0, sizeof(msg));
    msg.ID = static_cast<uint32_t>(canId);
    msg.MSGTYPE = extended ? PCAN_MESSAGE_EXTENDED : 0;
    if (dlc < 0) dlc = 0;
    if (dlc > 8) dlc = 8;
    msg.LEN = static_cast<uint8_t>(dlc);
    if (data) {
        jsize n = env->GetArrayLength(data);
        if (n > dlc) n = dlc;
        env->GetByteArrayRegion(data, 0, n, reinterpret_cast<jbyte*>(msg.DATA));
    }
    return static_cast<jint>(g.Write(static_cast<TPCANHandle>(channel), &msg));
}

// Blocks up to timeoutMs for one data frame. Returns a 13-byte packed array, or
// null on timeout / error / non-data frame.
JNIEXPORT jbyteArray JNICALL Java_com_vecu_can_PcanNative_recvFrame(JNIEnv* env, jobject,
                                                                   jint channel, jint timeoutMs) {
    if (!g.Read) return nullptr;
    const auto ch = static_cast<TPCANHandle>(channel);
    TPCANMsg msg;
    TPCANTimestamp ts;

    // Try a non-blocking read first (drains any queued frames without waiting);
    // only wait on the event when the queue is empty.
    TPCANStatus st = g.Read(ch, &msg, &ts);
    if (st == PCAN_ERROR_QRCVEMPTY) {
        auto it = g.rxEvents.find(ch);
        if (it != g.rxEvents.end()) WaitForSingleObject(it->second, static_cast<DWORD>(timeoutMs));
        st = g.Read(ch, &msg, &ts);
    }
    if (st != PCAN_ERROR_OK) return nullptr;
    if (msg.MSGTYPE & (PCAN_MESSAGE_STATUS | PCAN_MESSAGE_ERRFRAME)) return nullptr;

    const bool ext = (msg.MSGTYPE & PCAN_MESSAGE_EXTENDED) != 0;
    uint32_t rawId = msg.ID | (ext ? 0x80000000u : 0u);

    jbyte packed[13];
    packed[0] = static_cast<jbyte>((rawId >> 24) & 0xFF);
    packed[1] = static_cast<jbyte>((rawId >> 16) & 0xFF);
    packed[2] = static_cast<jbyte>((rawId >> 8) & 0xFF);
    packed[3] = static_cast<jbyte>(rawId & 0xFF);
    packed[4] = static_cast<jbyte>(msg.LEN > 8 ? 8 : msg.LEN);
    std::memcpy(packed + 5, msg.DATA, 8);

    jbyteArray out = env->NewByteArray(13);
    if (!out) return nullptr;
    env->SetByteArrayRegion(out, 0, 13, packed);
    return out;
}

JNIEXPORT jstring JNICALL Java_com_vecu_can_PcanNative_statusText(JNIEnv* env, jobject,
                                                                 jint status) {
    if (!g.GetErrorText) return env->NewStringUTF("PCANBasic.dll not loaded");
    char buf[256] = {0};
    if (g.GetErrorText(static_cast<TPCANStatus>(status), 0, buf) != PCAN_ERROR_OK) {
        return env->NewStringUTF("unknown PCAN error");
    }
    return env->NewStringUTF(buf);
}

}  // extern "C"
