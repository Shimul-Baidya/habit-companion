import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Private academic demo only. The key is extractable from the APK; never publish it.
// Provider configuration stays outside the Coach UI/domain contract.
val demoGeminiKey = providers.environmentVariable("GEMINI_API_KEY").orElse(
    providers.fileContents(rootProject.layout.projectDirectory.file(".env")).asText
        .map { text -> Properties().apply { load(text.reader()) }.getProperty("GEMINI_API_KEY", "") }
).orElse("").get().trim()
fun buildString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

// The supplied resource is the only editable catalog. Sync packages its exact bytes.
abstract class SyncCoachAssets : DefaultTask() {
    @get:InputFile abstract val catalogFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun copyCatalog() {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        catalogFile.get().asFile.copyTo(directory.resolve("coach_cards.json"), overwrite = true)
    }
}
val coachAssets = tasks.register<SyncCoachAssets>("syncCoachAssets") {
    catalogFile.set(rootProject.layout.projectDirectory.file("resources/coach_cards.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/coachAssets"))
}

android {
    namespace = "com.example.habit"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.habit"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", buildString(demoGeminiKey))
        buildConfigField("String", "GEMINI_MODEL", buildString("gemini-3.5-flash-lite"))
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // minSdk is 24, so java.time needs desugaring. The domain layer is written
        // against java.time rather than epoch arithmetic because it is far easier to read.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(coachAssets, SyncCoachAssets::outputDirectory)
}

ksp {
    // Room schema history, checked in so migrations can be reviewed.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
