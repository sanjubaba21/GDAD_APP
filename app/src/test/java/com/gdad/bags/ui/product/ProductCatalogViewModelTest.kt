package com.gdad.bags.ui.product

import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.domain.product.ProductResult
import com.gdad.bags.ui.components.ContentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductCatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun searchMatchesNameSkuAndBarcode() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = ProductCatalogViewModel(repository)
        viewModel.activate(OWNER)
        advanceUntilIdle()

        viewModel.search("8901")

        val products = (viewModel.state.value.content as ContentState.Ready).value
        assertEquals(listOf("Travel Bag"), products.map { it.name })
    }

    @Test
    fun retryReusesExactMutationRequestId() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = ProductCatalogViewModel(repository)
        viewModel.activate(OWNER)
        advanceUntilIdle()
        viewModel.mutate(ProductMutation.UPDATE, DRAFT)
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.requestIds.size)
        assertEquals(repository.requestIds.first(), repository.requestIds.last())
        assertEquals("Product updated and audited.", viewModel.state.value.safeMessage)
    }

    private class FakeRepository : ProductCatalogRepository {
        private val products = MutableStateFlow(listOf(PRODUCT))
        val requestIds = mutableListOf<String>()
        override fun observe(session: UserSession): Flow<List<CatalogProduct>> = products
        override suspend fun refresh(session: UserSession) = ProductResult.Success("Products refreshed.")
        override suspend fun mutate(session: UserSession, requestId: String, mutation: ProductMutation, draft: ProductDraft): ProductResult {
            requestIds += requestId
            return if (requestIds.size == 1) ProductResult.Failure(
                RemoteFailure(RemoteErrorKind.TIMEOUT, RetryDisposition.WITH_BACKOFF), "The request timed out. Try again.",
            ) else ProductResult.Success("Product updated and audited.")
        }
    }

    private companion object {
        const val SHOP = "11111111-1111-4111-8111-111111111111"
        const val PRODUCT_ID = "22222222-2222-4222-8222-222222222222"
        val OWNER = UserSession("33333333-3333-4333-8333-333333333333", "Owner", UserRole.OWNER, SHOP)
        val PRODUCT = CatalogProduct(PRODUCT_ID, "Travel Bag", "TB-1", "890123", 150000, 4, 8, 640000, true)
        val DRAFT = ProductDraft(PRODUCT_ID, "Travel Bag", "TB-1", "890123", 150000, 4)
    }
}
