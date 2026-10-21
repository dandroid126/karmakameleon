buildscript {
    dependencies {
        constraints {
            // AGP brings in older versions of some libraries with known vulnerabilities, so require fixed ones.
            libs.bundles.security.fixes
                .get()
                .forEach { classpath("${it.module}:${it.version}") }
        }
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
    // On the root classpath, so the security-fixes constraints above also apply to the libraries it brings in.
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dependencyAnalysis)
}

// Formats Kotlin with ktlint, using the rules in .editorconfig. Run spotlessApply to fix what it reports.
spotless {
    val buildOutput = listOf("**/build/**", ".gradle/**", ".kotlin/**")
    kotlin {
        target("**/*.kt")
        targetExclude(buildOutput)
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(buildOutput)
        ktlint(libs.versions.ktlint.get())
    }
}

subprojects {
    plugins.withId("dev.detekt") {
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            baseline = file("detekt-baseline.xml")
        }
    }

    val constraintHandler = dependencies.constraints
    // AGP runs lint with its own copy of the tooling, which brings in the same old libraries.
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        libs.bundles.security.fixes.get().forEach {
            dependencyConstraints.add(constraintHandler.create("${it.module}:${it.version}"))
        }
    }
}

// Finds unused dependencies, dependencies on the wrong configuration (api vs implementation) and code that uses a
// library it only gets through another one.
dependencyAnalysis {
    issues {
        all {
            onAny { severity("fail") }
            onUsedTransitiveDependencies {
                // kotlin("test") picks the right test framework artifact by itself.
                exclude("org.jetbrains.kotlin:kotlin-test-junit")
            }
        }
    }
    structure {
        // Libraries released as a family of artifacts, where declaring one artifact and using classes from another
        // in the same family is fine. Compose, for example, splits each library into several artifacts, like ui and
        // ui-text, and the BOM keeps their versions together.
        listOf(
            "androidx.activity",
            "androidx.compose.animation",
            "androidx.compose.foundation",
            "androidx.compose.material",
            "androidx.compose.runtime",
            "androidx.compose.ui",
            "androidx.core",
            "androidx.lifecycle",
            "androidx.media3",
            "androidx.navigation",
            "io.coil-kt.coil3",
            "io.github.aakira",
            "io.github.takahirom.roborazzi",
            "io.insert-koin",
            "org.robolectric",
        ).forEach { group ->
            bundle(group) { includeGroup(group) }
        }
        bundle("kotlinx-serialization") { include("org\\.jetbrains\\.kotlinx:kotlinx-serialization-.*") }
        // Multiplatform Settings and its test library, with their Android artifacts. Not the no-arg artifact, which
        // shared keeps out of its API.
        bundle("multiplatform-settings") { include("com\\.russhwolf:multiplatform-settings(-test)?(-android.*)?") }
    }
}

// Every static check in one task, so they can all be run locally before committing. CI runs it too.
tasks.register("qualityGate") {
    group = "verification"
    description = "Runs all static checks."
    dependsOn(
        ":androidApp:lintDebug",
        "spotlessCheck",
        "cpdCheck",
        "earlyReturnCheck",
        "actionlint",
        "zizmor",
        "gradleActionsCacheCheck",
        "buildHealth",
        // The architecture tests, which check the code against the rules in CLAUDE.md.
        ":konsistTest:test",
        // Detekt with type resolution. The app's debug and release variants have the same code, so one is enough.
        ":androidApp:detektDebug",
        ":androidApp:detektDebugUnitTest",
        ":shared:detektMainAndroid",
        ":shared:detektHostTestAndroid",
        ":konsistTest:detektTest",
    )
}

// Fails on unit tests that return when their data is missing, like `posts.firstOrNull() ?: return@runTest`. Such a
// test passes without checking anything, so use first() and let a missing fixture fail it. The integration tests in
// androidHostTest return this way on purpose, to skip when no client ID is set, so only commonTest is checked.
abstract class EarlyReturnCheckTask : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val projectPath: Property<String>

    @TaskAction
    fun run() {
        val pattern = Regex("""\?:\s*return@runTest""")
        val root = projectPath.get().trimEnd('/') + "/"
        val found =
            sources.files.sorted().flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.path.removePrefix(root)}:${index + 1}".takeIf { pattern.containsMatchIn(line) }
                }
            }
        if (found.isNotEmpty()) {
            throw GradleException(
                "These tests return early instead of failing. Give each test the data it needs and use first() " +
                    "instead:\n" + found.joinToString("\n") { "  $it" },
            )
        }
    }
}

tasks.register<EarlyReturnCheckTask>("earlyReturnCheck") {
    description = "Fails on unit tests that return early instead of failing."
    group = "verification"
    sources.from(fileTree("shared/src/commonTest") { include("**/*.kt") })
    projectPath.set(layout.projectDirectory.asFile.path)
}

// PMD's copy-paste detector, which finds duplicated Kotlin code.
val cpd =
    configurations.create("cpd") {
        // The GUI rule designer isn't needed to find duplicates.
        exclude(group = "net.sourceforge.pmd", module = "pmd-designer")
    }

dependencies {
    cpd(libs.pmd.cli)
    cpd(libs.pmd.kotlin)
}

val cpdSourceDirs = listOf("shared/src/commonMain", "shared/src/androidMain", "androidApp/src/main/kotlin")

val cpdReport =
    tasks.register<JavaExec>("cpdReport") {
        description = "Finds duplicated code with PMD's copy-paste detector."
        classpath = cpd
        mainClass = "net.sourceforge.pmd.cli.PmdCli"
        val minimumTokens = providers.gradleProperty("cpd.minimumTokens").orElse("50")
        val report = layout.buildDirectory.file("reports/cpd/cpd.xml")
        val sourceDirs = cpdSourceDirs
        inputs.files(sourceDirs.map { fileTree(it) })
        inputs.property("minimumTokens", minimumTokens)
        outputs.file(report)
        argumentProviders.add(
            CommandLineArgumentProvider {
                listOf(
                    "cpd",
                    "--language",
                    "kotlin",
                    "--minimum-tokens",
                    minimumTokens.get(),
                    "--format",
                    "xml",
                    "--no-fail-on-violation",
                ) +
                    sourceDirs.flatMap { listOf("--dir", it) } +
                    listOf("--report-file", report.get().asFile.path)
            },
        )
    }

// Compares the copy-paste detector's findings with config/cpd/cpd-baseline.txt, the duplicates that existed when
// the check was added. A duplicate is identified by its files and a hash of its code, so editing a known duplicate
// makes it new again: fix it then instead of adding it back to the baseline.
abstract class CpdBaselineTask : DefaultTask() {
    @get:InputFile
    abstract val report: RegularFileProperty

    @get:Internal
    abstract val baseline: RegularFileProperty

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val updateBaseline: Property<Boolean>

    @TaskAction
    fun run() {
        val duplicates = readDuplicates()
        val baselineFile = baseline.get().asFile
        if (updateBaseline.get()) {
            baselineFile.parentFile.mkdirs()
            baselineFile.writeText(
                "# Duplicated code that existed before the duplication check. Fix entries; don't add new ones.\n" +
                    duplicates.toSortedMap().entries.joinToString("") { (id, description) -> "$id $description\n" },
            )
            logger.lifecycle("Wrote ${duplicates.size} duplicates to $baselineFile")
            return
        }
        val known =
            baselineFile
                .takeIf { it.exists() }
                ?.readLines()
                .orEmpty()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.substringBefore(" ") }
                .toSet()
        val new = duplicates.filterKeys { it !in known }
        val fixed = known - duplicates.keys
        if (fixed.isNotEmpty()) {
            logger.lifecycle("${fixed.size} duplicates in the baseline are gone; remove them from $baselineFile: ${fixed.joinToString()}")
        }
        if (new.isNotEmpty()) {
            throw GradleException(
                "Found ${new.size} duplicated code blocks that aren't in the baseline. Extract the shared code instead of copying it:\n" +
                    new.values.joinToString("\n") { "  $it" } + "\nThe full report is ${report.get().asFile}",
            )
        }
    }

    // Maps each duplicate's ID to a description of where it is.
    private fun readDuplicates(): Map<String, String> {
        val document =
            javax.xml.parsers.DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(report.get().asFile)
        val nodes = document.getElementsByTagName("duplication")
        val root = projectPath.get().trimEnd('/') + "/"
        return (0 until nodes.length).associate { index ->
            val duplication = nodes.item(index) as org.w3c.dom.Element
            val fileNodes = duplication.getElementsByTagName("file")
            val files = (0 until fileNodes.length).map { fileNodes.item(it) as org.w3c.dom.Element }
            val locations =
                files.map { "${it.getAttribute("path").removePrefix(root)}:${it.getAttribute("line")}-${it.getAttribute("endline")}" }
            val code =
                duplication
                    .getElementsByTagName("codefragment")
                    .item(0)
                    .textContent
                    .replace(Regex("\\s+"), " ")
                    .trim()
            val key = files.map { it.getAttribute("path").removePrefix(root) }.sorted().joinToString("|") + "|" + code
            val hash =
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(key.toByteArray())
            val id = hash.joinToString("") { "%02x".format(it) }.take(16)
            id to "${duplication.getAttribute("lines")} lines in ${locations.joinToString(", ")}"
        }
    }
}

listOf("cpdCheck" to false, "cpdBaseline" to true).forEach { (name, update) ->
    tasks.register<CpdBaselineTask>(name) {
        description =
            if (update) {
                "Writes the current duplicates to config/cpd/cpd-baseline.txt."
            } else {
                "Fails on duplicated code that isn't in the baseline."
            }
        group = "verification"
        report.set(layout.buildDirectory.file("reports/cpd/cpd.xml"))
        dependsOn(cpdReport)
        baseline.set(layout.projectDirectory.file("config/cpd/cpd-baseline.txt"))
        projectPath.set(layout.projectDirectory.asFile.path)
        updateBaseline.set(update)
    }
}

// Command-line tools for checking the workflow files, downloaded from their GitHub releases. Each archive is checked
// against a pinned SHA-256, so nothing has to be installed and every machine runs the same version. Dependabot can't
// update them: to update one, change its version and take each archive's hash from the `digest` of its assets in
// `gh api repos/<owner>/<repo>/releases/tags/<tag>`.
class ReleaseTool(
    val name: String,
    val downloadUrl: String,
    // The archive and its SHA-256 for each platform, keyed like "linux-x86_64".
    val archives: Map<String, Pair<String, String>>,
    // Where the executable is in the archive.
    val executable: String,
)

val releaseTools =
    listOf(
        ReleaseTool(
            name = "actionlint",
            downloadUrl = "https://github.com/rhysd/actionlint/releases/download/v1.7.12",
            archives =
                mapOf(
                    "linux-x86_64" to
                        ("actionlint_1.7.12_linux_amd64.tar.gz" to "8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8"),
                    "linux-aarch64" to
                        ("actionlint_1.7.12_linux_arm64.tar.gz" to "325e971b6ba9bfa504672e29be93c24981eeb1c07576d730e9f7c8805afff0c6"),
                    "macos-x86_64" to
                        ("actionlint_1.7.12_darwin_amd64.tar.gz" to "5b44c3bc2255115c9b69e30efc0fecdf498fdb63c5d58e17084fd5f16324c644"),
                    "macos-aarch64" to
                        ("actionlint_1.7.12_darwin_arm64.tar.gz" to "aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f"),
                ),
            executable = "actionlint",
        ),
        // actionlint runs it on the workflows' shell scripts. It's downloaded too, so the results don't depend on
        // whether it happens to be installed.
        ReleaseTool(
            name = "shellcheck",
            downloadUrl = "https://github.com/koalaman/shellcheck/releases/download/v0.11.0",
            archives =
                mapOf(
                    "linux-x86_64" to
                        ("shellcheck-v0.11.0.linux.x86_64.tar.gz" to "b7af85e41cc99489dcc21d66c6d5f3685138f06d34651e6d34b42ec6d54fe6f6"),
                    "linux-aarch64" to
                        ("shellcheck-v0.11.0.linux.aarch64.tar.gz" to "68a8133197a50beb8803f8d42f9908d1af1c5540d4bb05fdfca8c1fa47decefc"),
                    "macos-x86_64" to
                        ("shellcheck-v0.11.0.darwin.x86_64.tar.gz" to "c2c15e08df0e8fbc374c335b230a7ee958c313fa5714817a59aa59f1aa594f51"),
                    "macos-aarch64" to
                        ("shellcheck-v0.11.0.darwin.aarch64.tar.gz" to "339b930feb1ea764467013cc1f72d09cd6b869ebf1013296ba9055ab2ffbd26f"),
                ),
            executable = "shellcheck-v0.11.0/shellcheck",
        ),
        ReleaseTool(
            name = "zizmor",
            downloadUrl = "https://github.com/zizmorcore/zizmor/releases/download/v1.30.1",
            archives =
                mapOf(
                    "linux-x86_64" to
                        ("zizmor-x86_64-unknown-linux-gnu.tar.gz" to "e65324f4430c2717591937edcec90ccbefaf14c174f8ec9415e03ca875b46e1a"),
                    "linux-aarch64" to
                        ("zizmor-aarch64-unknown-linux-gnu.tar.gz" to "7ff1dce33bdd18fd2a4affe63bdd47efcccca97b2cec1c1863ec26e9e2647540"),
                    "macos-x86_64" to
                        ("zizmor-x86_64-apple-darwin.tar.gz" to "10e6b18b11ea07e515a16f0f0518c7b07527bc9977c1fd5698181ce7f3554202"),
                    "macos-aarch64" to
                        ("zizmor-aarch64-apple-darwin.tar.gz" to "e28d22b087f9ebb8d99da6e740d348c930f559961c7c3f12badda54f882195a2"),
                ),
            executable = "zizmor",
        ),
    )

val toolPlatform: String =
    run {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val osName =
            when {
                os.startsWith("linux") -> "linux"
                os.startsWith("mac") -> "macos"
                else -> os
            }
        val archName =
            when (arch) {
                "amd64", "x86_64" -> "x86_64"
                "aarch64", "arm64" -> "aarch64"
                else -> arch
            }
        "$osName-$archName"
    }

// Downloads a release archive and fails unless its SHA-256 matches the pinned one.
abstract class DownloadToolTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val url: Property<String>

    @get:Input
    @get:Optional
    abstract val sha256: Property<String>

    @get:Input
    abstract val platform: Property<String>

    @get:OutputFile
    abstract val archive: RegularFileProperty

    @TaskAction
    fun run() {
        val source = url.orNull ?: throw GradleException("$name: there's no download for ${platform.get()}.")
        val client =
            java.net.http.HttpClient
                .newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build()
        val request =
            java.net.http.HttpRequest
                .newBuilder(java.net.URI(source))
                .build()
        val response =
            client.send(
                request,
                java.net.http.HttpResponse.BodyHandlers
                    .ofByteArray(),
            )
        if (response.statusCode() != 200) {
            throw GradleException("Downloading $source failed with HTTP ${response.statusCode()}.")
        }
        val hash =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(response.body())
                .joinToString("") { "%02x".format(it) }
        if (hash != sha256.get()) {
            throw GradleException("$source has SHA-256 $hash, but ${sha256.get()} is pinned. Don't use it.")
        }
        archive.get().asFile.writeBytes(response.body())
    }
}

// Maps each tool's name to the path of its executable, once extracted.
val toolExecutables =
    releaseTools.associate { tool ->
        val archive = tool.archives[toolPlatform]
        val archiveFile = layout.buildDirectory.file("tools/${archive?.first ?: tool.name}")
        val download =
            tasks.register<DownloadToolTask>("download${tool.name.replaceFirstChar { it.uppercase() }}") {
                description = "Downloads ${tool.name}."
                url.set(archive?.let { "${tool.downloadUrl}/${it.first}" })
                sha256.set(archive?.second)
                platform.set(toolPlatform)
                this.archive.set(archiveFile)
            }
        val directory = layout.buildDirectory.dir("tools/${tool.name}")
        tasks.register<Sync>("extract${tool.name.replaceFirstChar { it.uppercase() }}") {
            description = "Extracts ${tool.name}."
            dependsOn(download)
            from(tarTree(archiveFile))
            into(directory)
        }
        tool.name to
            directory
                .get()
                .file(tool.executable)
                .asFile.path
    }

// Finds mistakes in the workflow files, like invalid expressions, unknown action inputs and, through shellcheck,
// bugs in the shell scripts.
tasks.register<Exec>("actionlint") {
    description = "Checks the GitHub Actions workflows for mistakes."
    group = "verification"
    dependsOn("extractActionlint", "extractShellcheck")
    executable = toolExecutables.getValue("actionlint")
    // No workflow runs Python, and pyflakes would only run if it happened to be installed.
    args("-shellcheck=${toolExecutables.getValue("shellcheck")}", "-pyflakes=")
}

// Finds security problems in the workflows and dependabot.yml, like unpinned actions, template injection and
// leaked credentials. With a token in GH_TOKEN, which CI sets, it also checks that each pinned commit belongs to
// the action's own repo and that no action has a known vulnerability.
tasks.register<Exec>("zizmor") {
    description = "Checks the GitHub Actions workflows for security problems."
    group = "verification"
    dependsOn("extractZizmor")
    executable = toolExecutables.getValue("zizmor")
    // Without --strict-collection, a workflow it can't parse is skipped with only a warning.
    args("--quiet", "--strict-collection", ".")
}

// Fails on a gradle/actions step that doesn't set `cache-provider: basic`. Without it, the action uses its default
// cache, a proprietary Gradle component covered by Gradle's Terms of Use.
abstract class GradleActionsCacheCheckTask : DefaultTask() {
    @get:InputFiles
    abstract val workflows: ConfigurableFileCollection

    @get:Input
    abstract val projectPath: Property<String>

    @TaskAction
    fun run() {
        val root = projectPath.get().trimEnd('/') + "/"
        val gradleAction = Regex("""^\s*(- )?uses:\s*gradle/actions/""")
        val basicCache = Regex("""^\s*cache-provider:\s*basic\s*(#.*)?$""")
        val found =
            workflows.files.sorted().flatMap { file ->
                val lines = file.readLines()
                lines.indices
                    .filter { gradleAction.containsMatchIn(lines[it]) }
                    .filterNot { index -> stepLines(lines, index).any { basicCache.matches(it) } }
                    .map { "${file.path.removePrefix(root)}:${it + 1}" }
            }
        if (found.isNotEmpty()) {
            throw GradleException(
                "These gradle/actions steps don't set `cache-provider: basic`, so they'd use the proprietary cache:\n" +
                    found.joinToString("\n") { "  $it" },
            )
        }
    }

    // The lines of the step that the line at `index` is in: from the line starting with "- " to the next line
    // that's indented no further than that dash.
    private fun stepLines(
        lines: List<String>,
        index: Int,
    ): List<String> {
        val start = (index downTo 0).first { lines[it].trimStart().startsWith("- ") }
        val indent = lines[start].indexOfFirst { !it.isWhitespace() }
        val end =
            (start + 1 until lines.size)
                .firstOrNull { lines[it].isNotBlank() && lines[it].indexOfFirst { c -> !c.isWhitespace() } <= indent }
                ?: lines.size
        return lines.subList(start, end)
    }
}

tasks.register<GradleActionsCacheCheckTask>("gradleActionsCacheCheck") {
    description = "Fails on a gradle/actions step that doesn't use the open-source cache."
    group = "verification"
    workflows.from(fileTree(".github/workflows") { include("*.yml", "*.yaml") })
    projectPath.set(layout.projectDirectory.asFile.path)
}

// Fails when too few of the lines changed since the branch left main are covered by tests. Only the shared module is
// measured: the app has no tests yet, so every change to it would fail. A changed line counts if the coverage report
// has it, which leaves out blank lines, comments and declarations with no code. Uncommitted and untracked files count
// too, so it can be run before committing. It also reports how many branches on the changed lines are covered, without
// failing on them yet (#58).
abstract class DiffCoverageTask : DefaultTask() {
    @get:InputFile
    abstract val report: RegularFileProperty

    // The source directories to measure, relative to the project directory.
    @get:Input
    abstract val sourceDirs: ListProperty<String>

    // What the changes are compared with: the point where HEAD branched off it.
    @get:Input
    abstract val baseRef: Property<String>

    @get:Input
    abstract val minimumPercent: Property<Int>

    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    // A line in the coverage report: whether any of its code ran, and how many of its branches did and didn't.
    private data class LineCoverage(
        val covered: Boolean,
        val coveredBranches: Int,
        val missedBranches: Int,
    )

    private data class ChangedLine(
        val path: String,
        val line: Int,
        val coverage: LineCoverage,
    )

    @TaskAction
    fun run() {
        val coverage = readCoverage()
        val changed = changedLines()
        val measured =
            changed.flatMap { (path, lines) ->
                val fileCoverage = coverage[coverageKey(path)].orEmpty()
                lines.mapNotNull { line -> fileCoverage[line]?.let { ChangedLine(path, line, it) } }
            }
        if (measured.isEmpty()) {
            logger.lifecycle("No changed lines with code in ${sourceDirs.get().joinToString()}, so there's nothing to measure.")
            return
        }
        val covered = measured.count { it.coverage.covered }
        val percent = covered * 100.0 / measured.size
        val summary =
            "%d of %d changed lines are covered by tests (%.1f%%; at least %d%% is required).".format(
                covered,
                measured.size,
                percent,
                minimumPercent.get(),
            )
        val uncovered = lineList(measured.filterNot { it.coverage.covered })
        if (percent < minimumPercent.get()) {
            logger.lifecycle(branchReport(measured))
            throw GradleException("$summary Add tests for these lines:\n$uncovered")
        }
        logger.lifecycle(summary + if (uncovered.isEmpty()) "" else "\nNot covered:\n$uncovered")
        logger.lifecycle(branchReport(measured))
    }

    // How many branches on the changed lines are covered, and which lines have branches no test took.
    private fun branchReport(measured: List<ChangedLine>): String {
        val withBranches = measured.filter { it.coverage.coveredBranches + it.coverage.missedBranches > 0 }
        if (withBranches.isEmpty()) return "Branches (not required yet): the changed lines have no branches."
        val covered = withBranches.sumOf { it.coverage.coveredBranches }
        val total = withBranches.sumOf { it.coverage.coveredBranches + it.coverage.missedBranches }
        val summary =
            "Branches (not required yet): %d of %d branches on the changed lines are covered (%.1f%%).".format(
                covered,
                total,
                covered * 100.0 / total,
            )
        val partial = lineList(withBranches.filter { it.coverage.missedBranches > 0 })
        return summary + if (partial.isEmpty()) "" else "\nLines with branches no test took:\n$partial"
    }

    // The lines grouped by file, like "  path/File.kt: 3-5, 9".
    private fun lineList(lines: List<ChangedLine>): String =
        lines
            .groupBy({ it.path }, { it.line })
            .entries
            .joinToString("\n") { (path, numbers) -> "  $path: ${lineRanges(numbers)}" }

    // Maps each source file, as "package/path/File.kt", to the coverage of each of its lines with code.
    private fun readCoverage(): Map<String, Map<Int, LineCoverage>> {
        val document =
            javax.xml.parsers.DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(report.get().asFile)
        val packages = document.getElementsByTagName("package")
        return (0 until packages.length)
            .map { packages.item(it) as org.w3c.dom.Element }
            .flatMap { pkg ->
                val files = pkg.getElementsByTagName("sourcefile")
                (0 until files.length).map { index ->
                    val file = files.item(index) as org.w3c.dom.Element
                    val lines = file.getElementsByTagName("line")
                    "${pkg.getAttribute("name")}/${file.getAttribute("name")}" to
                        (0 until lines.length)
                            .map { lines.item(it) as org.w3c.dom.Element }
                            .associate {
                                it.getAttribute("nr").toInt() to
                                    LineCoverage(
                                        covered = it.getAttribute("ci").toInt() > 0,
                                        coveredBranches = it.getAttribute("cb").toInt(),
                                        missedBranches = it.getAttribute("mb").toInt(),
                                    )
                            }
                }
            }.toMap()
    }

    // shared/src/commonMain/kotlin/com/example/File.kt -> com/example/File.kt, the way the report names it.
    private fun coverageKey(path: String): String = path.substringAfter("/kotlin/")

    // Maps each changed .kt file to its added or changed line numbers.
    private fun changedLines(): Map<String, List<Int>> {
        val mergeBase = git("merge-base", "HEAD", baseRef.get()).trim()
        val diff = git("diff", "--unified=0", "--no-color", "--no-ext-diff", mergeBase, "--", *sourceDirs.get().toTypedArray())
        val changed = mutableMapOf<String, MutableList<Int>>()
        var file: String? = null
        val hunk = Regex("""^@@ -\S+ \+(\d+)(?:,(\d+))? @@""")
        diff.lineSequence().forEach { line ->
            when {
                line.startsWith("+++ ") -> {
                    file = line.removePrefix("+++ ").removePrefix("b/").takeIf { it.endsWith(".kt") }
                }

                line.startsWith("@@") && file != null -> {
                    val match = hunk.find(line) ?: return@forEach
                    val start = match.groupValues[1].toInt()
                    val count = match.groupValues[2].ifEmpty { "1" }.toInt()
                    changed.getOrPut(file!!) { mutableListOf() }.addAll(start until start + count)
                }
            }
        }
        git("ls-files", "--others", "--exclude-standard", "--", *sourceDirs.get().toTypedArray())
            .lines()
            .filter { it.endsWith(".kt") }
            .forEach { path ->
                changed[path] =
                    (
                        1..projectDir
                            .file(path)
                            .get()
                            .asFile
                            .readLines()
                            .size
                    ).toMutableList()
            }
        return changed
    }

    private fun git(vararg args: String): String {
        val output = java.io.ByteArrayOutputStream()
        execOperations.exec {
            workingDir = projectDir.get().asFile
            commandLine(listOf("git") + args)
            standardOutput = output
        }
        return output.toString(Charsets.UTF_8)
    }

    // [3, 4, 5, 9] -> "3-5, 9"
    private fun lineRanges(lines: List<Int>): String =
        lines
            .sorted()
            .fold(mutableListOf<IntRange>()) { ranges, line ->
                val last = ranges.lastOrNull()
                if (last != null && last.last == line - 1) ranges[ranges.lastIndex] = last.first..line else ranges += line..line
                ranges
            }.joinToString { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }
}

tasks.register<DiffCoverageTask>("diffCoverage") {
    description = "Fails when too few of the shared module's changed lines are covered by tests."
    group = "verification"
    dependsOn(":shared:koverXmlReport")
    report.set(layout.projectDirectory.file("shared/build/reports/kover/report.xml"))
    sourceDirs.set(listOf("shared/src/commonMain", "shared/src/androidMain"))
    // CI checks out main as origin/main. -Pcoverage.base=<ref> compares with something else.
    baseRef.set(providers.gradleProperty("coverage.base").orElse("origin/main"))
    minimumPercent.set(providers.gradleProperty("coverage.minimumPercent").map { it.toInt() }.orElse(80))
    projectDir.set(layout.projectDirectory)
}
