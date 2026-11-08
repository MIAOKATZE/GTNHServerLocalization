plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Keep Git's dirty/branch information; only remove the conventional tag prefix.
version = version.toString().removePrefix("v")
extra["modVersion"] = version.toString()

val localizationTestClasspath = sourceSets.test.get().runtimeClasspath

tasks.register("writeLocalizationTestClasspath") {
    val output = layout.buildDirectory.file("localization-test-classpath.txt")
    outputs.file(output)
    doLast {
        output.get().asFile.writeText(localizationTestClasspath.asPath)
    }
}

val localizationRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs language loading, formatting and chat component regressions."
    dependsOn(tasks.testClasses)
    classpath = localizationTestClasspath
    mainClass.set("ru.glitchless.serverlocalization.lang.LocalizationRegressionTest")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(26))
    })
}

tasks.check {
    dependsOn(localizationRegressionTest)
}

tasks.test {
    // The current regression suite is a JavaExec harness, without a JUnit dependency.
    failOnNoDiscoveredTests.set(false)
}
