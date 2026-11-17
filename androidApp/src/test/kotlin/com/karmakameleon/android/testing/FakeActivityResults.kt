package com.karmakameleon.android.testing

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

/**
 * Stands in for the activities a screen starts for a result, like Android's file pickers, which Robolectric can't show.
 * Each launch is recorded in [launched] and answered with [nextResult] at once. Provide it to a screen through
 * `LocalActivityResultRegistryOwner`.
 */
class FakeActivityResults : ActivityResultRegistryOwner {
    var nextResult: Any? = null
    val launched = mutableListOf<Any?>()

    override val activityResultRegistry =
        object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                launched += input
                dispatchResult(requestCode, nextResult)
            }
        }
}
