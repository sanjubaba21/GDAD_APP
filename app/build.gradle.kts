import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

fun releaseProperty(name: String) = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))

val appVersionCode = 9
val appVersionName = "0.2.0-rc8"
val developmentProjectRef = "zniqkuwktvincjndcgpu"
val productionReleaseRequested = releaseProperty("GDAD_PRODUCTION_RELEASE")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()
fun clientProperty(name: String) = if (productionReleaseRequested) {
    providers.environmentVariable(name).orElse(providers.gradleProperty(name))
} else {
    providers.gradleProperty(name).orElse(providers.environmentVariable(name))
}
val supabaseUrl = clientProperty("SUPABASE_URL").orElse("")
val supabasePublishableKey = clientProperty("SUPABASE_PUBLISHABLE_KEY").orElse("")
val releaseStoreFilePath = releaseProperty("GDAD_RELEASE_STORE_FILE")
val releaseStorePassword = releaseProperty("GDAD_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseProperty("GDAD_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseProperty("GDAD_RELEASE_KEY_PASSWORD")
val releaseSigningValues = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val releaseSigningConfigured = releaseSigningValues.all { !it.orNull.isNullOrBlank() }
val releaseSigningPartiallyConfigured =
    releaseSigningValues.any { !it.orNull.isNullOrBlank() } && !releaseSigningConfigured

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun ByteArray.containsBytes(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    for (start in 0..size - needle.size) {
        var matches = true
        for (offset in needle.indices) {
            if (this[start + offset] != needle[offset]) {
                matches = false
                break
            }
        }
        if (matches) return true
    }
    return false
}

android {
    namespace = "com.gdad.bags"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.gdad.bags"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.get().asBuildConfigString())
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            supabasePublishableKey.get().asBuildConfigString(),
        )
    }
    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStoreFilePath.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8/resource shrinking stays disabled until the signed device smoke matrix passes.
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

room { schemaDirectory("$projectDir/schemas") }

val verifyReleaseAuthSafety by tasks.registering {
    group = "verification"
    description = "Fails when release sources contain preview authentication or embedded secrets."

    val productionSources = fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    }
    val sourceManifest = file("src/main/AndroidManifest.xml")
    inputs.files(productionSources, sourceManifest)

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

        val manifest = sourceManifest.readText()
        val requiredManifestPolicies = linkedMapOf(
            "backup disabled" to "android:allowBackup=\"false\"",
            "cloud/device-transfer exclusion rules" to "android:dataExtractionRules=",
            "legacy backup exclusion rules" to "android:fullBackupContent=",
            "cleartext disabled" to "android:usesCleartextTraffic=\"false\"",
            "network security policy" to "android:networkSecurityConfig=",
        )
        val missingPolicies = requiredManifestPolicies.filterValues { it !in manifest }.keys
        check(missingPolicies.isEmpty()) {
            "Release manifest safety check failed: missing ${missingPolicies.joinToString()}."
        }
        check("android.permission.POST_NOTIFICATIONS" !in manifest) {
            "Do not request notification permission until system notification delivery exists."
        }
        check("android.intent.category.BROWSABLE" !in manifest) {
            "Release manifest must not expose deep links without a reviewed authenticated contract."
        }
    }
}

val verifyReleaseArtifactSafety by tasks.registering {
    group = "verification"
    description = "Scans the assembled release APK for forbidden auth, secret, and test markers."
    dependsOn("assembleRelease")

    val releaseApk = layout.buildDirectory.file(
        if (releaseSigningConfigured) {
            "outputs/apk/release/app-release.apk"
        } else {
            "outputs/apk/release/app-release-unsigned.apk"
        },
    )
    inputs.file(releaseApk)

    doLast {
        val apk = releaseApk.get().asFile
        check(apk.isFile) { "Release APK was not produced at ${apk.absolutePath}." }

        val forbiddenMarkers = linkedMapOf(
            "preview authentication adapter" to "PreviewAuthRepository",
            "Supabase secret-key prefix" to "sb_secret_",
            "PIN pepper environment name" to "GDAD_PIN_PEPPER_V1",
            "rate-limit pepper environment name" to "GDAD_RATE_LIMIT_PEPPER_V1",
            "bootstrap credential environment name" to "GDAD_BOOTSTRAP_TOKEN",
            "diagnostic credential environment name" to "GDAD_LOGIN_DIAGNOSTIC_TOKEN",
        ).mapValues { (_, marker) -> marker.toByteArray(Charsets.UTF_8) }

        val violations = mutableListOf<String>()
        ZipFile(apk).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val contents = archive.getInputStream(entry).use { it.readBytes() }
                forbiddenMarkers.forEach { (description, marker) ->
                    if (contents.containsBytes(marker)) {
                        violations += "${entry.name}: $description"
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            "Release artifact safety check failed:\n${violations.joinToString("\n")}"
        }
    }
}

val verifyProductionReleaseReady by tasks.registering {
    group = "verification"
    description = "Fails unless production backend and signing inputs are complete and isolated."

    doLast {
        check(productionReleaseRequested) {
            "Set GDAD_PRODUCTION_RELEASE=true only for an approved production release build."
        }
        check(!releaseSigningPartiallyConfigured) {
            "Release signing is partially configured; provide all four GDAD_RELEASE_* values."
        }
        check(releaseSigningConfigured) {
            "Production release signing is not configured."
        }
        val store = file(releaseStoreFilePath.get())
        check(store.isFile) { "The configured release keystore does not exist." }

        val releaseUrl = supabaseUrl.get().trim()
        val releaseKey = supabasePublishableKey.get().trim()
        check(releaseUrl.isNotEmpty() && releaseKey.isNotEmpty()) {
            "Production Supabase URL and publishable key are required."
        }
        check(developmentProjectRef !in releaseUrl) {
            "Production release must not target the development Supabase project."
        }
        check(releaseKey.startsWith("sb_publishable_") && releaseKey.length <= 256) {
            "Production release requires a valid client-safe Supabase publishable key."
        }
        check(appVersionCode > 1 && appVersionName.isNotBlank()) {
            "Production versionCode must advance beyond the initial development build."
        }
    }
}

val assembleProductionRelease by tasks.registering {
    group = "build"
    description = "Builds the signed APK only after the production release gate passes."
    dependsOn(verifyProductionReleaseReady, verifyReleaseArtifactSafety)
}

val verifyReleaseAccessibilitySafety by tasks.registering {
    group = "verification"
    description = "Rejects known accessibility and Nepal UX regressions in release sources."

    val productionSources = fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    }
    inputs.files(productionSources)

    doLast {
        val forbiddenPatterns = linkedMapOf(
            "ambiguous rupee label; use NPR" to Regex("\"[^\"]*\\bRs\\s"),
            "mojibake/replacement character" to Regex("[Ãâ�]"),
            "raw clickable; use a Material control with built-in touch semantics" to
                Regex("\\.clickable\\s*\\("),
        )
        val violations = productionSources.files.flatMap { source ->
            val contents = source.readText()
            forbiddenPatterns.mapNotNull { (description, pattern) ->
                if (pattern.containsMatchIn(contents)) {
                    "${source.relativeTo(projectDir)}: $description"
                } else {
                    null
                }
            } + if (
                source.name != "NepalDateTime.kt" &&
                "LocalDate.now(" in contents
            ) {
                listOf("${source.relativeTo(projectDir)}: device-local date; use NepalDateTime")
            } else {
                emptyList()
            }
        }

        val dateScreens = listOf(
            "ui/sale/SaleCheckoutScreen.kt",
            "ui/purchase/PurchaseManagementScreen.kt",
            "ui/returning/SaleReturnScreen.kt",
            "ui/stock/StockManagementScreen.kt",
            "ui/finance/FinanceScreen.kt",
            "ui/vendorfinance/VendorFinanceScreen.kt",
            "ui/report/ReportScreen.kt",
        ).map { file("src/main/java/com/gdad/bags/$it") }
        val bypassedDateScreens = dateScreens.filter { screen ->
            !screen.isFile || "BusinessDateField(" !in screen.readText()
        }

        val sharedStates = file("src/main/java/com/gdad/bags/ui/components/SharedStates.kt").readText()
        val appShell = file("src/main/java/com/gdad/bags/ui/GdadApp.kt").readText()
        val money = file("src/main/java/com/gdad/bags/domain/model/MoneyAmounts.kt").readText()

        check(violations.isEmpty()) {
            "Release accessibility safety check failed:\n${violations.joinToString("\n")}"
        }
        check(bypassedDateScreens.isEmpty()) {
            "Nepal business date field bypassed by: " +
                bypassedDateScreens.joinToString { it.relativeTo(projectDir).path }
        }
        check("liveRegion" in sharedStates && "StatusMessage" in sharedStates) {
            "Shared async states must retain TalkBack live-region announcements."
        }
        check("verticalScroll(rememberScrollState())" in appShell) {
            "The login shell must remain scrollable at large font scales."
        }
        check("\"NPR " in money) {
            "Money display must use the explicit NPR currency code."
        }
    }
}

val verifyReleasePerformanceSafety by tasks.registering {
    group = "verification"
    description = "Rejects unbounded production reads and known first-release performance regressions."

    val productionSources = fileTree("src/main/java") {
        include("**/*.kt", "**/*.java")
    }
    val remoteReadSources = fileTree("src/main/java/com/gdad/bags/data") {
        include("**/*RemoteDataSource.kt", "auth/SupabaseAuthDataSources.kt")
    }
    inputs.files(productionSources, remoteReadSources)
    inputs.file("../supabase/migrations/20260729170000_bounded_report_detail_windows.sql")

    doLast {
        val unboundedRemoteFiles = remoteReadSources.files.mapNotNull { source ->
            val contents = source.readText()
            val selects = Regex("\\.select\\s*\\(").findAll(contents).count()
            val limits = Regex("\\blimit\\s*\\(").findAll(contents).count()
            if (selects != limits) {
                "${source.relativeTo(projectDir)} has $selects select(s) but $limits explicit limit(s)"
            } else {
                null
            }
        }
        check(unboundedRemoteFiles.isEmpty()) {
            "Every remote list select needs an explicit limit:\n${unboundedRemoteFiles.joinToString("\n")}"
        }

        val cacheDao = file("src/main/java/com/gdad/bags/data/local/CacheDao.kt").readText()
        val roomSelects = Regex("SELECT\\s", RegexOption.IGNORE_CASE).findAll(cacheDao).count()
        val roomLimits = Regex("LIMIT\\s", RegexOption.IGNORE_CASE).findAll(cacheDao).count()
        check(roomSelects == roomLimits) {
            "Every Room SELECT must be bounded; found $roomSelects SELECT(s) and $roomLimits LIMIT(s)."
        }

        val mainThreadBlocks = productionSources.files.filter { source ->
            Regex("\\brunBlocking\\s*\\(").containsMatchIn(source.readText())
        }
        check(mainThreadBlocks.isEmpty()) {
            "Production runBlocking is forbidden: ${mainThreadBlocks.joinToString { it.relativeTo(projectDir).path }}"
        }

        val reportMigration = file(
            "../supabase/migrations/20260729170000_bounded_report_detail_windows.sql",
        ).readText()
        check(Regex("limit 501", RegexOption.IGNORE_CASE).findAll(reportMigration).count() == 3) {
            "Trusted report must retain three deterministic 501-row detail sentinels."
        }
        val reportSource = file("src/main/java/com/gdad/bags/data/report/ReportRemoteDataSource.kt").readText()
        val purchaseSource = file("src/main/java/com/gdad/bags/data/purchase/PurchaseRemoteDataSource.kt").readText()
        check("requireSupportedWindow" in reportSource && "requireSupportedWindow" in purchaseSource) {
            "Report detail arrays must fail closed when the sentinel row is returned."
        }
        val stockScreen = file("src/main/java/com/gdad/bags/ui/stock/StockManagementScreen.kt").readText()
        check("lotsByProduct" in stockScreen && "recentMovementsByProduct" in stockScreen) {
            "Stock rendering must retain snapshot-level history indexes."
        }
        val mainActivity = file("src/main/java/com/gdad/bags/MainActivity.kt").readText()
        val navigation = file(
            "src/main/java/com/gdad/bags/ui/navigation/AppNavigation.kt",
        ).readText()
        check(
            "FeatureActivationPolicy.requiredData" in mainActivity &&
                "activeDataSlices" in mainActivity &&
                "FeatureActivationPolicy" in navigation
        ) {
            "Authenticated data must remain destination-scoped and warm only for one identity."
        }
        check(
            !Regex("LaunchedEffect\\(session\\)\\s*\\{\\s*\\w+ViewModel\\.activate")
                .containsMatchIn(mainActivity)
        ) {
            "Do not restore the all-feature authenticated startup request storm."
        }
    }
}

tasks.configureEach {
    if (name == "assembleRelease") {
        mustRunAfter(verifyProductionReleaseReady)
    }
    if (name == "preReleaseBuild") {
        dependsOn(
            verifyReleaseAuthSafety,
            verifyReleaseAccessibilitySafety,
            verifyReleasePerformanceSafety,
        )
        if (productionReleaseRequested) {
            dependsOn(verifyProductionReleaseReady)
        }
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
    testImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.6.0")
    implementation(supabaseBom)
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-android:3.5.0")
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    val navigationVersion = "2.9.8"
    implementation("androidx.navigation:navigation-compose:$navigationVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("androidx.navigation:navigation-testing:$navigationVersion")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
