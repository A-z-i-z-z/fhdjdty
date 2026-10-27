// Runs the app's inference engine against a real GGUF model on the host.
//   engine_test <model.gguf> [n_threads]
// Exits with a non-zero status when a required check fails.

#include "engine.h"
#include "json.h"
#include "llama.h"

#include <cstdio>
#include <cstdlib>
#include <string>
#include <thread>

using yerel::Engine;

static int g_failed = 0;

static void check(bool ok, const char * what, const std::string & detail = "") {
    printf("[%s] %s%s%s\n", ok ? "PASS" : "FAIL", what, detail.empty() ? "" : " :: ", detail.c_str());
    fflush(stdout);
    if (!ok) {
        ++g_failed;
    }
}

static void note(const char * what, const std::string & detail) {
    printf("[INFO] %s :: %s\n", what, detail.c_str());
    fflush(stdout);
}

static common_json run(Engine & eng, const std::string & messages, const std::string & tools,
                       const yerel::GenParams & gp, int * n_updates = nullptr) {
    int updates = 0;
    const std::string out = eng.generate(messages, tools, gp, [&](const std::string & partial) {
        ++updates;
        if (updates == 1) {
            note("first update", partial.substr(0, 200));
        }
    });
    if (n_updates) {
        *n_updates = updates;
    }
    note("result", out.substr(0, 600));
    return common_json::parse(out);
}

static std::string content_of(const common_json & r) {
    if (!r.contains("message") || !r.at("message").contains("content") || !r.at("message").at("content").is_string()) {
        return "";
    }
    return r.at("message").at("content").get<std::string>();
}

int main(int argc, char ** argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s model.gguf [n_threads]\n", argv[0]);
        return 2;
    }
    const int n_threads = argc > 2 ? atoi(argv[2]) : 4;

    // keep the output readable: llama.cpp warnings and errors only
    llama_log_set([](ggml_log_level level, const char * text, void *) {
        if (level == GGML_LOG_LEVEL_WARN || level == GGML_LOG_LEVEL_ERROR) {
            fputs(text, stderr);
        }
    }, nullptr);
    Engine::init_backends("");
    note("system", Engine::system_info());

    Engine eng;
    float last_progress = 0.0f;
    std::string err = eng.load(argv[1], {}, [&](float p) {
        last_progress = p;
        return true;
    });
    check(err.empty(), "load model", err);
    if (!err.empty()) {
        return 1;
    }
    check(last_progress > 0.99f, "load progress reaches 1.0", std::to_string(last_progress));

    yerel::ContextParams cp;
    cp.n_ctx           = 4096;
    cp.n_threads       = n_threads;
    cp.n_threads_batch = n_threads;
    cp.type_k          = "q8_0";
    cp.type_v          = "q8_0";
    err = eng.create_context(cp);
    check(err.empty(), "create context with q8_0 KV cache", err);
    if (!err.empty()) {
        return 1;
    }
    const auto info = common_json::parse(eng.model_info_json());
    note("model", info.dump());
    const bool supports_thinking = info.contains("supports_thinking") && info.at("supports_thinking").get<bool>();
    const bool supports_tools    = info.contains("supports_tools") && info.at("supports_tools").get<bool>();

    yerel::GenParams gp;
    gp.temp            = 0.0f; // greedy, so the checks are deterministic
    gp.max_tokens      = 256;
    gp.enable_thinking = false;

    // 1. plain answer
    const std::string q1 = R"([{"role":"system","content":"You are a helpful assistant. Answer briefly."},)"
                           R"({"role":"user","content":"What is 7 plus 5? Reply with just the number."}])";
    int n_updates = 0;
    auto r1 = run(eng, q1, "", gp, &n_updates);
    check(!r1.contains("error"), "basic generation", r1.dump());
    check(content_of(r1).find("12") != std::string::npos, "answer contains 12", content_of(r1));
    // updates are throttled to one per 50 ms, so a one-token answer may legitimately have none
    const double gen_ms = r1.contains("stats") ? r1.at("stats").at("gen_ms").get<double>() : 0.0;
    check(n_updates > 0 || gen_ms < 150, "streaming updates were sent",
          std::to_string(n_updates) + " in " + std::to_string((int) gen_ms) + " ms");

    // 2. second turn reuses the cached prompt
    const std::string a1 = content_of(r1);
    const std::string q2 = R"([{"role":"system","content":"You are a helpful assistant. Answer briefly."},)"
                           R"({"role":"user","content":"What is 7 plus 5? Reply with just the number."},)"
                           R"({"role":"assistant","content":)" + common_json(a1).dump() + "}," +
                           R"({"role":"user","content":"Now multiply that by 2. Reply with just the number."}])";
    auto r2 = run(eng, q2, "", gp);
    const long long reused = r2.contains("stats") ? r2.at("stats").at("reused_tokens").get<long long>() : 0;
    check(reused > 0, "second turn reuses cached tokens", std::to_string(reused));
    check(content_of(r2).find("24") != std::string::npos, "second answer contains 24", content_of(r2));

    // 3. tool calling round trip
    const std::string tools = R"([{"type":"function","function":{"name":"get_weather",)"
                              R"("description":"Get the current weather for a city",)"
                              R"("parameters":{"type":"object","properties":{"city":{"type":"string","description":"City name"}},"required":["city"]}}}])";
    const std::string q3 = R"([{"role":"system","content":"You are a helpful assistant. Use tools when they help."},)"
                           R"({"role":"user","content":"What is the weather in Istanbul right now?"}])";
    auto r3 = run(eng, q3, tools, gp);
    bool has_call = false;
    std::string call_id = "call_0";
    std::string call_args;
    if (r3.contains("message") && r3.at("message").contains("tool_calls") && r3.at("message").at("tool_calls").is_array() &&
        !r3.at("message").at("tool_calls").empty()) {
        const auto & tc = r3.at("message").at("tool_calls").at(0);
        has_call = tc.at("function").at("name").get<std::string>() == "get_weather";
        call_args = tc.at("function").at("arguments").get<std::string>();
        if (tc.contains("id") && tc.at("id").is_string() && !tc.at("id").get<std::string>().empty()) {
            call_id = tc.at("id").get<std::string>();
        }
    }
    if (supports_tools) {
        check(has_call, "model calls get_weather", r3.dump());
    } else {
        note("tools", "template does not declare tool support, call check skipped");
    }
    if (has_call) {
        check(call_args.find("Istanbul") != std::string::npos, "tool arguments mention Istanbul", call_args);
        const std::string q4 = R"([{"role":"system","content":"You are a helpful assistant. Use tools when they help."},)"
                               R"({"role":"user","content":"What is the weather in Istanbul right now?"},)"
                               R"({"role":"assistant","content":"","tool_calls":[{"id":)" + common_json(call_id).dump() +
                               R"(,"type":"function","function":{"name":"get_weather","arguments":)" +
                               common_json(call_args).dump() + "}}]}," +
                               R"({"role":"tool","tool_call_id":)" + common_json(call_id).dump() +
                               R"(,"content":"{\"city\":\"Istanbul\",\"temperature_c\":18,\"condition\":\"sunny\"}"}])";
        auto r4 = run(eng, q4, tools, gp);
        check(content_of(r4).find("18") != std::string::npos, "final answer uses the tool result (18)", content_of(r4));
    }

    // 3b. continue a truncated answer
    {
        yerel::GenParams gs = gp;
        gs.max_tokens       = 12;
        const std::string qc = R"([{"role":"user","content":"Count from 1 to 30, separated by commas."}])";
        auto rc1 = run(eng, qc, "", gs);
        const std::string part = content_of(rc1);
        const bool truncated = rc1.contains("stats") && rc1.at("stats").at("stop").get<std::string>() == "length";
        check(truncated, "short answer is cut by max_tokens", part);
        yerel::GenParams gc = gp;
        gc.max_tokens       = 160;
        gc.continue_final   = true;
        const std::string qc2 = R"([{"role":"user","content":"Count from 1 to 30, separated by commas."},)"
                                R"({"role":"assistant","content":)" + common_json(part).dump() + "}]";
        auto rc2 = run(eng, qc2, "", gc);
        const std::string rest = content_of(rc2);
        check(!rc2.contains("error") && !rest.empty(), "continue the truncated answer", rest.substr(0, 200));
        check((part + rest).find("29") != std::string::npos, "the continuation goes on with the count",
              (part + "|" + rest).substr(0, 300));
    }

    // 4. thinking
    if (supports_thinking) {
        yerel::GenParams gt = gp;
        gt.enable_thinking  = true;
        gt.max_tokens       = 512;
        auto r5 = run(eng, q1, "", gt);
        const bool has_reasoning = r5.contains("message") && r5.at("message").contains("reasoning_content") &&
                                   !r5.at("message").at("reasoning_content").get<std::string>().empty();
        check(has_reasoning, "thinking produces reasoning_content", r5.dump().substr(0, 300));

        yerel::GenParams gb = gt;
        gb.reasoning_budget = 16;
        auto r6 = run(eng, q1, "", gb);
        check(!r6.contains("error"), "generation with a reasoning budget of 16", r6.dump().substr(0, 300));
    }

    // 5. cancel from another thread
    {
        yerel::GenParams gc = gp;
        gc.max_tokens       = 2000;
        const std::string q = R"([{"role":"user","content":"Write a very long story about the sea."}])";
        std::string out;
        std::thread worker([&] {
            out = eng.generate(q, "", gc, [&](const std::string &) { eng.cancel(); });
        });
        worker.join();
        note("cancel result", out.substr(0, 300));
        const auto r = common_json::parse(out);
        const bool cancelled = r.contains("stats") && r.at("stats").at("stop").get<std::string>() == "cancel";
        check(cancelled, "generation stops on cancel");
    }

    // 6. the engine keeps working after a cancel
    auto r7 = run(eng, q1, "", gp);
    check(content_of(r7).find("12") != std::string::npos, "generation after cancel", content_of(r7));

    // 7. prompt larger than the context is reported, not crashed on
    {
        yerel::ContextParams small = cp;
        small.n_ctx = 256;
        check(eng.create_context(small).empty(), "recreate context with n_ctx=256");
        std::string big;
        for (int i = 0; i < 400; ++i) {
            big += "lorem ipsum dolor sit amet ";
        }
        const std::string q = R"([{"role":"user","content":)" + common_json(big).dump() + "}]";
        const auto r = common_json::parse(eng.generate(q, "", gp, nullptr));
        check(r.contains("error") && r.at("error").get<std::string>() == "context_overflow", "context overflow is reported",
              r.dump());
    }

    // 8. speculative decoding: n-gram lookup, the model as its own draft, MTP heads when present
    const std::string text = "The quick brown fox jumps over the lazy dog. A journey of a thousand miles begins "
                             "with a single step. All that glitters is not gold. Knowledge is power.";
    const std::string q_rep = R"([{"role":"user","content":)" +
                              common_json("Repeat the following text exactly, without any changes:\n\n" + text).dump() + "}]";
    yerel::GenParams gr = gp;
    gr.max_tokens       = 96;
    std::string baseline;
    {
        check(eng.create_context(cp).empty(), "recreate context for the speculative baseline");
        baseline = content_of(run(eng, q_rep, "", gr));
    }
    auto spec_run = [&](const char * mode, const std::string & draft_path) {
        yerel::LoadParams lp;
        lp.spec_mode  = mode;
        lp.draft_path = draft_path;
        std::string e = eng.load(argv[1], lp, nullptr);
        if (e.empty()) {
            e = eng.create_context(cp);
        }
        check(e.empty(), (std::string("load with spec_mode=") + mode).c_str(), e);
        const auto mi = common_json::parse(eng.model_info_json());
        note("spec", mi.at("spec").get<std::string>() + " / " + mi.at("spec_note").get<std::string>());
        return mi;
    };
    auto check_spec = [&](const char * label, bool require_accept) {
        auto r = run(eng, q_rep, "", gr);
        check(!r.contains("error"), (std::string(label) + ": generation").c_str(), r.dump().substr(0, 300));
        if (r.contains("error")) {
            return;
        }
        const auto & st = r.at("stats");
        const int drafted  = st.at("draft_tokens").get<int>();
        const int accepted = st.at("draft_accepted").get<int>();
        const double tps   = st.at("gen_tokens").get<double>() * 1000.0 / std::max(1.0, st.at("gen_ms").get<double>());
        note(label, std::to_string(accepted) + "/" + std::to_string(drafted) + " draft tokens accepted, " +
                        std::to_string((int) tps) + " tok/s, spec after run: " +
                        common_json::parse(eng.model_info_json()).at("spec_note").get<std::string>());
        if (require_accept) {
            check(accepted > 0, (std::string(label) + ": draft tokens accepted").c_str());
        }
        // batched verification can change the last bits of the logits, so a different greedy path is possible
        const bool same = content_of(r) == baseline;
        if (same) {
            check(true, (std::string(label) + ": output identical to plain decoding").c_str());
        } else {
            note((std::string(label) + ": output differs from plain decoding").c_str(), content_of(r).substr(0, 200));
        }
        // multi-turn: the speculative state must follow prompt reuse
        auto r2 = run(eng, q1, "", gp);
        auto r3 = run(eng, q2, "", gp);
        check(content_of(r2).find("12") != std::string::npos && !r3.contains("error"),
              (std::string(label) + ": follow-up turns").c_str(), content_of(r2) + " | " + content_of(r3));
    };
    {
        const auto mi = spec_run("ngram", "");
        if (mi.at("spec").get<std::string>().empty()) {
            note("ngram", "not available for this architecture");
        } else {
            check_spec("ngram", true);
        }
    }
    {
        const auto mi = spec_run("draft", argv[1]);
        if (mi.at("spec").get<std::string>().empty()) {
            note("draft", "not available for this architecture");
        } else {
            check_spec("draft (self)", true);
        }
    }
    {
        const auto mi = spec_run("mtp", "");
        if (!mi.at("has_mtp").get<bool>()) {
            note("mtp", "model has no MTP layers");
        } else {
            check(!mi.at("spec").get<std::string>().empty(), "MTP speculative decoding is active",
                  mi.at("spec_note").get<std::string>());
            check_spec("mtp", true);
        }
    }

    // 9. devices
    note("devices", Engine::devices_json());

    eng.unload();
    printf("\n%s: %d check(s) failed\n", g_failed ? "FAILED" : "OK", g_failed);
    return g_failed ? 1 : 0;
}
