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
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
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

// Every static check in one task, so they can all be run locally before committing. CI runs it too.
tasks.register("qualityGate") {
    group = "verification"
    description = "Runs all static checks."
    dependsOn(
        ":androidApp:lintDebug",
        "spotlessCheck",
        "cpdCheck",
        // Detekt with type resolution. The app's debug and release variants have the same code, so one is enough.
        ":androidApp:detektDebug",
        ":shared:detektMainAndroid",
        ":shared:detektHostTestAndroid",
    )
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
        val minimumTokens = providers.gradleProperty("cpd.minimumTokens").orElse("100")
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
