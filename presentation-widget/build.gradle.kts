plugins {
    alias(fitx.plugins.android.library)
    alias(fitx.plugins.compose)

    alias(fitx.plugins.spotless)
    alias(libs.plugins.metro)
}

android {
    namespace = "com.fitplan.presentation.widget"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.metro)
    implementation(projects.domain)
    implementation(projects.presentationCore)

    // Compose（图表组件要用 MaterialTheme / 圆角等 foundation 能力）
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.glance.appWidget)
    implementation(libs.kotlinx.datetime)

    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.compose.glance)

    implementation(libs.metro.runtime)
}
