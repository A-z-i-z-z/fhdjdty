package dev.pocketagent.app.service

/**
 * The notification groups of the app and the (pure) rules that keep their summaries in step with their children.
 *
 * Why explicit groups: without them Android bundles the app's notifications into one automatic group, and the reply of a chat
 * ends up behind the collapsed "runtime is running" row. So the ongoing runtime notification has a group of its own, and every
 * other notification belongs to the group of its channel. Those groups have a summary posted by the app itself: grouped children
 * WITHOUT a summary get force-grouped by recent Android versions. A summary is shown only while at least one child exists.
 */
internal object NotificationGroups {
    /** The foreground service's ongoing notification (no summary: it is the only member of its group). */
    const val GROUP_RUNTIME = "agiri.runtime"
    const val GROUP_REPLIES = "agiri.replies"
    const val GROUP_APPROVALS = "agiri.approvals"
    const val GROUP_ROUTINES = "agiri.routines"

    /** The groups that have a summary notification. */
    val SUMMARY_GROUPS: List<String> = listOf(GROUP_REPLIES, GROUP_APPROVALS, GROUP_ROUTINES)

    /** The tag shared by all summary notifications (the ids tell the groups apart). */
    const val SUMMARY_TAG = "agiri.summary"

    private const val ID_SUMMARY_REPLIES = 111
    private const val ID_SUMMARY_APPROVALS = 112
    private const val ID_SUMMARY_ROUTINES = 113

    /** The notification id of the summary of [group]; null for a group without a summary. */
    fun summaryId(group: String): Int? = when (group) {
        GROUP_REPLIES -> ID_SUMMARY_REPLIES
        GROUP_APPROVALS -> ID_SUMMARY_APPROVALS
        GROUP_ROUTINES -> ID_SUMMARY_ROUTINES
        else -> null
    }

    /** One entry of `NotificationManager.getActiveNotifications()`, reduced to what the rules need. */
    data class Active(val tag: String?, val id: Int, val group: String?, val isSummary: Boolean)

    /** What to do so that exactly the groups with children have a summary. */
    data class Plan(val post: Set<String>, val cancel: Set<String>)

    /**
     * Groups of [SUMMARY_GROUPS] that have at least one child (a notification that is not a summary) among [active], not counting
     * the notifications in [ignoring] (tag to id): those were cancelled a moment ago, and Android removes them asynchronously,
     * so the list may still hold them. [inFlight] are children posted a moment ago that `activeNotifications` may not list yet:
     * they count as live (cancelling a summary makes Android cancel all children of its group, queued ones included).
     */
    fun groupsWithChildren(
        active: Collection<Active>, ignoring: Set<Pair<String?, Int>> = emptySet(), inFlight: Collection<Active> = emptyList(),
    ): Set<String> =
        (active.asSequence() + inFlight.asSequence())
            .filter { !it.isSummary && it.group in SUMMARY_GROUPS && (it.tag to it.id) !in ignoring }
            .mapNotNull { it.group }
            .toSet()

    /**
     * The summaries to post (the group has children and no summary yet) and to cancel (the group has no children left but its
     * summary is still there). A group that is fine is left alone, so nothing is re-posted needlessly.
     */
    fun plan(
        active: Collection<Active>, ignoring: Set<Pair<String?, Int>> = emptySet(), inFlight: Collection<Active> = emptyList(),
    ): Plan {
        val withChildren = groupsWithChildren(active, ignoring, inFlight)
        val summaryIds = active.asSequence().filter { it.isSummary && it.tag == SUMMARY_TAG }.map { it.id }.toSet()
        val post = LinkedHashSet<String>()
        val cancel = LinkedHashSet<String>()
        for (g in SUMMARY_GROUPS) {
            val has = summaryIds.contains(summaryId(g))
            if (g in withChildren && !has) post += g
            if (g !in withChildren && has) cancel += g
        }
        return Plan(post, cancel)
    }

    /** The group of a notification posted on [channel]: replies, approvals and routines each have their own. */
    fun groupForChannel(channel: String): String = when (channel) {
        ReplyNotifier.CHANNEL_APPROVALS -> GROUP_APPROVALS
        ReplyNotifier.CHANNEL_ROUTINES -> GROUP_ROUTINES
        else -> GROUP_REPLIES
    }
}
