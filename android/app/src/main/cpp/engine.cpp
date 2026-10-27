#include "engine.h"

#include "chat.h"
#include "common.h"
#include "ggml-backend.h"
#include "json.h"
#include "llama.h"
#include "sampling.h"
#include "speculative.h"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <stdexcept>

#if defined(__ANDROID__)
#include <android/log.h>
#include <dlfcn.h>
#endif

namespace yerel {

namespace {

using clock_type = std::chrono::steady_clock;

double ms_since(clock_type::time_point t0) {
    return std::chrono::duration<double, std::milli>(clock_type::now() - t0).count();
}

ggml_type kv_type_from_str(const std::string & s) {
    static const std::pair<const char *, ggml_type> types[] = {
        {"f32", GGML_TYPE_F32},   {"f16", GGML_TYPE_F16},   {"bf16", GGML_TYPE_BF16},
        {"q8_0", GGML_TYPE_Q8_0}, {"q5_1", GGML_TYPE_Q5_1}, {"q5_0", GGML_TYPE_Q5_0},
        {"q4_1", GGML_TYPE_Q4_1}, {"q4_0", GGML_TYPE_Q4_0}, {"iq4_nl", GGML_TYPE_IQ4_NL},
    };
    for (const auto & [name, type] : types) {
        if (s == name) {
            return type;
        }
    }
    throw std::invalid_argument("unknown KV cache type: " + s);
}

// length of the longest prefix of s that is complete UTF-8
size_t utf8_complete_prefix(const std::string & s) {
    size_t n = s.size();
    // walk back over at most 3 continuation bytes to the last lead byte
    for (size_t back = 1; back <= 4 && back <= n; ++back) {
        const auto c = (unsigned char) s[n - back];
        if ((c & 0xC0) == 0x80) {
            continue;
        }
        size_t need = 1;
        if ((c & 0xE0) == 0xC0) {
            need = 2;
        } else if ((c & 0xF0) == 0xE0) {
            need = 3;
        } else if ((c & 0xF8) == 0xF0) {
            need = 4;
        }
        return back >= need ? n : n - back;
    }
    return n;
}

std::string json_escape(const std::string & s) {
    std::string out;
    out.reserve(s.size() + 2);
    out += '"';
    for (const char ch : s) {
        const auto c = (unsigned char) ch;
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += ch;
                }
        }
    }
    out += '"';
    return out;
}

std::string error_json(const std::string & msg) {
    return "{\"error\":" + json_escape(msg) + "}";
}

std::string msg_to_json(const common_chat_msg & msg) {
    return common_chat_msgs_to_json_oaicompat({ msg }).at(0).dump();
}

std::string meta_str(const llama_model * model, const char * key) {
    char buf[512];
    const int n = llama_model_meta_val_str(model, key, buf, sizeof(buf));
    return n < 0 ? std::string() : std::string(buf);
}

// state of the recurrent / sliding-window part of the memory after n_tokens prompt tokens;
// lets the next turn reuse the prompt on models where the memory cannot be truncated
struct Checkpoint {
    size_t               n_tokens = 0;
    std::vector<uint8_t> data;
};

constexpr size_t MAX_CHECKPOINTS = 4;

} // namespace

GenParams GenParams::from_json(const std::string & text) {
    GenParams p;
    const auto j = common_json::parse(text);
    auto num = [&](const char * key, auto & dst) {
        if (j.contains(key) && j.at(key).is_number()) {
            dst = j.at(key).get<std::decay_t<decltype(dst)>>();
        }
    };
    num("temp", p.temp);
    num("top_k", p.top_k);
    num("top_p", p.top_p);
    num("min_p", p.min_p);
    num("repeat_penalty", p.repeat_penalty);
    num("repeat_last_n", p.repeat_last_n);
    num("max_tokens", p.max_tokens);
    num("reasoning_budget", p.reasoning_budget);
    if (j.contains("seed") && j.at("seed").is_number()) {
        const auto seed = j.at("seed").get<long long>();
        p.seed = seed < 0 ? 0xFFFFFFFF : (uint32_t) seed;
    }
    if (j.contains("enable_thinking") && j.at("enable_thinking").is_boolean()) {
        p.enable_thinking = j.at("enable_thinking").get<bool>();
    }
    if (j.contains("continue") && j.at("continue").is_boolean()) {
        p.continue_final = j.at("continue").get<bool>();
    }
    return p;
}

struct Engine::Impl {
    llama_model *             model = nullptr;
    llama_context *           ctx   = nullptr;
    const llama_vocab *       vocab = nullptr;
    common_chat_templates_ptr tmpls;
    ContextParams             cparams;
    LoadParams                lparams;
    std::string               device_desc = "CPU";
    std::string               device_note; // why the requested device is not used

    // speculative decoding
    llama_model *        model_dft = nullptr; // separate draft model, if any
    llama_context *      ctx_dft   = nullptr; // draft model or MTP context, kept in lockstep with ctx
    common_speculative * spec      = nullptr;
    bool                 has_mtp   = false;   // the GGUF carries MTP (nextn) layers and they were loaded
    bool                 draft_is_mtp = false; // the draft GGUF is an exported MTP head
    int                  n_spec_max = 0;
    std::string          spec_desc;           // active speculative types
    std::string          spec_note;           // why speculative decoding is (partly) off

    // tokens currently held in the memory of sequence 0
    llama_tokens            cached;
    std::vector<Checkpoint> checkpoints;
    bool                    needs_checkpoints = false;

    std::atomic<bool> cancel_flag{false};

    void free_spec() {
        if (spec) {
            common_speculative_free(spec);
            spec = nullptr;
        }
        if (ctx_dft) {
            llama_free(ctx_dft);
            ctx_dft = nullptr;
        }
        spec_desc.clear();
        n_spec_max = 0;
    }

    void free_context() {
        free_spec();
        if (ctx) {
            llama_free(ctx);
            ctx = nullptr;
        }
        cached.clear();
        checkpoints.clear();
    }

    void clear_dft() {
        if (ctx_dft && llama_get_memory(ctx_dft)) {
            llama_memory_clear(llama_get_memory(ctx_dft), true);
        }
    }

    void reset_memory() {
        llama_memory_clear(llama_get_memory(ctx), true);
        clear_dft();
        cached.clear();
        checkpoints.clear();
    }

    // drops positions >= n from the draft context; false if it cannot be truncated there
    bool truncate_dft(size_t n) {
        if (!ctx_dft || !llama_get_memory(ctx_dft)) {
            return true;
        }
        return llama_memory_seq_rm(llama_get_memory(ctx_dft), 0, (llama_pos) n, -1);
    }

    void save_checkpoint() {
        Checkpoint ck;
        ck.n_tokens   = cached.size();
        const size_t n = llama_state_seq_get_size_ext(ctx, 0, LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY);
        ck.data.resize(n);
        if (llama_state_seq_get_data_ext(ctx, ck.data.data(), n, 0, LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY) != n) {
            return;
        }
        checkpoints.erase(std::remove_if(checkpoints.begin(), checkpoints.end(),
                                         [&](const Checkpoint & c) { return c.n_tokens >= ck.n_tokens; }),
                          checkpoints.end());
        checkpoints.push_back(std::move(ck));
        while (checkpoints.size() > MAX_CHECKPOINTS) {
            checkpoints.erase(checkpoints.begin());
        }
    }

    // makes the memory hold exactly tokens[0, n_keep) and returns the number of tokens kept,
    // which can be smaller than n_keep when the memory cannot be truncated there
    size_t truncate_to(size_t n_keep) {
        const size_t n = truncate_target(n_keep);
        if (!truncate_dft(n)) {
            reset_memory();
            return 0;
        }
        return n;
    }

    size_t truncate_target(size_t n_keep) {
        auto * mem = llama_get_memory(ctx);
        if (n_keep >= cached.size()) {
            return cached.size();
        }
        if (!needs_checkpoints) {
            if (llama_memory_seq_rm(mem, 0, (llama_pos) n_keep, -1)) {
                cached.resize(n_keep);
                return n_keep;
            }
            reset_memory();
            return 0;
        }
        // restore the latest checkpoint at or before n_keep
        for (auto it = checkpoints.rbegin(); it != checkpoints.rend(); ++it) {
            if (it->n_tokens > n_keep || it->n_tokens == 0) {
                continue;
            }
            if (llama_state_seq_set_data_ext(ctx, it->data.data(), it->data.size(), 0,
                                             LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY) != it->data.size()) {
                break;
            }
            const size_t n = it->n_tokens;
            if (!llama_memory_seq_rm(mem, 0, (llama_pos) n, -1)) {
                break;
            }
            cached.resize(n);
            checkpoints.erase(std::remove_if(checkpoints.begin(), checkpoints.end(),
                                             [&](const Checkpoint & c) { return c.n_tokens > n; }),
                              checkpoints.end());
            return n;
        }
        reset_memory();
        return 0;
    }

    // decodes a batch on the target and feeds it to the speculative implementations
    int decode(common_batch & batch) {
        const int rc = llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch.get());
        if (rc == 0 && spec && !common_speculative_process(spec, batch)) {
            // the draft state no longer follows the target, continue without speculation
            free_spec();
            spec_note = "hızlandırma hata verdi ve kapatıldı";
        }
        return rc;
    }

    // decodes tokens[cached.size(), end) in batches; returns false on error or cancel
    bool prefill(const llama_tokens & tokens, size_t end, std::string & err) {
        const size_t n_batch = (size_t) std::max(1, cparams.n_batch);
        common_batch batch(ctx);
        while (cached.size() < end) {
            const size_t start = cached.size();
            const size_t n     = std::min(n_batch, end - start);
            batch.clear();
            for (size_t i = 0; i < n; ++i) {
                batch.add(tokens[start + i], (llama_pos) (start + i), 0, false);
            }
            const int rc = decode(batch);
            if (rc != 0) {
                // aborted or failed batches can leave a partial state behind
                reset_memory();
                err = rc == 2 ? "cancelled" : "llama_decode failed (" + std::to_string(rc) + ")";
                return false;
            }
            cached.insert(cached.end(), tokens.begin() + start, tokens.begin() + start + n);
        }
        return true;
    }

    void setup_spec(const llama_context_params & cp);
};

Engine::Engine() : impl(std::make_unique<Impl>()) {}

Engine::~Engine() {
    unload();
}

namespace {

// cpu, gpu (OpenCL, Vulkan, ...), npu (Hexagon) or other
std::string device_kind(ggml_backend_dev_t dev) {
    switch (ggml_backend_dev_type(dev)) {
        case GGML_BACKEND_DEVICE_TYPE_CPU:
            return "cpu";
        case GGML_BACKEND_DEVICE_TYPE_GPU:
        case GGML_BACKEND_DEVICE_TYPE_IGPU: {
            const std::string reg = ggml_backend_reg_name(ggml_backend_dev_backend_reg(dev));
            return reg.find("HTP") != std::string::npos || reg.find("Hexagon") != std::string::npos ? "npu" : "gpu";
        }
        default:
            return "other";
    }
}

std::vector<ggml_backend_dev_t> devices_of_kind(const std::string & kind) {
    std::vector<ggml_backend_dev_t> out;
    for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
        auto * dev = ggml_backend_dev_get(i);
        if (device_kind(dev) == kind) {
            out.push_back(dev);
        }
    }
    return out;
}

bool has_cpu_backend() {
    return !devices_of_kind("cpu").empty();
}

bool has_reg(const char * name) {
    return ggml_backend_reg_by_name(name) != nullptr;
}

#if defined(__ANDROID__)
void log_android(int prio, const std::string & msg) {
    __android_log_write(prio, "yerel-engine", msg.c_str());
}

// Loads the CPU backend by library name. The dynamic linker finds names inside the APK, so this
// also works when native libraries are not extracted to nativeLibraryDir. Variants are tried from
// the most capable down; each reports through ggml_backend_score() whether this CPU can run it.
void load_cpu_backend_by_name() {
    std::vector<std::string> names;
#if defined(__aarch64__)
    for (const char * v : {"android_armv9.2_2", "android_armv9.2_1", "android_armv9.0_1", "android_armv8.6_1",
                           "android_armv8.2_2", "android_armv8.2_1", "android_armv8.0_1"}) {
        names.push_back(std::string("libggml-cpu-") + v + ".so");
    }
#endif
    names.push_back("libggml-cpu.so");

    for (const auto & name : names) {
        void * handle = dlopen(name.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            const char * err = dlerror();
            log_android(ANDROID_LOG_DEBUG, name + ": " + (err ? err : "not found"));
            continue;
        }
        using score_fn = int (*)();
        auto score = reinterpret_cast<score_fn>(dlsym(handle, "ggml_backend_score"));
        const int s = score ? score() : 1; // single-variant builds have no score function
        dlclose(handle);
        if (s <= 0) {
            log_android(ANDROID_LOG_INFO, name + ": not supported by this CPU");
            continue;
        }
        if (ggml_backend_load(name.c_str())) {
            log_android(ANDROID_LOG_INFO, "CPU backend: " + name);
            return;
        }
        log_android(ANDROID_LOG_WARN, "ggml_backend_load failed for " + name);
    }
}
#endif

} // namespace

void Engine::init_backends(const std::string & lib_dir) {
#if defined(__ANDROID__)
    // accelerators are loaded on demand (load_backend): initialising OpenCL compiles its kernels
    // and Hexagon opens a DSP session, neither is wanted when the model runs on the CPU
    (void) lib_dir;
    load_cpu_backend_by_name();
#else
    if (lib_dir.empty()) {
        ggml_backend_load_all();
    } else {
        ggml_backend_load_all_from_path(lib_dir.c_str());
    }
#endif
#if defined(__ANDROID__)
    if (!has_cpu_backend()) {
        log_android(ANDROID_LOG_ERROR, "no CPU backend could be loaded from " + lib_dir);
    }
#endif
    llama_backend_init();
}

std::string Engine::system_info() {
    std::string info = llama_print_system_info();
    info += "\nbackends:";
    for (size_t i = 0; i < ggml_backend_reg_count(); ++i) {
        info += " ";
        info += ggml_backend_reg_name(ggml_backend_reg_get(i));
    }
    if (!has_cpu_backend()) {
        info += " (CPU backend missing)";
    }
    return info;
}

bool Engine::load_backend(const std::string & kind) {
#if defined(__ANDROID__)
    std::vector<std::pair<const char *, const char *>> libs; // registry name, library
    if (kind == "gpu") {
        libs = { { "OpenCL", "libggml-opencl.so" }, { "Vulkan", "libggml-vulkan.so" } };
    } else if (kind == "npu") {
        libs = { { "HTP", "libggml-hexagon.so" } };
    }
    for (const auto & [reg, lib] : libs) {
        if (has_reg(reg) || !devices_of_kind(kind).empty()) {
            break;
        }
        if (ggml_backend_load(lib)) {
            log_android(ANDROID_LOG_INFO, std::string(lib) + " loaded");
        } else {
            log_android(ANDROID_LOG_INFO, std::string(lib) + " not available on this device");
        }
    }
#endif
    return !devices_of_kind(kind).empty();
}

std::string Engine::devices_json() {
    std::string out = "[";
    for (size_t i = 0; i < ggml_backend_dev_count(); ++i) {
        auto * dev = ggml_backend_dev_get(i);
        size_t free = 0;
        size_t total = 0;
        ggml_backend_dev_memory(dev, &free, &total);
        if (i > 0) {
            out += ",";
        }
        out += "{\"name\":" + json_escape(ggml_backend_dev_name(dev));
        out += ",\"desc\":" + json_escape(ggml_backend_dev_description(dev));
        out += ",\"kind\":" + json_escape(device_kind(dev));
        out += ",\"mem_total\":" + std::to_string(total);
        out += ",\"mem_free\":" + std::to_string(free) + "}";
    }
    return out + "]";
}

std::string Engine::load(const std::string & path, const LoadParams & params, const ProgressFn & progress) {
    unload();
    auto & d   = *impl;
    d.lparams  = params;

    // device selection: the layers go to the first device of the requested kind, the rest stays on the CPU
    static std::vector<ggml_backend_dev_t> devs; // the model keeps the list for its lifetime
    devs.clear();
    d.device_desc = "CPU";
    d.device_note.clear();
    if (params.device == "gpu" || params.device == "npu") {
        if (load_backend(params.device)) {
            const auto found = devices_of_kind(params.device);
            devs.push_back(found.front());
            d.device_desc = std::string(ggml_backend_dev_description(found.front())) + " (" +
                            ggml_backend_dev_name(found.front()) + ")";
        } else {
            d.device_note = params.device == "gpu" ? "GPU bulunamadı, CPU kullanılıyor" : "NPU bulunamadı, CPU kullanılıyor";
        }
    }
    devs.push_back(nullptr);

    auto mparams         = llama_model_default_params();
    mparams.devices      = devs.data();
    mparams.n_gpu_layers = devs.size() > 1 ? params.n_gpu_layers : 0;
    if (params.use_mmap) {
        mparams.load_mode = params.use_mlock ? LLAMA_LOAD_MODE_MMAP_MLOCK : LLAMA_LOAD_MODE_MMAP;
    } else {
        mparams.load_mode = params.use_mlock ? LLAMA_LOAD_MODE_MLOCK : LLAMA_LOAD_MODE_NONE;
    }
    struct ProgressCtx {
        const ProgressFn * fn;
        float              scale;
        float              offset;
    } pctx{ &progress, 1.0f, 0.0f };
    if (progress) {
        mparams.progress_callback = [](float p, void * ud) {
            auto * c = static_cast<ProgressCtx *>(ud);
            return (*c->fn)(c->offset + p * c->scale);
        };
        mparams.progress_callback_user_data = &pctx;
    }

    // MTP layers are only loaded when they will be used
    const bool want_mtp = params.spec_mode == "auto" || params.spec_mode == "mtp";
    if (want_mtp) {
        const auto types = common_speculative_types_from_gguf(path);
        d.has_mtp = std::find(types.begin(), types.end(), COMMON_SPECULATIVE_TYPE_DRAFT_MTP) != types.end();
        mparams.load_mtp = d.has_mtp;
    }
    const bool want_draft = !params.draft_path.empty() && (params.spec_mode == "draft" ||
                                                           (params.spec_mode == "auto" && !d.has_mtp));
    if (want_draft) {
        pctx.scale = 0.85f;
    }

    d.model = llama_model_load_from_file(path.c_str(), mparams);
    if (!d.model) {
        d.has_mtp = false;
        return "model açılamadı: bu mimari desteklenmiyor ya da dosya bozuk/eksik";
    }
    d.vocab = llama_model_get_vocab(d.model);
    try {
        d.tmpls = common_chat_templates_init(d.model, "");
    } catch (const std::exception & e) {
        unload();
        return std::string("sohbet şablonu hatası: ") + e.what();
    }
    d.needs_checkpoints = llama_model_is_recurrent(d.model) || llama_model_is_hybrid(d.model) ||
                          llama_model_n_swa(d.model) > 0;
    if (d.has_mtp && llama_model_n_layer_nextn(d.model) <= 0) {
        d.has_mtp = false;
    }

    d.spec_note.clear();
    if (want_draft) {
        pctx.offset = 0.85f;
        pctx.scale  = 0.15f;
        const auto dtypes = common_speculative_types_from_gguf(params.draft_path);
        d.draft_is_mtp = std::find(dtypes.begin(), dtypes.end(), COMMON_SPECULATIVE_TYPE_DRAFT_MTP) != dtypes.end();
        const bool unsupported = !dtypes.empty() && !d.draft_is_mtp;
        auto dparams = mparams;
        dparams.load_mtp = d.draft_is_mtp;
        d.model_dft = unsupported ? nullptr : llama_model_load_from_file(params.draft_path.c_str(), dparams);
        if (unsupported) {
            d.spec_note = "bu taslak model türü (DFlash) desteklenmiyor";
        } else if (!d.model_dft) {
            d.spec_note = "taslak model açılamadı";
        } else if (!common_speculative_are_compatible(d.model, d.model_dft)) {
            llama_model_free(d.model_dft);
            d.model_dft = nullptr;
            d.spec_note = "taslak modelin sözlüğü bu modelle uyumsuz";
        }
    }
    return "";
}

void Engine::Impl::setup_spec(const llama_context_params & cp) {
    free_spec();
    const std::string & mode = lparams.spec_mode;
    if (mode != "auto" && mode != "ngram" && mode != "draft" && mode != "mtp") {
        return;
    }
    const auto seq_rm = common_context_can_seq_rm(ctx);
    if (seq_rm != COMMON_CONTEXT_SEQ_RM_TYPE_PART && seq_rm != COMMON_CONTEXT_SEQ_RM_TYPE_RS) {
        spec_note = "bu model mimarisinde hızlandırma desteklenmiyor";
        return;
    }

    common_params_speculative sp;
    sp.types.clear();
    sp.draft.n_max = std::clamp(lparams.n_draft, 1, 16);
    // prompt lookup: when the last 4 tokens appeared before, propose the 12 that followed them
    sp.ngram_simple.size_n = 4;
    sp.ngram_simple.size_m = 12;
    if (mode == "auto" || mode == "ngram") {
        sp.types.push_back(COMMON_SPECULATIVE_TYPE_NGRAM_SIMPLE);
    }

    const bool use_mtp   = has_mtp && (mode == "auto" || mode == "mtp");
    const bool use_draft = !use_mtp && model_dft && (mode == "auto" || mode == "draft");
    if (mode == "mtp" && !has_mtp) {
        spec_note = "bu modelde MTP katmanı yok";
    }
    if (mode == "draft" && !model_dft && spec_note.empty()) {
        spec_note = "taslak model seçilmedi";
    }
    if (use_mtp || use_draft) {
        auto dp            = llama_context_default_params();
        dp.n_ctx           = llama_n_ctx(ctx);
        dp.n_batch         = cp.n_batch;
        dp.n_ubatch        = cp.n_ubatch;
        dp.n_seq_max       = 1;
        dp.n_threads       = cp.n_threads;
        dp.n_threads_batch = cp.n_threads_batch;
        dp.type_k          = cp.type_k;
        dp.type_v          = cp.type_v;
        dp.flash_attn_type = cp.flash_attn_type;
        dp.n_outputs_max         = 1;
        dp.n_outputs_max_per_seq = 1;
        dp.n_rs_seq        = 0;
        dp.ctx_other       = ctx;
        dp.abort_callback      = cp.abort_callback;
        dp.abort_callback_data = cp.abort_callback_data;
        const bool mtp_ctx = use_mtp || draft_is_mtp;
        if (mtp_ctx) {
            dp.ctx_type = LLAMA_CONTEXT_TYPE_MTP;
        }
        ctx_dft = llama_init_from_model(use_mtp ? model : model_dft, dp);
        if (!ctx_dft) {
            spec_note = mtp_ctx ? "MTP bağlamı oluşturulamadı" : "taslak model bağlamı oluşturulamadı";
        } else if (!mtp_ctx && common_context_can_seq_rm(ctx_dft) != COMMON_CONTEXT_SEQ_RM_TYPE_PART) {
            llama_free(ctx_dft);
            ctx_dft   = nullptr;
            spec_note = "taslak model mimarisi desteklenmiyor (yalnız transformer modeller)";
        } else {
            sp.types.push_back(mtp_ctx ? COMMON_SPECULATIVE_TYPE_DRAFT_MTP : COMMON_SPECULATIVE_TYPE_DRAFT_SIMPLE);
            sp.draft.ctx_tgt = ctx;
            sp.draft.ctx_dft = ctx_dft;
        }
    }
    if (sp.types.empty()) {
        if (ctx_dft) {
            llama_free(ctx_dft);
            ctx_dft = nullptr;
        }
        return;
    }
    n_spec_max = common_speculative_n_max(&sp);
    spec       = common_speculative_init(sp, 1);
    if (!spec) {
        free_spec();
        spec_note = "hızlandırma başlatılamadı";
        return;
    }
    spec_desc = common_speculative_type_name_str(sp.types);
}

std::string Engine::create_context(const ContextParams & params) {
    if (!impl->model) {
        return "yüklü model yok";
    }
    impl->free_context();

    auto cp            = llama_context_default_params();
    cp.n_ctx           = (uint32_t) std::max(256, params.n_ctx);
    cp.n_batch         = (uint32_t) std::max(32, params.n_batch);
    cp.n_ubatch        = (uint32_t) std::clamp(params.n_ubatch, 32, (int) cp.n_batch);
    cp.n_seq_max       = 1;
    cp.n_threads       = std::max(1, params.n_threads);
    cp.n_threads_batch = std::max(1, params.n_threads_batch);
    try {
        cp.type_k = kv_type_from_str(params.type_k);
        cp.type_v = kv_type_from_str(params.type_v);
    } catch (const std::exception & e) {
        return e.what();
    }
    cp.flash_attn_type = params.flash_attn < 0  ? LLAMA_FLASH_ATTN_TYPE_AUTO :
                         params.flash_attn == 0 ? LLAMA_FLASH_ATTN_TYPE_DISABLED :
                                                  LLAMA_FLASH_ATTN_TYPE_ENABLED;
    // a quantized V cache only works with flash attention
    if (cp.type_v != GGML_TYPE_F16 && cp.type_v != GGML_TYPE_F32 && cp.type_v != GGML_TYPE_BF16 &&
        cp.flash_attn_type == LLAMA_FLASH_ATTN_TYPE_DISABLED) {
        cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    }
    cp.abort_callback = [](void * ud) {
        return static_cast<Impl *>(ud)->cancel_flag.load();
    };
    cp.abort_callback_data = impl.get();
    // recurrent models keep snapshots of their last states so rejected draft tokens can be rolled back
    const std::string & mode = impl->lparams.spec_mode;
    if (mode == "auto" || mode == "ngram" || mode == "draft" || mode == "mtp") {
        const int n_draft = std::clamp(impl->lparams.n_draft, 1, 16);
        cp.n_rs_seq = (uint32_t) (mode == "auto" || mode == "ngram" ? std::max(12, n_draft) : n_draft);
    }

    impl->ctx = llama_init_from_model(impl->model, cp);
    if (!impl->ctx) {
        return "bağlam oluşturulamadı: bu bağlam boyutu için bellek yetmiyor olabilir, Ayarlar'dan küçültün";
    }
    impl->cparams       = params;
    impl->cparams.n_ctx = (int) llama_n_ctx(impl->ctx);
    impl->cparams.n_batch = (int) cp.n_batch;
    impl->setup_spec(cp);
    return "";
}

void Engine::unload() {
    impl->free_context();
    impl->tmpls.reset();
    if (impl->model_dft) {
        llama_model_free(impl->model_dft);
        impl->model_dft = nullptr;
    }
    impl->has_mtp      = false;
    impl->draft_is_mtp = false;
    if (impl->model) {
        llama_model_free(impl->model);
        impl->model = nullptr;
    }
    impl->vocab = nullptr;
}

bool Engine::loaded() const {
    return impl->model != nullptr && impl->ctx != nullptr;
}

std::string Engine::model_info_json() const {
    if (!impl->model) {
        return "{}";
    }
    auto * m = impl->model;
    char desc[256];
    llama_model_desc(m, desc, sizeof(desc));
    const auto caps = common_chat_templates_get_caps(impl->tmpls.get());
    auto cap = [&](const char * key) {
        const auto it = caps.find(key);
        return it != caps.end() && it->second;
    };

    std::string out = "{";
    out += "\"desc\":" + json_escape(desc);
    out += ",\"name\":" + json_escape(meta_str(m, "general.name"));
    out += ",\"arch\":" + json_escape(meta_str(m, "general.architecture"));
    out += ",\"size_bytes\":" + std::to_string(llama_model_size(m));
    out += ",\"n_params\":" + std::to_string(llama_model_n_params(m));
    out += ",\"n_ctx_train\":" + std::to_string(llama_model_n_ctx_train(m));
    out += ",\"n_layer\":" + std::to_string(llama_model_n_layer(m));
    out += ",\"n_swa\":" + std::to_string(llama_model_n_swa(m));
    out += ",\"recurrent\":" + std::string(impl->needs_checkpoints ? "true" : "false");
    out += ",\"has_template\":" + std::string(common_chat_templates_was_explicit(impl->tmpls.get()) ? "true" : "false");
    out += ",\"supports_thinking\":" +
           std::string(common_chat_templates_support_enable_thinking(impl->tmpls.get()) ? "true" : "false");
    out += ",\"supports_tools\":" + std::string(cap("supports_tools") ? "true" : "false");
    if (impl->ctx) {
        out += ",\"n_ctx\":" + std::to_string(llama_n_ctx(impl->ctx));
    }
    out += ",\"device\":" + json_escape(impl->device_desc);
    out += ",\"device_note\":" + json_escape(impl->device_note);
    out += ",\"has_mtp\":" + std::string(impl->has_mtp ? "true" : "false");
    out += ",\"has_draft\":" + std::string(impl->model_dft ? "true" : "false");
    out += ",\"spec\":" + json_escape(impl->spec_desc);
    out += ",\"spec_note\":" + json_escape(impl->spec_note);
    out += "}";
    return out;
}

void Engine::cancel() {
    impl->cancel_flag = true;
}

int Engine::count_tokens(const std::string & text) const {
    if (!impl->vocab) {
        return 0;
    }
    return (int) common_tokenize(impl->vocab, text, false, true).size();
}

std::string Engine::generate(const std::string & messages_json,
                             const std::string & tools_json,
                             const GenParams &   params,
                             const UpdateFn &    on_update) {
    auto & d = *impl;
    if (!d.model || !d.ctx) {
        return error_json("yüklü model yok");
    }
    d.cancel_flag = false;

    common_chat_templates_inputs inputs;
    try {
        inputs.messages = common_chat_msgs_parse_oaicompat(common_json::parse(messages_json));
        if (!tools_json.empty()) {
            const auto tools = common_json::parse(tools_json);
            if (tools.is_array() && !tools.empty()) {
                inputs.tools = common_chat_tools_parse_oaicompat(tools);
            }
        }
    } catch (const std::exception & e) {
        return error_json(std::string("geçersiz istek: ") + e.what());
    }
    inputs.add_generation_prompt = true;
    inputs.use_jinja             = true;
    inputs.tool_choice           = COMMON_CHAT_TOOL_CHOICE_AUTO;
    inputs.parallel_tool_calls   = false;
    inputs.reasoning_format      = COMMON_REASONING_FORMAT_DEEPSEEK;
    inputs.enable_thinking       = params.enable_thinking;
    if (params.continue_final && !inputs.messages.empty() && inputs.messages.back().role == "assistant") {
        inputs.continue_final_message = COMMON_CHAT_CONTINUATION_AUTO;
    }

    std::unique_ptr<common_chat_session> session;
    try {
        session = std::make_unique<common_chat_session>(d.tmpls.get(), d.vocab, inputs);
    } catch (const std::exception & e) {
        return error_json(std::string("sohbet şablonu hatası: ") + e.what());
    }

    const auto t_start = clock_type::now();
    const llama_tokens tokens = common_tokenize(d.vocab, session->prompt().str(), true, true);
    const size_t n_ctx = (size_t) d.cparams.n_ctx;
    if (tokens.empty()) {
        return error_json("boş istem");
    }
    if (tokens.size() + 8 >= n_ctx) {
        return "{\"error\":\"context_overflow\",\"prompt_tokens\":" + std::to_string(tokens.size()) +
               ",\"n_ctx\":" + std::to_string(n_ctx) + "}";
    }

    // reuse the longest common prefix of the memory, at least one prompt token must be decoded
    size_t n_common = 0;
    while (n_common < d.cached.size() && n_common < tokens.size() && d.cached[n_common] == tokens[n_common]) {
        ++n_common;
    }
    n_common = std::min(n_common, tokens.size() - 1);
    // drop checkpoints that cover tokens not shared with this prompt
    d.checkpoints.erase(std::remove_if(d.checkpoints.begin(), d.checkpoints.end(),
                                       [&](const Checkpoint & c) { return c.n_tokens > n_common; }),
                        d.checkpoints.end());
    const size_t n_reused = d.truncate_to(n_common);

    // checkpoint before the generation prompt, which the next turn re-renders as the start of this reply
    size_t n_ckpt = 0;
    if (d.needs_checkpoints) {
        const size_t n_gen_prompt = common_tokenize(d.vocab, session->generation_prompt(), false, true).size();
        if (tokens.size() > n_gen_prompt + 1) {
            n_ckpt = tokens.size() - n_gen_prompt - 1;
        }
    }

    std::string err;
    if (n_ckpt > d.cached.size()) {
        if (!d.prefill(tokens, n_ckpt, err)) {
            return error_json(err);
        }
        d.save_checkpoint();
    }
    // the last prompt token is decoded by the generation loop, together with the first draft
    if (!d.prefill(tokens, tokens.size() - 1, err)) {
        return error_json(err);
    }
    const double prompt_ms     = ms_since(t_start);
    const size_t prompt_tokens = tokens.size();

    // sampler with the template's tool-call grammar, preserved tokens and stops
    common_params_sampling sp;
    sp.temp           = params.temp;
    sp.top_k          = params.top_k;
    sp.top_p          = params.top_p;
    sp.min_p          = params.min_p;
    sp.penalty_repeat = params.repeat_penalty;
    sp.penalty_last_n = params.repeat_last_n;
    sp.seed           = params.seed;
    session->apply_sampling(sp);
    if (params.reasoning_budget >= 0 && !session->thinking_start_tag().empty() &&
        !session->thinking_end_tags().empty()) {
        sp.reasoning_budget_tokens = params.reasoning_budget;
        sp.reasoning_budget_start  = common_tokenize(d.vocab, session->thinking_start_tag(), false, true);
        for (const auto & tag : session->thinking_end_tags()) {
            if (!tag.empty()) {
                sp.reasoning_budget_end.push_back(common_tokenize(d.vocab, tag, false, true));
            }
        }
        if (!sp.reasoning_budget_end.empty()) {
            sp.reasoning_budget_forced = sp.reasoning_budget_end.front();
        }
    }
    common_sampler * smpl = common_sampler_init(d.model, sp);
    if (!smpl) {
        return error_json("örnekleyici oluşturulamadı");
    }
    const auto & stops = session->additional_stops();
    size_t max_stop_len = 0;
    for (const auto & s : stops) {
        max_stop_len = std::max(max_stop_len, s.size());
    }

    common_chat_input generated;
    size_t            n_fed    = 0; // bytes of `generated` given to the parser
    int               n_gen    = 0;
    int               n_drafted  = 0;
    int               n_accepted = 0;
    std::string       stop     = "length";
    auto              t_gen    = clock_type::now();
    auto              t_update = t_gen;

    // the parser cannot take text back, so a partial feed holds back what could still become a stop string
    auto feed_parser = [&](bool final) -> const common_chat_msg & {
        size_t upto = generated.size();
        if (!final) {
            upto = upto > max_stop_len ? upto - max_stop_len : 0;
            upto = utf8_complete_prefix(generated.text.substr(0, upto));
        }
        common_chat_input chunk = upto > n_fed ? generated.substr(n_fed, upto - n_fed) : common_chat_input();
        n_fed = std::max(n_fed, upto);
        return final ? session->finish(chunk) : session->feed(chunk);
    };

    if (d.spec) {
        common_speculative_begin(d.spec, 0, d.cached);
    }

    // id_last is sampled but not decoded yet; each step decodes it together with the draft tokens
    // proposed after it, then keeps the longest prefix of the draft that the target agrees with
    llama_token  id_last = tokens.back();
    llama_tokens draft;
    common_batch batch(d.ctx);
    bool         done = false;

    try {
        while (!done) {
            if (d.cancel_flag) {
                stop = "cancel";
                break;
            }
            if (n_gen >= params.max_tokens) {
                stop = "length";
                break;
            }
            if (d.cached.size() + 1 >= n_ctx) {
                stop = "ctx_full";
                break;
            }

            draft.clear();
            if (d.spec) {
                const int n_max = std::min({ (int) n_ctx - (int) d.cached.size() - 2, params.max_tokens - n_gen - 1,
                                             d.n_spec_max });
                if (n_max > 0) {
                    common_speculative_get_draft_params(d.spec, 0) = {
                        /* .drafting = */ true,
                        /* .n_max    = */ n_max,
                        /* .pos0     = */ (llama_pos) d.cached.size(),
                        /* .id_last  = */ id_last,
                        /* .prompt   = */ &d.cached,
                        /* .result   = */ &draft,
                    };
                    common_speculative_draft(d.spec);
                    if ((int) draft.size() > n_max) {
                        draft.resize(n_max);
                    }
                    // the draft model decoded its own proposals, the verification batch replaces them
                    if (!d.truncate_dft(d.cached.size())) {
                        d.free_spec();
                        d.spec_note = "taslak bağlam geri alınamadı, hızlandırma kapatıldı";
                        draft.clear();
                    }
                }
            }

            const llama_pos pos = (llama_pos) d.cached.size();
            batch.clear();
            batch.add(id_last, pos, 0, true);
            for (size_t i = 0; i < draft.size(); ++i) {
                batch.add(draft[i], pos + 1 + (llama_pos) i, 0, true);
            }
            const int rc = d.decode(batch);
            if (rc != 0) {
                stop = rc == 2 ? "cancel" : "error";
                d.reset_memory();
                break;
            }
            d.cached.push_back(id_last);

            // at least one token: the accepted draft prefix plus the target's own next token
            const auto   ids   = common_sampler_sample_and_accept_n(smpl, d.ctx, draft);
            const size_t n_acc = ids.size() - 1;
            n_drafted  += (int) draft.size();
            n_accepted += (int) n_acc;
            d.cached.insert(d.cached.end(), draft.begin(), draft.begin() + n_acc);
            if (d.spec) {
                common_speculative_accept(d.spec, 0, (uint16_t) n_acc);
            }
            if (n_acc < draft.size()) {
                // drop the rejected tail of the draft from both contexts
                if (!llama_memory_seq_rm(llama_get_memory(d.ctx), 0, (llama_pos) d.cached.size(), -1)) {
                    d.reset_memory();
                    stop = "error";
                    break;
                }
                if (!d.truncate_dft(d.cached.size())) {
                    d.free_spec();
                    d.spec_note = "taslak bağlam geri alınamadı, hızlandırma kapatıldı";
                }
            }

            for (const llama_token id : ids) {
                if (llama_vocab_is_eog(d.vocab, id)) {
                    stop = "eos";
                    done = true;
                    break;
                }
                const bool special = sp.preserved_tokens.count(id) > 0;
                generated.append(common_token_to_piece(d.vocab, id, special), id);
                ++n_gen;

                for (const auto & s : stops) {
                    if (s.empty()) {
                        continue;
                    }
                    const size_t from = generated.size() > s.size() + 32 ? generated.size() - s.size() - 32 : 0;
                    const size_t p    = generated.text.find(s, from);
                    if (p != std::string::npos) {
                        generated.truncate(p);
                        stop = "stop";
                        done = true;
                        break;
                    }
                }
                if (done) {
                    break;
                }
                if (n_gen >= params.max_tokens) {
                    stop = "length";
                    done = true;
                    break;
                }
            }
            id_last = ids.back();

            if (!done && on_update && ms_since(t_update) >= 50.0) {
                t_update = clock_type::now();
                const auto & msg = feed_parser(false);
                const double gen_ms = ms_since(t_gen);
                on_update("{\"message\":" + msg_to_json(msg) + ",\"n_gen\":" + std::to_string(n_gen) +
                          ",\"tps\":" + std::to_string(gen_ms > 0 ? n_gen * 1000.0 / gen_ms : 0.0) + "}");
            }
        }
    } catch (const std::exception & e) {
        common_sampler_free(smpl);
        return error_json(std::string("üretim hatası: ") + e.what());
    }
    common_sampler_free(smpl);

    common_chat_msg final_msg;
    try {
        final_msg = feed_parser(true);
    } catch (const std::exception & e) {
        // keep the raw text when the output does not match the expected format
        final_msg.role    = "assistant";
        final_msg.content = generated.text;
    }
    const double gen_ms = ms_since(t_gen);

    std::string out = "{\"message\":" + msg_to_json(final_msg);
    out += ",\"stats\":{";
    out += "\"prompt_tokens\":" + std::to_string(prompt_tokens);
    out += ",\"reused_tokens\":" + std::to_string(n_reused);
    out += ",\"prompt_ms\":" + std::to_string(prompt_ms);
    out += ",\"gen_tokens\":" + std::to_string(n_gen);
    out += ",\"gen_ms\":" + std::to_string(gen_ms);
    out += ",\"ctx_used\":" + std::to_string(d.cached.size());
    out += ",\"n_ctx\":" + std::to_string(n_ctx);
    out += ",\"stop\":" + json_escape(stop);
    out += ",\"draft_tokens\":" + std::to_string(n_drafted);
    out += ",\"draft_accepted\":" + std::to_string(n_accepted);
    out += "}}";
    return out;
}

} // namespace yerel
