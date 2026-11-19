import mihon.buildlogic.AndroidConfig
import mihon.buildlogic.libs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.CompileSdkVersion

plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")
    id("mihon.code.lint")
}

kotlin {
    android {
        minSdk = AndroidConfig.MIN_SDK
        compileSdk {
            version = release("37.2")
        }
        enableCoreLibraryDesugaring = true
    }
    applyDefaultHierarchyTemplate()
}

dependencies {
    "coreLibraryDesugaring"(libs.desugar)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(AndroidConfig.JvmTarget)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        val warningsAsErrors: String? by project
        allWarningsAsErrors.set(warningsAsErrors.toBoolean())
    }
}

private fun KotlinMultiplatformExtension.android(block: KotlinMultiplatformAndroidLibraryTarget.() -> Unit) {
    targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach(block)
}

private fun CompileSdkSpec.release(version: String): CompileSdkVersion {
    val (major, minor) = version.split(".", limit = 2).let {
        it[0].toInt() to it.getOrNull(1)?.toInt()
    }
    return release(major) { minorApiLevel = minor }
}
