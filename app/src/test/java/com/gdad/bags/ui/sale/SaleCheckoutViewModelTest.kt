package com.gdad.bags.ui.sale
import com.gdad.bags.data.remote.*
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.product.*
import com.gdad.bags.domain.sale.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) class SaleCheckoutViewModelTest{private val d=StandardTestDispatcher();@Before fun b()=Dispatchers.setMain(d);@After fun a()=Dispatchers.resetMain();@Test fun retryReusesExactSaleKey()=runTest(d){val repo=Repo();val vm=SaleCheckoutViewModel(repo,Products());vm.activate(SESSION);advanceUntilIdle();vm.post(DRAFT);advanceUntilIdle();vm.retry();advanceUntilIdle();Assert.assertEquals(repo.ids.first(),repo.ids.last());Assert.assertEquals(POSTED,vm.state.value.posted)}
private class Repo:SaleCheckoutRepository{val ids=mutableListOf<String>();override suspend fun post(session:UserSession,requestId:String,draft:SaleDraft):SaleResult<PostedSale>{ids+=requestId;return if(ids.size==1)SaleResult.Failure(RemoteFailure(RemoteErrorKind.TIMEOUT,RetryDisposition.WITH_BACKOFF),"timeout")else SaleResult.Success(POSTED,"ok")}}
private class Products:ProductCatalogRepository{override fun observe(session:UserSession)=flowOf(listOf(PRODUCT_ROW));override suspend fun refresh(session:UserSession)=ProductResult.Success("ok");override suspend fun mutate(session:UserSession,requestId:String,mutation:ProductMutation,draft:ProductDraft)=ProductResult.Success("ok")}
companion object{const val SHOP="11111111-1111-4111-8111-111111111111";const val PID="22222222-2222-4222-8222-222222222222";val SESSION=UserSession("33333333-3333-4333-8333-333333333333","Sales",UserRole.SALESMAN,SHOP);val PRODUCT_ROW=CatalogProduct(PID,"Bag","B-1",null,1000,1,2,null,true);val DRAFT=SaleDraft("2026-07-28",listOf(SaleLineDraft(PID,"Bag",1,null)),payments=listOf(SalePaymentDraft(SalePaymentMethod.CASH,1000)));val POSTED=PostedSale("44444444-4444-4444-8444-444444444444",1000,1000,0,null,1,1)}}
