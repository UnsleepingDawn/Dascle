plugins {
    alias(fitx.plugins.android.library)
    alias(fitx.plugins.spotless)

    alias(libs.plugins.metro)
}

android {
    namespace = "com.fitplan.core.metro"
}

dependencies {
    implementation(libs.metro.runtime)
}
