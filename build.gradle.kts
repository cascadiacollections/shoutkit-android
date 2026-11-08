// Top-level build file where you can add configuration options common to all sub-projects/modules.

// These are transitive Gradle plugin dependencies. Explicit constraints keep
// security updates editable by Dependabot without adding libraries to the APK.
buildscript {
    dependencies {
        constraints {
            classpath("com.google.guava:guava:33.8.0-jre")
            classpath("org.apache.commons:commons-lang3:3.21.0")
            classpath("org.apache.httpcomponents:httpclient:4.5.14")
            classpath("org.bitbucket.b_c:jose4j:0.9.7")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.jdom:jdom2:2.0.6.1")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.aboutlibraries.plugin) apply false
    alias(libs.plugins.detekt) apply false
}

// Android Lint runs from its own resolved classpath (androidLintTool), which AGP fills with
// older Guava, commons-lang3, httpclient and Bouncy Castle. Patched minimums for that tool
// classpath only.
val lintToolSecurityConstraints = listOf(
    "com.google.guava:guava:33.8.0-jre",
    "org.apache.commons:commons-lang3:3.21.0",
    "org.apache.httpcomponents:httpclient:4.5.14",
    "org.bouncycastle:bcpkix-jdk18on:1.86",
    "org.bouncycastle:bcprov-jdk18on:1.86",
)
// Patched minimum for Guava, which media3, Cast and Wear pull in transitively.
val guavaSecurity = libs.guava.security.get().toString()
subprojects {
    val constraintHandler = dependencies.constraints
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        lintToolSecurityConstraints.forEach { dependencyConstraints.add(constraintHandler.create(it)) }
    }
    configurations.matching { it.name == "implementation" }.configureEach {
        dependencyConstraints.add(constraintHandler.create(guavaSecurity))
    }
}

// Configure Java toolchain for all subprojects
subprojects {
    plugins.withType<JavaBasePlugin>().configureEach {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
                // Any vendor's JDK 21. mise installs Temurin locally and in CI, but
                // F-Droid's build server only has Debian's OpenJDK, and a vendor pin
                // fails there with "Cannot find a Java installation".
            }
        }
    }
}

// Static analysis for every module: detekt (+ ktlint formatting and Compose rules).
// Findings that predate a rule live in each module's detekt-baseline.xml; new code must be clean.
// `./gradlew detekt` checks, `--auto-correct` applies formatting fixes, `detektBaseline` regenerates baselines.
val detektRulePlugins = listOf(libs.detekt.rules.ktlint.wrapper, libs.compose.rules.detekt)
val detektReportMerge by tasks.registering(dev.detekt.gradle.report.ReportMergeTask::class) {
    output.set(layout.buildDirectory.file("reports/detekt/detekt.sarif"))
}
subprojects {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline.set(file("detekt-baseline.xml"))
        basePath.set(rootProject.layout.projectDirectory)
        // Every source set, including product flavors (src/play, src/foss) and tests.
        source.setFrom("src", "build.gradle.kts")
    }
    dependencies {
        detektRulePlugins.forEach { "detektPlugins"(it) }
    }
    // One SARIF file for GitHub code scanning (see .github/workflows/build.yml).
    val detekt = tasks.named<dev.detekt.gradle.Detekt>("detekt") { finalizedBy(detektReportMerge) }
    detektReportMerge.configure { input.from(detekt.flatMap { it.reports.sarif.outputLocation }) }
}
