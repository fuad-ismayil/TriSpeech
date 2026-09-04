buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.0.21-1.0.28")
        classpath("com.google.gms:google-services:4.4.2")
    }
}
