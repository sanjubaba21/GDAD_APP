package com.gdad.bags.data.local

data class CacheOwner(val userId: String, val shopId: String?) {
    val tenantKey: String get() = shopId ?: "platform"

    init {
        require(userId.isNotBlank())
        require(shopId == null || shopId.isNotBlank())
    }
}

interface SessionCache {
    suspend fun activate(owner: CacheOwner)
    suspend fun purge()
}

/** Desktop read caches arrive in a later slice; authentication still gets strict owner isolation. */
class DesktopSessionCache : SessionCache {
    @Volatile
    private var activeOwner: CacheOwner? = null

    override suspend fun activate(owner: CacheOwner) {
        activeOwner = owner
    }

    override suspend fun purge() {
        activeOwner = null
    }
}
