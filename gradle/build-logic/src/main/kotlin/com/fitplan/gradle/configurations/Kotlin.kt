package com.fitplan.gradle.configurations

import com.fitplan.gradle.extensions.fitx
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import tapmoc.configureJavaCompatibility

fun Project.configureKotlin() {
    configureJavaCompatibility(fitx.versions.java.get().toInt())
}

@Suppress("Unused")
private fun Project.kotlin(block: KotlinBaseExtension.() -> Unit) {
    extensions.configure(block)
}

@Suppress("Unused")
private fun KotlinBaseExtension.compilerOptions(block: KotlinCommonCompilerOptions.() -> Unit) {
    if (this is HasConfigurableKotlinCompilerOptions<*>) compilerOptions(block)
}
