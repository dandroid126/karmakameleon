package com.karmakameleon.konsist

// The patterns the architecture tests look for, kept apart so RulesTest can check them against sample code.
object Rules {
    // A color written into the code: a Color(...) call whose first argument is a number, like Color(0xFF6200EE) or
    // Color(red = 0.5f, ...), or a named color like Color.Black or android.graphics.Color.parseColor. Color(argb)
    // with a value computed at runtime, like a subreddit's flair color, isn't hardcoded. Color.Transparent and
    // Color.Unspecified mean "no color" and "inherit", so they're allowed.
    val hardcodedColor =
        Regex("""\bColor\(\s*(\w+\s*=\s*)?(0x|\d)|\bColor\.(?!Transparent\b|Unspecified\b)[A-Za-z]\w*""")

    // The packages shared/commonMain may import from: Kotlin's own, the app's shared code and multiplatform
    // libraries. Add a library here when it's added to commonMain's dependencies, after checking it supports
    // every platform, not just Android.
    val commonMainImportPrefixes =
        listOf(
            "kotlin.",
            "kotlinx.",
            "com.karmakameleon.shared.",
            "io.ktor.",
            "com.russhwolf.settings.",
            "io.github.aakira.napier.",
            "org.koin.",
            "androidx.lifecycle.",
        )

    // Platform packages that commonMain can't use, imported or written out in full. The Android target is the only
    // one, so they compile today, but they'd break as soon as another platform is added. They belong in androidMain,
    // behind an expect declaration or an interface. Ktor's OkHttp engine is Android-only too.
    val platformReference = Regex("""(?<![\w.])(android|java|javax|dalvik|okhttp3|okio)\.[A-Za-z]|\bio\.ktor\.client\.engine\.okhttp\b""")

    // Using the API client or making HTTP calls from UI code. Screens and view models go through a repository.
    val apiClientUse = Regex("""\bRedditApi\b|\bio\.ktor\.""")

    // runCatching catches the CancellationException that stops a cancelled coroutine. runSuspendCatching doesn't.
    val runCatchingCall = Regex("""\brunCatching\b""")

    // Creating a WebView, like WebView(context). Composables whose names end in WebView, like RedditLoginWebView(...),
    // don't count.
    val webViewConstruction = Regex("""\bWebView\(""")
}
