package tr.mcoaihl.elakcaller

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class ElakCallerApp : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity) return
        refreshVisibleVersion(activity)
        UpdateManager.resumePendingInstall(activity)
        UpdateManager.checkForUpdates(activity, silent = true)
    }

    private fun refreshVisibleVersion(activity: Activity) {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        val versionName = info.versionName ?: return
        val root = activity.window?.decorView ?: return
        walk(root) { view ->
            if (view is TextView) {
                val current = view.text?.toString().orEmpty()
                if (current.matches(Regex("v\\d+\\.\\d+\\.\\d+.*Android 10\\+"))) {
                    view.text = "v$versionName · Android 10+"
                }
            }
        }
    }

    private fun walk(view: View, block: (View) -> Unit) {
        block(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i), block)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
