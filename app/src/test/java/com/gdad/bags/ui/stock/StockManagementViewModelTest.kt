package com.gdad.bags.ui.stock
import com.gdad.bags.data.remote.*
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.product.*
import com.gdad.bags.domain.stock.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) class StockManagementViewModelTest{private val d=StandardTestDispatcher();@Before fun b()=Dispatchers.setMain(d);@After fun a()=Dispatchers.resetMain();@Test fun retryReusesKey()=runTest(d){val repo=Repo();val vm=StockManagementViewModel(repo,Products());vm.activate(OWNER);advanceUntilIdle();vm.adjust(DRAFT);advanceUntilIdle();vm.retry();advanceUntilIdle();Assert.assertEquals(2,repo.ids.size);Assert.assertEquals(repo.ids.first(),repo.ids.last());Assert.assertEquals(POSTED,vm.state.value.posted)}
private class Repo:StockManagementRepository{val ids=mutableListOf<String>();override suspend fun load(session:UserSession)=StockResult.Success(StockHistory(),"ok");override suspend fun adjust(session:UserSession,requestId:String,draft:StockAdjustmentDraft):StockResult<PostedAdjustment>{ids+=requestId;return if(ids.size==1)StockResult.Failure(RemoteFailure(RemoteErrorKind.TIMEOUT,RetryDisposition.WITH_BACKOFF),"timeout")else StockResult.Success(POSTED,"ok")}}
private class Products:ProductCatalogRepository{override fun observe(session:UserSession):Flow<List<CatalogProduct>> = flowOf(listOf(PRODUCT_ROW));override suspend fun refresh(session:UserSession)=ProductResult.Success("ok");override suspend fun mutate(session:UserSession,requestId:String,mutation:ProductMutation,draft:ProductDraft)=ProductResult.Success("ok")}
companion object{const val SHOP="11111111-1111-4111-8111-111111111111";const val PRODUCT="22222222-2222-4222-8222-222222222222";val OWNER=UserSession("33333333-3333-4333-8333-333333333333","Owner",UserRole.OWNER,SHOP);val DRAFT=StockAdjustmentDraft(PRODUCT,AdjustmentType.MANUAL_ADD,AdjustmentReason.STOCK_FOUND,1,null,100,"2026-07-28","found");val POSTED=PostedAdjustment("44444444-4444-4444-8444-444444444444",PRODUCT,"manual_add","stock_found",1,100,100,2,null,"55555555-5555-4555-8555-555555555555");val PRODUCT_ROW=CatalogProduct(PRODUCT,"Bag","B-1",null,1000,1,1,100,true)}}
