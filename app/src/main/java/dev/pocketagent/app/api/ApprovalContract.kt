package dev.pocketagent.app.api

import org.json.JSONObject

/**
 * The contract of `POST /v1/runs/{run_id}/approval` as the Hermes that ships in the image enforces it. Pure, so it is unit-tested.
 *
 * Read in the Hermes source (gateway/platforms/api_server_authority_runs.py, `respond_run`, and api_server_runs.py,
 * `_handle_run_approval`): when the gateway runs in session-authority mode (it does for every API turn of this image) the body
 * must contain EXACTLY the three keys `request_id`, `execution_generation` and `choice`; any other key set, or a missing one, is
 * HTTP 400 `invalid_params`. `execution_generation` must be a JSON integer equal to the generation of the run (otherwise HTTP 409
 * `stale_generation`); `request_id` must name a prompt that is still pending (otherwise 409 `approval_not_pending`); `choice`
 * must be one of the `choices` of that prompt: `once`, `session`, `always` or `deny`.
 *
 * Where the client gets the numbers: the `approval.request` stream event carries `request_id` and `execution_generation`
 * (session_api_turn.py `run_api_turn` hands the whole prompt to the stream's notifier, api_server.py `_approval_request_event`
 * keeps every field), and `GET /v1/runs/{run_id}` returns the same generation as `execution_generation` plus the open prompts
 * in `pending_controls` (each with `prompt_id`, `kind`, `choices`). The second source is the fallback here.
 */
object ApprovalContract {
    /** The four spellings Hermes accepts as an answer. */
    val CHOICES: Set<String> = setOf("once", "session", "always", "deny")

    /** The exact body of an answer; the generation goes out as an integer. */
    fun body(requestId: String, generation: Long, choice: String): String =
        JSONObject().put("request_id", requestId).put("execution_generation", generation).put("choice", choice).toString()

    /** What `GET /v1/runs/{id}` says about the run: its generation and the ids of the approval prompts that are open. */
    data class RunState(val generation: Long?, val approvalIds: List<String>)

    fun parseRun(body: String?): RunState {
        val o = try { JSONObject(body.orEmpty()) } catch (_: Exception) { return RunState(null, emptyList()) }
        val gen = if (o.has("execution_generation") && !o.isNull("execution_generation")) o.optLong("execution_generation", -1L).takeIf { it >= 0L } else null
        val ids = ArrayList<String>()
        o.optJSONArray("pending_controls")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.optJSONObject(i) ?: continue
                if (p.optString("kind", "") != "approval") continue
                val id = if (p.isNull("prompt_id")) "" else p.optString("prompt_id", "")
                if (id.isNotEmpty()) ids += id
            }
        }
        return RunState(gen, ids)
    }

    /** True when the answer says the request is gone for good (settled, replaced, or the run ended): retrying cannot help. */
    fun isGone(r: HttpResult): Boolean {
        if (r.code != 404 && r.code != 409) return false
        val code = ApiErrorText.parse(r.body).code ?: ApiErrorText.parse(r.body).message.orEmpty()
        return code.contains("approval_not_pending") || code.contains("stale_generation") || code.contains("not_found") ||
            code.contains("approval_not_active") || r.code == 404
    }

    /** True when the answer says only that the generation was out of date (a fresh one may work). */
    fun isStale(r: HttpResult): Boolean =
        r.code == 409 && (ApiErrorText.parse(r.body).let { (it.code ?: it.message).orEmpty() }).contains("stale_generation")
}
