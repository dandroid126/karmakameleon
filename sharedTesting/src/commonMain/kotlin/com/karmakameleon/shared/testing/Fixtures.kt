package com.karmakameleon.shared.testing

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/**
 * The text of a file in `sharedTesting/fixtures`, such as a saved Reddit response. Tests run in their module's
 * directory, and every module is a sibling of this one, so the path is the same from each.
 */
fun readFixture(name: String): String = SystemFileSystem.source(Path("../sharedTesting/fixtures", name)).buffered().use { it.readString() }
