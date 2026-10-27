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
    check(n_updates > 0, "streaming updates were sent", std::to_string(n_updates));

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

    eng.unload();
    printf("\n%s: %d check(s) failed\n", g_failed ? "FAILED" : "OK", g_failed);
    return g_failed ? 1 : 0;
}
