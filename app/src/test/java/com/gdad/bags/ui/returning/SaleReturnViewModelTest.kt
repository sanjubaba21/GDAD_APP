package com.gdad.bags.ui.returning

import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.returning.PostedSaleReturn
import com.gdad.bags.domain.returning.RefundMethod
import com.gdad.bags.domain.returning.ReturnDisposition
import com.gdad.bags.domain.returning.ReturnLineDraft
import com.gdad.bags.domain.returning.ReturnResult
import com.gdad.bags.domain.returning.SaleHistory
import com.gdad.bags.domain.returning.SaleReturnDraft
import com.gdad.bags.domain.returning.SaleReturnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SaleReturnViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test
    fun retryReusesTheExactIdempotencyKey() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = SaleReturnViewModel(repository)
        viewModel.activate(OWNER)
        advanceUntilIdle()

        viewModel.post(DRAFT)
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.requestIds.size)
        assertEquals(repository.requestIds.first(), repository.requestIds.last())
        assertEquals(POSTED, viewModel.state.value.posted)
    }

    @Test
    fun conflictReloadsVisibleReturnableHistory() = runTest(dispatcher) {
        val repository = FakeRepository().apply { conflict = true }
        val viewModel = SaleReturnViewModel(repository)
        viewModel.activate(OWNER)
        advanceUntilIdle()

        viewModel.post(DRAFT)
        advanceUntilIdle()

        assertEquals(2, repository.loads)
        assertTrue(viewModel.state.value.safeMessage.orEmpty().contains("changed"))
    }

    private class FakeRepository : SaleReturnRepository {
        val requestIds = mutableListOf<String>()
        var loads = 0
        var conflict = false
        override suspend fun load(session: UserSession): ReturnResult<SaleHistory> {
            loads++
            return ReturnResult.Success(SaleHistory(), "ok")
        }
        override suspend fun post(
            session: UserSession,
            requestId: String,
            draft: SaleReturnDraft,
        ): ReturnResult<PostedSaleReturn> {
            requestIds += requestId
            if (conflict) {
                return ReturnResult.Failure(
                    RemoteFailure(RemoteErrorKind.CONFLICT, RetryDisposition.NEVER),
                    "Returnable quantities changed. History was refreshed.",
                )
            }
            return if (requestIds.size == 1) {
                ReturnResult.Failure(
                    RemoteFailure(RemoteErrorKind.TIMEOUT, RetryDisposition.WITH_BACKOFF),
                    "timeout",
                )
            } else {
                ReturnResult.Success(POSTED, "ok")
            }
        }
    }

    companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        const val ACTOR = "22222222-2222-4222-8222-222222222222"
        const val SALE = "33333333-3333-4333-8333-333333333333"
        const val LINE = "44444444-4444-4444-8444-444444444444"
        const val RETURN = "55555555-5555-4555-8555-555555555555"
        val OWNER = UserSession(ACTOR, "Owner", UserRole.OWNER, SHOP)
        val DRAFT = SaleReturnDraft(
            SALE,
            "2026-07-28",
            "Exchange",
            listOf(ReturnLineDraft(LINE, 1, ReturnDisposition.SELLABLE)),
            RefundMethod.CASH,
        )
        val POSTED = PostedSaleReturn(RETURN, SALE, 10000, 10000, 0, 1, 5000, "returned")
    }
}
