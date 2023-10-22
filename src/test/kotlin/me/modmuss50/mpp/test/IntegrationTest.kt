package me.modmuss50.mpp.test

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import java.io.File

interface IntegrationTest {
    fun gradleTest(groovy: Boolean = false): TestBuilder {
        return TestBuilder(groovy)
    }

    class TestBuilder(val groovy: Boolean) {
        private val runner = GradleRunner.create()
            .withPluginClasspath()
            .forwardOutput()
            .withDebug(true)

        private val gradleHome: File
        private val projectDir: File
        private val buildScript: File
        private var arguments = ArrayList<String>()

        init {
            val testDir = File("build/intergation_test")
            val ext = if (groovy) { "" } else { ".kts" }
            gradleHome = File(testDir, "home")
            projectDir = File(testDir, "project")
            buildScript = File(projectDir, "build.gradle$ext")

            projectDir.mkdirs()

            // Clean up
            File(projectDir, "build.gradle").delete()
            File(projectDir, "build.gradle.kts").delete()
            File(projectDir, "settings.gradle").delete()
            File(projectDir, "settings.gradle.kts").delete()

            // Create a fmj for modrith
            val resources = File(projectDir, "src/main/resources")
            resources.mkdirs()
            File(resources, "fabric.mod.json").writeText("{}")

            if (!groovy) {
                buildScript(
                    """
                plugins {
                    java
                    id("me.modmuss50.mod-publish-plugin")
                }
                    """.trimIndent(),
                )
            } else {
                buildScript(
                    """
                plugins {
                    id 'java'
                    id 'me.modmuss50.mod-publish-plugin'
                }
                    """.trimIndent(),
                )
            }

            File(projectDir, "settings.gradle$ext").writeText("rootProject.name = \"mpp-example\"")

            runner.withProjectDir(projectDir)
            argument("--gradle-user-home", gradleHome.absolutePath)
            argument("--stacktrace")
            argument("--warning-mode", "fail")
            argument("clean")
        }

        // Appends to an existing buildscript
        fun buildScript(@Language("gradle") script: String): TestBuilder {
            buildScript.appendText(script + "\n")
            return this
        }

        fun argument(vararg args: String) {
            arguments.addAll(args)
        }

        fun run(task: String): BuildResult {
            argument(task)
            runner.withArguments(arguments)
            return runner.run()
        }
    }
}
