package com.karmakameleon.shared

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/** The text of a file in `src/commonTest/fixtures`, such as a saved Reddit response. Tests run in the module's directory. */
fun readFixture(name: String): String = SystemFileSystem.source(Path("src/commonTest/fixtures", name)).buffered().use { it.readString() }
