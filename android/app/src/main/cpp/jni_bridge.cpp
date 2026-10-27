// JNI surface of app.yerel.engine.NativeBridge. Strings cross the boundary as UTF-8 byte
// arrays, because JNI's modified UTF-8 mangles characters outside the BMP (emoji).

#include "engine.h"
#include "json.h"
#include "llama.h"

#include <android/log.h>
#include <jni.h>

#include <string>

namespace {

yerel::Engine & engine() {
    static yerel::Engine e;
    return e;
}

std::string to_string(JNIEnv * env, jbyteArray bytes) {
    if (!bytes) {
        return {};
    }
    const jsize n = env->GetArrayLength(bytes);
    std::string s((size_t) n, '\0');
    env->GetByteArrayRegion(bytes, 0, n, reinterpret_cast<jbyte *>(s.data()));
    return s;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray((jsize) s.size());
    env->SetByteArrayRegion(arr, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

void log_callback(ggml_log_level level, const char * text, void *) {
    int prio = ANDROID_LOG_DEBUG;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  prio = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_INFO:  prio = ANDROID_LOG_INFO;  break;
        default: break;
    }
    __android_log_write(prio, "yerel-llama", text);
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_app_yerel_engine_NativeBridge_initBackends(JNIEnv * env, jobject, jbyteArray lib_dir) {
    llama_log_set(log_callback, nullptr);
    yerel::Engine::init_backends(to_string(env, lib_dir));
}

JNIEXPORT jbyteArray JNICALL
Java_app_yerel_engine_NativeBridge_systemInfo(JNIEnv * env, jobject) {
    return to_bytes(env, yerel::Engine::system_info());
}

// progress: object with `boolean onProgress(float)`, may be null
JNIEXPORT jbyteArray JNICALL
Java_app_yerel_engine_NativeBridge_load(JNIEnv * env, jobject, jbyteArray path, jint n_gpu_layers,
                                        jboolean use_mmap, jboolean use_mlock, jobject progress) {
    yerel::LoadParams params;
    params.n_gpu_layers = n_gpu_layers;
    params.use_mmap     = use_mmap;
    params.use_mlock    = use_mlock;

    yerel::ProgressFn fn;
    jmethodID on_progress = nullptr;
    if (progress) {
        on_progress = env->GetMethodID(env->GetObjectClass(progress), "onProgress", "(F)Z");
        fn = [&](float p) {
            const jboolean keep = env->CallBooleanMethod(progress, on_progress, p);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return false;
            }
            return (bool) keep;
        };
    }
    return to_bytes(env, engine().load(to_string(env, path), params, fn));
}

JNIEXPORT jbyteArray JNICALL
Java_app_yerel_engine_NativeBridge_createContext(JNIEnv * env, jobject, jbyteArray params_json) {
    yerel::ContextParams cp;
    try {
        const auto j = common_json::parse(to_string(env, params_json));
        auto num = [&](const char * key, int & dst) {
            if (j.contains(key) && j.at(key).is_number()) {
                dst = j.at(key).get<int>();
            }
        };
        num("n_ctx", cp.n_ctx);
        num("n_batch", cp.n_batch);
        num("n_ubatch", cp.n_ubatch);
        num("n_threads", cp.n_threads);
        num("n_threads_batch", cp.n_threads_batch);
        num("flash_attn", cp.flash_attn);
        if (j.contains("type_k") && j.at("type_k").is_string()) {
            cp.type_k = j.at("type_k").get<std::string>();
        }
        if (j.contains("type_v") && j.at("type_v").is_string()) {
            cp.type_v = j.at("type_v").get<std::string>();
        }
    } catch (const std::exception & e) {
        return to_bytes(env, std::string("invalid context params: ") + e.what());
    }
    return to_bytes(env, engine().create_context(cp));
}

JNIEXPORT jbyteArray JNICALL
Java_app_yerel_engine_NativeBridge_modelInfo(JNIEnv * env, jobject) {
    return to_bytes(env, engine().model_info_json());
}

// listener: object with `void onUpdate(byte[])`, may be null
JNIEXPORT jbyteArray JNICALL
Java_app_yerel_engine_NativeBridge_generate(JNIEnv * env, jobject, jbyteArray messages, jbyteArray tools,
                                            jbyteArray params, jobject listener) {
    yerel::GenParams gp;
    try {
        gp = yerel::GenParams::from_json(to_string(env, params));
    } catch (const std::exception & e) {
        return to_bytes(env, std::string("{\"error\":\"invalid generation params\"}"));
    }
    yerel::UpdateFn fn;
    jmethodID on_update = nullptr;
    if (listener) {
        on_update = env->GetMethodID(env->GetObjectClass(listener), "onUpdate", "([B)V");
        fn = [&](const std::string & json) {
            jbyteArray arr = to_bytes(env, json);
            env->CallVoidMethod(listener, on_update, arr);
            env->DeleteLocalRef(arr);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        };
    }
    return to_bytes(env, engine().generate(to_string(env, messages), to_string(env, tools), gp, fn));
}

JNIEXPORT void JNICALL
Java_app_yerel_engine_NativeBridge_cancel(JNIEnv *, jobject) {
    engine().cancel();
}

JNIEXPORT jint JNICALL
Java_app_yerel_engine_NativeBridge_countTokens(JNIEnv * env, jobject, jbyteArray text) {
    return engine().count_tokens(to_string(env, text));
}

JNIEXPORT void JNICALL
Java_app_yerel_engine_NativeBridge_unload(JNIEnv *, jobject) {
    engine().unload();
}

} // extern "C"
