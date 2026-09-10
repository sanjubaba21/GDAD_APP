package com.gdad.bags.data.auth

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

fun interface InstallationIdProvider {
    fun getInstallationId(): String
}

/** Stores only a random installation identifier. It never stores a Login ID, PIN, or session. */
class PersistentInstallationIdProvider(
    private val file: Path = desktopDataDirectory().resolve("installation-id"),
) : InstallationIdProvider {
    override fun getInstallationId(): String = synchronized(this) {
        runCatching { Files.readString(file).trim() }
            .getOrNull()
            ?.takeIf(::isValidUuid)
            ?: UUID.randomUUID().toString().also { generated ->
                Files.createDirectories(file.parent)
                Files.writeString(
                    file,
                    generated,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }
    }

    private fun isValidUuid(value: String): Boolean = runCatching {
        UUID.fromString(value).toString() == value.lowercase()
    }.getOrDefault(false)
}

internal fun desktopDataDirectory(): Path {
    val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
    val base = localAppData?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), "AppData", "Local")
    return base.resolve("GDAD BAGS")
}
