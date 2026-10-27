// Inference engine shared by the Android JNI bridge and the host test.
// One model and one context at a time; calls must come from a single thread,
// except cancel() which may be called from any thread.
#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

namespace yerel {

struct LoadParams {
    std::string device       = "cpu"; // cpu | gpu | npu, falls back to cpu when not available
    int         n_gpu_layers = 999;   // layers offloaded when device is gpu or npu
    bool        use_mmap     = true;
    bool        use_mlock    = false;

    // speculative decoding: off | auto | ngram | draft | mtp
    // auto = n-gram lookup plus MTP heads of the model when present, otherwise the draft model if given
    std::string spec_mode  = "off";
    std::string draft_path;     // GGUF of a small model sharing the target's vocabulary
    int         n_draft    = 3; // max tokens proposed by the draft model / MTP head per step
};

struct ContextParams {
    int         n_ctx           = 4096;
    int         n_batch         = 512;
    int         n_ubatch        = 512;
    int         n_threads       = 4;
    int         n_threads_batch = 4;
    std::string type_k          = "f16";
    std::string type_v          = "f16";
    int         flash_attn      = -1; // -1 auto, 0 off, 1 on
};

struct GenParams {
    float    temp             = 0.7f;
    int      top_k            = 40;
    float    top_p            = 0.95f;
    float    min_p            = 0.05f;
    float    repeat_penalty   = 1.0f;
    int      repeat_last_n    = 64;
    int      max_tokens       = 2048;
    uint32_t seed             = 0xFFFFFFFF;
    bool     enable_thinking  = true;
    int      reasoning_budget = -1; // -1 = unlimited
    bool     continue_final   = false; // extend the last (assistant) message instead of starting a new one

    // parses the JSON object sent by the app, missing keys keep their defaults
    static GenParams from_json(const std::string & json);
};

using ProgressFn = std::function<bool(float)>;               // return false to abort loading
using UpdateFn   = std::function<void(const std::string &)>; // partial message JSON

class Engine {
  public:
    Engine();
    ~Engine();

    // loads ggml backends (CPU variants, GPU) from a directory, call once per process
    static void init_backends(const std::string & lib_dir);
    static std::string system_info();
    // loads an accelerator backend on demand: "gpu" (OpenCL/Vulkan) or "npu" (Hexagon);
    // returns true when a device of that kind is available afterwards
    static bool load_backend(const std::string & kind);
    // JSON array of the available devices: [{"name","desc","kind","mem_total","mem_free"}]
    static std::string devices_json();

    // all methods returning std::string return "" on success or an error message
    std::string load(const std::string & path, const LoadParams & params, const ProgressFn & progress);
    std::string create_context(const ContextParams & params);
    void        unload();

    bool        loaded() const;
    std::string model_info_json() const;

    // messages_json: OpenAI chat messages, tools_json: OpenAI tools array (or empty)
    // returns a JSON object: {"message": {...}, "stats": {...}} or {"error": "..."}
    std::string generate(const std::string & messages_json,
                         const std::string & tools_json,
                         const GenParams &   params,
                         const UpdateFn &    on_update);

    void cancel();
    int  count_tokens(const std::string & text) const;

  private:
    struct Impl;
    std::unique_ptr<Impl> impl;
};

} // namespace yerel
