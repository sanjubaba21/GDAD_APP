plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
    .orElse(providers.environmentVariable("SUPABASE_URL"))
    .orElse("")
val supabasePublishableKey = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY")
    .orElse(providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY"))
    .orElse("")

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.gdad.bags"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.gdad.bags"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.get().asBuildConfigString())
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            supabasePublishableKey.get().asBuildConfigString(),
        )
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { jvmToolchain(17) }

val verifyReleaseAuthSafety by tasks.registering {
    group = "verification"
    description = "Fails when release sources contain preview authentication or embedded secrets."

    val productionSources = fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    }
    inputs.files(productionSources)

    doLast {
        val forbiddenPatterns = linkedMapOf(
            "preview authentication adapter" to Regex("PreviewAuthRepository"),
            "user-ID prefix role inference" to Regex("startsWith\\(\\s*\"(?:admin|sales)\""),
            "Supabase secret/service-role key" to Regex("(?:sb_secret_|service_role)"),
            "hard-coded numeric PIN" to Regex("(?i)pin\\s*=\\s*\"\\d{4,8}\""),
        )
        val violations = productionSources.files.flatMap { source ->
            val contents = source.readText()
            forbiddenPatterns.mapNotNull { (description, pattern) ->
                if (pattern.containsMatchIn(contents)) {
                    "${source.relativeTo(projectDir)}: $description"
                } else {
                    null
                }
            }
        }

        check(violations.isEmpty()) {
            "Release authentication safety check failed:\n${violations.joinToString("\n")}"
        }

        val compositionRoot = file(
            "src/main/java/com/gdad/bags/di/AppContainer.kt",
        ).readText()
        check("ProductionAuthRepository(" in compositionRoot) {
            "ProductionAppContainer must bind ProductionAuthRepository."
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(verifyReleaseAuthSafety)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.6.0")
    implementation(supabaseBom)
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-android:3.5.0")
    testImplementation("junit:junit:4.13.2")
}
