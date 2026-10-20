package com.karmakameleon.konsist

import com.lemonappdev.konsist.api.Konsist

// The source sets that go into the app. Tests are left out: they may break some rules on purpose, and the shared
// module's tests can't see the app's theme.
private const val APP_MAIN = "androidApp/src/main"
private const val COMMON_MAIN = "shared/src/commonMain"
private const val SHARED_ANDROID_MAIN = "shared/src/androidMain"

val productionScope = Konsist.scopeFromDirectory(APP_MAIN, COMMON_MAIN, SHARED_ANDROID_MAIN)

val commonMainScope = Konsist.scopeFromDirectory(COMMON_MAIN)
