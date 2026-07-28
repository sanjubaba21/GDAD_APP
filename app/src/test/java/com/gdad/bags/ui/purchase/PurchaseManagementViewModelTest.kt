package com.gdad.bags.ui.purchase

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
import com.gdad.bags.domain.purchase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PurchaseManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()
    @Test fun purchaseRetryReusesExactIdempotencyKey() = runTest(dispatcher) {
        val repo=FakeRepo(); val vm=PurchaseManagementViewModel(repo,FakeProducts()); vm.activate(OWNER); advanceUntilIdle()
        vm.postPurchase(DRAFT); advanceUntilIdle(); vm.retry(); advanceUntilIdle()
        assertEquals(2,repo.ids.size); assertEquals(repo.ids.first(),repo.ids.last()); assertEquals(POSTED,vm.state.value.postedPurchase)
    }
    private class FakeRepo:PurchaseManagementRepository {
        val ids=mutableListOf<String>(); override fun observe(session:UserSession)=flowOf(PurchaseDirectory())
        override suspend fun refresh(session:UserSession)=PurchaseResult.Success(Unit,"ok")
        override suspend fun manageVendor(session:UserSession,requestId:String,mutation:VendorMutation,draft:VendorDraft)=PurchaseResult.Success(Unit,"ok")
        override suspend fun postPurchase(session:UserSession,requestId:String,draft:PurchaseDraft):PurchaseResult<PostedPurchase>{ ids+=requestId; return if(ids.size==1) PurchaseResult.Failure(RemoteFailure(RemoteErrorKind.TIMEOUT,RetryDisposition.WITH_BACKOFF),"timeout") else PurchaseResult.Success(POSTED,"posted") }
    }
    private class FakeProducts:ProductCatalogRepository { override fun observe(session:UserSession):Flow<List<CatalogProduct>> = flowOf(emptyList()); override suspend fun refresh(session:UserSession)=ProductResult.Success("ok"); override suspend fun mutate(session:UserSession,requestId:String,mutation:ProductMutation,draft:ProductDraft)=ProductResult.Success("ok") }
    companion object { const val SHOP="11111111-1111-4111-8111-111111111111"; const val VENDOR="22222222-2222-4222-8222-222222222222"; const val PRODUCT="33333333-3333-4333-8333-333333333333"; val OWNER=UserSession("44444444-4444-4444-8444-444444444444","Owner",UserRole.OWNER,SHOP); val DRAFT=PurchaseDraft(VENDOR,null,"2026-07-28",listOf(PurchaseLineDraft(PRODUCT,"Bag",1,100)),0,null); val POSTED=PostedPurchase("55555555-5555-4555-8555-555555555555","66666666-6666-4666-8666-666666666666",null,100,0,100,1) }
}
