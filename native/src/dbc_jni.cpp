// JNI bridge over dbcppp. Kotlin side: com.vecu.dbc.DbcNative.
//
// The Kotlin layer never sees a dbcppp type. It gets:
//   * a full schema as one JSON string (parsed once at startup), and
//   * decode -> physical values in signal-declaration order, and
//   * encode -> a packed CAN frame.
//
// Frame packing (encode result, 13 bytes):
//   [0..3] can_id, big-endian, with bit31 set when the message is extended
//   [4]    dlc
//   [5..12] data (8 bytes)

#include <jni.h>

#include <cstdint>
#include <cstring>
#include <fstream>
#include <memory>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

#include <dbcppp/Network.h>

namespace {

// DBC marks a 29-bit (extended) message by setting bit 31 of the BO_ id.
constexpr uint32_t kExtendedFlag = 0x80000000u;
constexpr uint32_t kEffMask = 0x1FFFFFFFu;  // 29-bit
constexpr uint32_t kSffMask = 0x7FFu;       // 11-bit

struct MsgKey {
    uint32_t id;
    bool extended;
};

MsgKey keyOfDbcMessage(uint64_t dbc_id) {
    return MsgKey{static_cast<uint32_t>(dbc_id) & kEffMask, (dbc_id & kExtendedFlag) != 0};
}

uint64_t packKey(const MsgKey& k) {
    return (static_cast<uint64_t>(k.extended) << 32) | k.id;
}

// A loaded DBC set. Owns the networks; the lookup maps point into their storage.
struct DbcHandle {
    std::vector<std::unique_ptr<dbcppp::INetwork>> nets;
    std::unordered_map<uint64_t, const dbcppp::IMessage*> by_id;
    std::unordered_map<std::string, const dbcppp::IMessage*> by_name;
};

std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

// Minimal JSON string escaping (DBC identifiers are tame, but units/descriptions
// can carry quotes or backslashes).
void appendEscaped(std::string& out, const std::string& s) {
    for (char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out += c;
        }
    }
}

std::string numToStr(double v) {
    std::ostringstream os;
    os.precision(10);
    os << v;
    return os.str();
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_vecu_dbc_DbcNative_loadDbc(JNIEnv* env, jobject, jstring jpath) {
    const std::string path = jstr(env, jpath);
    std::ifstream is(path);
    if (!is) return 0;
    auto net = dbcppp::INetwork::LoadDBCFromIs(is);
    if (!net) return 0;

    auto* h = new DbcHandle();
    for (uint64_t i = 0; i < net->Messages_Size(); ++i) {
        const dbcppp::IMessage& msg = net->Messages_Get(i);
        h->by_id[packKey(keyOfDbcMessage(msg.Id()))] = &msg;
        h->by_name[msg.Name()] = &msg;
    }
    h->nets.push_back(std::move(net));
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT void JNICALL Java_com_vecu_dbc_DbcNative_release(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<DbcHandle*>(handle);
}

JNIEXPORT jstring JNICALL Java_com_vecu_dbc_DbcNative_schemaJson(JNIEnv* env, jobject,
                                                                 jlong handle) {
    auto* h = reinterpret_cast<DbcHandle*>(handle);
    if (!h) return env->NewStringUTF("{\"messages\":[]}");

    std::string out = "{\"messages\":[";
    bool firstMsg = true;
    for (const auto& net : h->nets) {
        for (uint64_t mi = 0; mi < net->Messages_Size(); ++mi) {
            const dbcppp::IMessage& msg = net->Messages_Get(mi);
            const MsgKey k = keyOfDbcMessage(msg.Id());
            if (!firstMsg) out += ",";
            firstMsg = false;
            out += "{\"name\":\"";
            appendEscaped(out, msg.Name());
            out += "\",\"id\":" + std::to_string(k.id);
            out += ",\"eff\":";
            out += (k.extended ? "true" : "false");
            out += ",\"dlc\":" + std::to_string(msg.MessageSize());
            out += ",\"signals\":[";
            for (uint64_t si = 0; si < msg.Signals_Size(); ++si) {
                const dbcppp::ISignal& sig = msg.Signals_Get(si);
                if (si) out += ",";
                out += "{\"name\":\"";
                appendEscaped(out, sig.Name());
                out += "\",\"start\":" + std::to_string(sig.StartBit());
                out += ",\"length\":" + std::to_string(sig.BitSize());
                out += ",\"unit\":\"";
                appendEscaped(out, sig.Unit());
                out += "\",\"min\":" + numToStr(sig.Minimum());
                out += ",\"max\":" + numToStr(sig.Maximum());
                out += ",\"factor\":" + numToStr(sig.Factor());
                const bool isSigned =
                    sig.ValueType() == dbcppp::ISignal::EValueType::Signed;
                out += ",\"signed\":";
                out += (isSigned ? "true" : "false");
                out += ",\"values\":{";
                for (uint64_t vi = 0; vi < sig.ValueEncodingDescriptions_Size(); ++vi) {
                    const auto& ved = sig.ValueEncodingDescriptions_Get(vi);
                    if (vi) out += ",";
                    out += "\"" + std::to_string(ved.Value()) + "\":\"";
                    appendEscaped(out, ved.Description());
                    out += "\"";
                }
                out += "}}";
            }
            out += "]}";
        }
    }
    out += "]}";
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jdoubleArray JNICALL Java_com_vecu_dbc_DbcNative_decode(JNIEnv* env, jobject,
                                                                 jlong handle, jint canId,
                                                                 jboolean eff, jbyteArray data) {
    auto* h = reinterpret_cast<DbcHandle*>(handle);
    if (!h) return nullptr;
    MsgKey k{static_cast<uint32_t>(canId) & (eff ? kEffMask : kSffMask), eff != 0};
    auto it = h->by_id.find(packKey(k));
    if (it == h->by_id.end()) return nullptr;
    const dbcppp::IMessage* msg = it->second;

    uint8_t buf[8] = {0};
    if (data) {
        jsize n = env->GetArrayLength(data);
        if (n > 8) n = 8;
        env->GetByteArrayRegion(data, 0, n, reinterpret_cast<jbyte*>(buf));
    }

    const jsize count = static_cast<jsize>(msg->Signals_Size());
    jdoubleArray result = env->NewDoubleArray(count);
    if (!result) return nullptr;
    std::vector<jdouble> vals(count);
    for (jsize i = 0; i < count; ++i) {
        const dbcppp::ISignal& sig = msg->Signals_Get(i);
        vals[i] = sig.RawToPhys(sig.Decode(buf));
    }
    env->SetDoubleArrayRegion(result, 0, count, vals.data());
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_com_vecu_dbc_DbcNative_encode(JNIEnv* env, jobject, jlong handle,
                                                               jstring jmessage,
                                                               jobjectArray signalNames,
                                                               jdoubleArray values) {
    auto* h = reinterpret_cast<DbcHandle*>(handle);
    if (!h) return nullptr;
    const std::string message = jstr(env, jmessage);
    auto mit = h->by_name.find(message);
    if (mit == h->by_name.end()) return nullptr;
    const dbcppp::IMessage* msg = mit->second;

    std::unordered_map<std::string, const dbcppp::ISignal*> sigByName;
    for (uint64_t i = 0; i < msg->Signals_Size(); ++i) {
        const dbcppp::ISignal& s = msg->Signals_Get(i);
        sigByName[s.Name()] = &s;
    }

    uint8_t data[8] = {0};
    const jsize n = signalNames ? env->GetArrayLength(signalNames) : 0;
    for (jsize i = 0; i < n; ++i) {
        auto nameObj = reinterpret_cast<jstring>(env->GetObjectArrayElement(signalNames, i));
        const std::string name = jstr(env, nameObj);
        env->DeleteLocalRef(nameObj);
        auto sit = sigByName.find(name);
        if (sit == sigByName.end()) continue;  // unknown signal: skip, leave zero
        jdouble phys = 0;
        env->GetDoubleArrayRegion(values, i, 1, &phys);
        sit->second->Encode(sit->second->PhysToRaw(phys), data);
    }

    const MsgKey k = keyOfDbcMessage(msg->Id());
    uint32_t rawId = k.id | (k.extended ? kExtendedFlag : 0u);
    uint8_t dlc = static_cast<uint8_t>(msg->MessageSize());

    jbyte packed[13];
    packed[0] = static_cast<jbyte>((rawId >> 24) & 0xFF);
    packed[1] = static_cast<jbyte>((rawId >> 16) & 0xFF);
    packed[2] = static_cast<jbyte>((rawId >> 8) & 0xFF);
    packed[3] = static_cast<jbyte>(rawId & 0xFF);
    packed[4] = static_cast<jbyte>(dlc);
    std::memcpy(packed + 5, data, 8);

    jbyteArray out = env->NewByteArray(13);
    if (!out) return nullptr;
    env->SetByteArrayRegion(out, 0, 13, packed);
    return out;
}

}  // extern "C"
