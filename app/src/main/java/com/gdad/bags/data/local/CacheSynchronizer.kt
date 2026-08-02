package com.gdad.bags.data.local

import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RemoteResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface CacheSnapshotSource {
    suspend fun fetch(owner: CacheOwner): RemoteResult<CacheSnapshot>
}

sealed interface CacheSyncResult {
    data object Refreshed : CacheSyncResult
    data class Failed(val error: RemoteFailure) : CacheSyncResult
}

/** Serializes refreshes and publishes only complete, owner-matched snapshots. */
class CacheSynchronizer(
    private val source: CacheSnapshotSource,
    private val store: RoomCacheStore,
) {
    private val refreshMutex = Mutex()

    suspend fun refresh(owner: CacheOwner): CacheSyncResult = refreshMutex.withLock {
        when (val remote = source.fetch(owner)) {
            is RemoteResult.Failure -> CacheSyncResult.Failed(remote.error)
            is RemoteResult.Success -> {
                require(remote.value.owner == owner) {
                    "Remote snapshot owner does not match the active cache owner"
                }
                store.replaceSnapshot(remote.value)
                CacheSyncResult.Refreshed
            }
        }
    }
}
