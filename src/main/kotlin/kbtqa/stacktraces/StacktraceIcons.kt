package kbtqa.stacktraces

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icons used by the Stacktrace Copy Tool plugin.
 */
object StacktraceIcons {
    /**
     * Icon for the Copy Stacktrace action.
     */
    @JvmField
    val COPY_STACKTRACE: Icon = IconLoader.getIcon("/icons/copy_stacktrace.svg", StacktraceIcons::class.java)
}