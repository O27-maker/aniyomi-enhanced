import mihon.buildlogic.tasks.GenerateLocalesConfigTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.kotlin.multiplatform")
    alias(libs.plugins.moko)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "tachiyomi.i18n.aniyomi"
    }
}

androidComponents {
    onVariants { variant ->
        val resSource = variant.sources.res ?: return@onVariants
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<GenerateLocalesConfigTask>("generate${variantName}LocalesConfig") {
            localeFiles.from(
                layout.projectDirectory.dir("src/commonMain/moko-resources").asFileTree.matching {
                    include("**/strings.xml", "**/plurals.xml")
                },
            )
        }
        resSource.addGeneratedSourceDirectory(task) { it.outputDirectory }
    }
}

multiplatformResources {
    resourcesClassName.set("AYMR")
    resourcesPackage.set("tachiyomi.i18n.aniyomi")
}
