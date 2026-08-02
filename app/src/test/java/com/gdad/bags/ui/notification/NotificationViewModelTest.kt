package com.gdad.bags.ui.notification

import com.gdad.bags.data.notification.ProductionNotificationRepositoryTest
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.notification.*
import com.gdad.bags.ui.components.ContentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test
    fun cachedFeedSurvivesOfflineAndOpeningMarksUnreadItem() = runTest(dispatcher) {
        val repository = Repository()
        val viewModel = NotificationViewModel(repository)

        viewModel.activate(ProductionNotificationRepositoryTest.OWNER)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertTrue(viewModel.state.value.safeMessage!!.contains("offline", ignoreCase = true))

        viewModel.select(ProductionNotificationRepositoryTest.NOTIFICATION)
        advanceUntilIdle()

        assertEquals(ProductionNotificationRepositoryTest.NOTIFICATION, viewModel.state.value.selectedId)
        assertEquals(1, repository.marks)
        val center = (viewModel.state.value.content as ContentState.Ready).value
        assertEquals(0, center.unreadCount)
    }

    @Test
    fun categorySelectionIsStateful() = runTest(dispatcher) {
        val viewModel = NotificationViewModel(Repository())
        viewModel.activate(ProductionNotificationRepositoryTest.OWNER)
        advanceUntilIdle()
        viewModel.setCategory("low_stock")
        assertEquals("low_stock", viewModel.state.value.category)
    }

    private class Repository : NotificationRepository {
        val center = MutableStateFlow(NotificationCenter(listOf(ProductionNotificationRepositoryTest.active())))
        var marks = 0
        override fun observe(session: UserSession): Flow<NotificationCenter> = center
        override suspend fun refresh(session: UserSession) = NotificationResult.Failure(
            RemoteFailure(RemoteErrorKind.OFFLINE, RetryDisposition.WITH_BACKOFF),
            "You are offline. Saved notifications remain available.",
        )
        override suspend fun markRead(session: UserSession, notificationId: String): NotificationResult<Unit> {
            marks++
            center.value = NotificationCenter(center.value.items.map {
                if (it.id == notificationId) it.copy(isRead = true) else it
            })
            return NotificationResult.Success(Unit, "read")
        }
    }
}
