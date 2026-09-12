import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "com.gdad.bags"
version = "0.1.0"

kotlin { jvmToolchain(17) }

val sharedAndroidSources = rootProject.file("app/src/main/java")
val generatedRuntimeResources = layout.buildDirectory.dir("generated/resources/runtime")
val desktopSupabaseUrl = providers.environmentVariable("SUPABASE_URL")
    .orElse(providers.gradleProperty("SUPABASE_URL"))
    .orElse("")
val desktopSupabasePublishableKey = providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY")
    .orElse(providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY"))
    .orElse("")
val productionDesktopRelease = providers.environmentVariable("GDAD_DESKTOP_PRODUCTION_RELEASE")
    .orElse(providers.gradleProperty("GDAD_DESKTOP_PRODUCTION_RELEASE"))
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

sourceSets {
    main {
        kotlin.srcDir(sharedAndroidSources)
        kotlin.include(
            "com/gdad/bags/desktop/**",
            "com/gdad/bags/data/auth/DesktopAuthPlatform.kt",
            "com/gdad/bags/data/auth/ProductionAuthRepository.kt",
            "com/gdad/bags/data/auth/SupabaseAuthDataSources.kt",
            "com/gdad/bags/data/local/DesktopSessionCache.kt",
            "com/gdad/bags/data/local/DesktopCacheEntities.kt",
            "com/gdad/bags/data/product/ProductRemoteDataSource.kt",
            "com/gdad/bags/data/purchase/PurchaseRemoteDataSource.kt",
            "com/gdad/bags/data/sale/ProductionSaleCheckoutRepository.kt",
            "com/gdad/bags/data/sale/SaleRemoteDataSource.kt",
            "com/gdad/bags/data/remote/RemoteContracts.kt",
            "com/gdad/bags/data/remote/RemoteDtos.kt",
            "com/gdad/bags/data/remote/RemoteQueryWindow.kt",
            "com/gdad/bags/data/remote/SupabaseClientFactory.kt",
            "com/gdad/bags/data/report/ReportRemoteDataSource.kt",
            "com/gdad/bags/domain/auth/Authentication.kt",
            "com/gdad/bags/domain/model/**",
            "com/gdad/bags/domain/product/ProductCatalog.kt",
            "com/gdad/bags/domain/purchase/PurchaseManagement.kt",
            "com/gdad/bags/domain/report/BusinessReporting.kt",
            "com/gdad/bags/domain/sale/SaleCheckout.kt",
        )
        resources.srcDir(generatedRuntimeResources)
    }
}

val generateDesktopRuntimeConfig by tasks.registering {
    outputs.dir(generatedRuntimeResources)
    doLast {
        val output = generatedRuntimeResources.get().file("gdad-desktop.properties").asFile
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("supabase.url=${desktopSupabaseUrl.get()}")
                appendLine("supabase.publishableKey=${desktopSupabasePublishableKey.get()}")
            },
        )
    }
}

tasks.named("processResources") { dependsOn(generateDesktopRuntimeConfig) }

val verifyDesktopAuthSafety by tasks.registering {
    group = "verification"
    description = "Rejects desktop preview authentication, privileged keys, and hard-coded PINs."

    val sources = fileTree("src/main/kotlin") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val forbiddenPatterns = linkedMapOf(
            "preview authentication" to Regex("PreviewAuthRepository"),
            "Supabase secret/service-role key" to Regex("(?:sb_secret_|service_role)"),
            "hard-coded numeric PIN" to Regex("(?i)pin\\s*=\\s*\"\\d{4,8}\""),
        )
        val violations = sources.files.flatMap { source ->
            forbiddenPatterns.mapNotNull { (description, pattern) ->
                if (pattern.containsMatchIn(source.readText())) {
                    "${source.relativeTo(projectDir)}: $description"
                } else {
                    null
                }
            }
        }
        check(violations.isEmpty()) {
            "Desktop authentication safety check failed:\n${violations.joinToString("\n")}"
        }
    }
}

val verifyDesktopProductionReady by tasks.registering {
    group = "verification"
    description = "Fails unless a protected desktop release targets production with a client key."

    doLast {
        check(productionDesktopRelease.get()) {
            "Set GDAD_DESKTOP_PRODUCTION_RELEASE=true only for an approved production desktop build."
        }
        val url = desktopSupabaseUrl.get().trim().removeSuffix("/")
        val key = desktopSupabasePublishableKey.get().trim()
        check(url.startsWith("https://") && url.endsWith(".supabase.co")) {
            "A valid production Supabase HTTPS origin is required."
        }
        check("zniqkuwktvincjndcgpu" !in url) {
            "A production desktop release must not target the development project."
        }
        check(Regex("^sb_publishable_[A-Za-z0-9_-]{20,240}$").matches(key)) {
            "A client-safe Supabase publishable key is required."
        }
    }
}

tasks.named("check") { dependsOn(verifyDesktopAuthSafety) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.6.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-cio:3.5.0")
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.gdad.bags.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "GDAD BAGS"
            packageVersion = project.version.toString()
            description = "GDAD BAGS sales, stock, vendor, cash and reporting desktop application"
            vendor = "GDAD BAGS"
            windows {
                iconFile.set(project.file("src/main/resources/gdad-bags.ico"))
                menuGroup = "GDAD BAGS"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
