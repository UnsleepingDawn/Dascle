package com.fitplan.gradle

import org.gradle.api.Project

interface BuildConfig {
    val includeDependencyInfo: Boolean
}

val Project.Config: BuildConfig get() = object : BuildConfig {
    override val includeDependencyInfo: Boolean = project.hasProperty("include-dependency-info")
}
