buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.0")
    }
}
plugins {
    alias(libs.plugins.android).apply(false)
    alias(libs.plugins.kotlinAndroid).apply(false)
    alias(libs.plugins.kotlinSerialization).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.parcelize).apply(false)
    alias(libs.plugins.library).apply(false)
}

tasks.register<Delete>("clean") {
    delete {
        rootProject.buildDir
    }
}
