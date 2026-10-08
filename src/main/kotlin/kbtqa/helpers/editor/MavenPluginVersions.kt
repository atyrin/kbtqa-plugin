package kbtqa.helpers.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import kbtqa.helpers.versions.MavenVersionsService

/**
 * Picks the version for an Apache Maven plugin an action is about to declare: its newest stable
 * release that still runs on Maven 3, looked up on Maven Central.
 */
internal object MavenPluginVersions {

    /** Plain releases of the 3.x line; a 4.x line of an Apache plugin needs Maven 4. */
    private val STABLE_MAVEN3_VERSION = Regex("""3(\.\d+)+""")

    /**
     * The newest stable 3.x version in [versions], ignoring alphas, betas, RCs and snapshots, or
     * `null` when there is none.
     */
    fun latestStableMaven3(versions: List<String>): String? =
        versions.map { it.trim() }
            .filter { STABLE_MAVEN3_VERSION.matches(it) }
            .maxWithOrNull(::compareNumericVersions)

    /**
     * Looks up the latest stable Maven 3 release of the Apache Maven plugin [artifactId] off the EDT,
     * then calls [then] on the EDT with it — or with [fallback] when the lookup fails or is cancelled,
     * so that being offline still lets the action finish.
     */
    fun lookUpLatest(project: Project, artifactId: String, fallback: String, then: (String) -> Unit) {
        object : Task.Backgroundable(project, "Looking up the latest $artifactId", true) {
            private var latest: String? = null

            override fun run(indicator: ProgressIndicator) {
                latest = runBlockingCancellable {
                    latestStableMaven3(service<MavenVersionsService>().getMavenPluginVersions(artifactId))
                }
            }

            // Called on the EDT however the lookup ended
            override fun onFinished() {
                then(latest ?: fallback)
            }
        }.queue()
    }

    /** Compares dot-separated numeric versions segment by segment, so that 3.10 sorts above 3.9. */
    private fun compareNumericVersions(first: String, second: String): Int {
        val a = first.split('.').map { it.toLongOrNull() ?: 0L }
        val b = second.split('.').map { it.toLongOrNull() ?: 0L }
        for (i in 0 until maxOf(a.size, b.size)) {
            val comparison = a.getOrElse(i) { 0L }.compareTo(b.getOrElse(i) { 0L })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
