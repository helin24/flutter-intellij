/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

// See https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-plugins.html#module,
// flutter-studio is a submodule importing org.jetbrains.intellij.platform.module.

plugins {
  // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
  // https://github.com/JetBrains/intellij-platform-gradle-plugin/releases
  // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm
  id("java")
  id("org.jetbrains.intellij.platform.module")
  id("org.jetbrains.kotlin.jvm") version "2.1.21-RC2"
}

val flutterPluginVersion = providers.gradleProperty("flutterPluginVersion").get()
val ideaProduct = providers.gradleProperty("ideaProduct").get()
val ideaVersion = providers.gradleProperty("ideaVersion").get()
val dartPluginVersion = providers.gradleProperty("dartPluginVersion").get()
// The Android Plugin version is only used if the ideaProduct is not "android-studio"
val androidPluginVersion = providers.gradleProperty("androidPluginVersion").get()
val sinceBuildInput = providers.gradleProperty("sinceBuild").get()
val untilBuildInput = providers.gradleProperty("untilBuild").get()
val javaVersion = providers.gradleProperty("javaVersion").get()
group = "io.flutter"

var jvmVersion: JvmTarget
jvmVersion = when (javaVersion) {
  "17" -> {
    JvmTarget.JVM_17
  }

  "21" -> {
    JvmTarget.JVM_21
  }

  else -> {
    throw IllegalArgumentException("javaVersion must be defined in the product matrix as either \"17\" or \"21\", but is not for $ideaVersion")
  }
}
kotlin {
  compilerOptions {
    apiVersion.set(KotlinVersion.KOTLIN_1_9)
    jvmTarget = jvmVersion
  }
}

var javaCompatibilityVersion: JavaVersion
javaCompatibilityVersion = when (javaVersion) {
  "17" -> {
    JavaVersion.VERSION_17
  }

  "21" -> {
    JavaVersion.VERSION_21
  }

  else -> {
    throw IllegalArgumentException("javaVersion must be defined in the product matrix as either \"17\" or \"21\", but is not for $ideaVersion")
  }
}
java {
  sourceCompatibility = javaCompatibilityVersion
  targetCompatibility = javaCompatibilityVersion
}

dependencies {
  intellijPlatform {
    // Documentation on the default target platform methods:
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#default-target-platforms
    if (ideaProduct == "android-studio") {
      androidStudio(ideaVersion)
    } else { // if (ideaProduct == "IC") {
      intellijIdeaCommunity(ideaVersion)
    }
    testFramework(TestFrameworkType.Platform)

    // Plugin dependency documentation:
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#plugins
    val bundledPluginList = mutableListOf(
      "com.google.tools.ij.aiplugin",
      "com.intellij.java",
      "com.intellij.properties",
      "JUnit",
      "Git4Idea",
      "org.jetbrains.kotlin",
      "org.jetbrains.plugins.gradle",
      "org.jetbrains.plugins.yaml",
      "org.intellij.intelliLang"
    )
    if (ideaProduct == "android-studio") {
      bundledPluginList.add("org.jetbrains.android")
      bundledPluginList.add("com.android.tools.idea.smali")
    }
    val pluginList = mutableListOf("Dart:$dartPluginVersion")
    if (ideaProduct == "IC") {
      pluginList.add("org.jetbrains.android:$androidPluginVersion")
    }

    // Finally, add the plugins into their respective lists:
    // https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#project-setup
    bundledPlugins(bundledPluginList)
    plugins(pluginList)

    if (sinceBuildInput == "243" || sinceBuildInput == "251") {
      bundledModule("intellij.platform.coverage")
      bundledModule("intellij.platform.coverage.agent")
    }
  }
}

intellijPlatform {
  pluginConfiguration {
    version = flutterPluginVersion
    ideaVersion {
      sinceBuild = sinceBuildInput
      untilBuild = untilBuildInput
    }
  }
}

dependencies {
  compileOnly(project(":flutter-idea"))
  testImplementation(project(":flutter-idea"))
  compileOnly(
    fileTree(
      mapOf(
        "dir" to "${project.rootDir}/artifacts/android-studio/lib",
        "include" to listOf("*.jar")
      )
    )
  )
  testImplementation(
    fileTree(
      mapOf(
        "dir" to "${project.rootDir}/artifacts/android-studio/lib",
        "include" to listOf("*.jar")
      )
    )
  )
  compileOnly(
    fileTree(
      mapOf(
        "dir" to "${project.rootDir}/artifacts/android-studio/plugins",
        "include" to listOf("**/*.jar"),
        "exclude" to listOf("**/kotlin-compiler.jar", "**/kotlin-plugin.jar")
      )
    )
  )
  testImplementation(
    fileTree(
      mapOf(
        "dir" to "${project.rootDir}/artifacts/android-studio/plugins",
        "include" to listOf("**/*.jar"),
        "exclude" to listOf("**/kotlin-compiler.jar", "**/kotlin-plugin.jar")
      )
    )
  )
}

sourceSets {
  main {
    java.srcDirs(
      listOf(
        "src",
        "third_party/vmServiceDrivers"
      )
    )
  }
}
