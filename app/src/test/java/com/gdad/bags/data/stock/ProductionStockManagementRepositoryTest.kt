package com.gdad.bags.data.stock

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.*
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.product.*
import com.gdad.bags.domain.stock.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProductionStockManagementRepositoryTest {
    @Test fun salesmanGetsNoCostHistoryAndCannotAdjust()=runBlocking{val remote=FakeRemote();val repo=ProductionStockManagementRepository(remote,FakeProducts());assertTrue(repo.load(OWNER.copy(role=UserRole.SALESMAN)) is StockResult.Success);assertEquals(0,remote.loads);assertTrue(repo.adjust(OWNER.copy(role=UserRole.SALESMAN),REQUEST,DRAFT) is StockResult.Failure);assertTrue(remote.ids.isEmpty())}
    @Test fun ownerLoadsLotsMovementsAndRefreshesAfterAdjustment()=runBlocking{val remote=FakeRemote();val products=FakeProducts();val repo=ProductionStockManagementRepository(remote,products);val loaded=repo.load(OWNER) as StockResult.Success;assertEquals(1,loaded.value.lots.size);val posted=repo.adjust(OWNER,REQUEST,DRAFT) as StockResult.Success;assertEquals(9,posted.value.stockAfter);assertEquals(1,products.refreshes)}
    @Test fun invalidOrExcessiveInputIsActionable()=runBlocking{val remote=FakeRemote();val repo=ProductionStockManagementRepository(remote,FakeProducts());assertTrue(repo.adjust(OWNER,REQUEST,DRAFT.copy(quantity=0)) is StockResult.Failure);remote.result=RemoteResult.Failure(RemoteFailure(RemoteErrorKind.VALIDATION,RetryDisposition.NEVER));val result=repo.adjust(OWNER,REQUEST,DRAFT) as StockResult.Failure;assertTrue(result.safeMessage.contains("quantity"));assertEquals(listOf(REQUEST),remote.ids)}
    @Test fun overflowingAddedStockValueNeverReachesRemote()=runBlocking{val remote=FakeRemote();val repo=ProductionStockManagementRepository(remote,FakeProducts());assertTrue(repo.adjust(OWNER,REQUEST,DRAFT.copy(quantity=2,unitCostPaisa=Long.MAX_VALUE)) is StockResult.Failure);assertTrue(remote.ids.isEmpty())}
    private class FakeRemote:StockRemoteDataSource{var loads=0;val ids=mutableListOf<String>();var result:RemoteResult<PostedAdjustment> = RemoteResult.Success(POSTED);override suspend fun load(owner:CacheOwner):RemoteResult<StockHistory>{loads++;return RemoteResult.Success(HISTORY)};override suspend fun adjust(owner:CacheOwner,requestId:String,draft:StockAdjustmentDraft):RemoteResult<PostedAdjustment>{ids+=requestId;return result}}
    private class FakeProducts:ProductCatalogRepository{var refreshes=0;override fun observe(session:UserSession):Flow<List<CatalogProduct>> = flowOf(emptyList());override suspend fun refresh(session:UserSession):ProductResult{refreshes++;return ProductResult.Success("ok")};override suspend fun mutate(session:UserSession,requestId:String,mutation:ProductMutation,draft:ProductDraft)=ProductResult.Success("ok")}
    companion object{const val SHOP="11111111-1111-4111-8111-111111111111";const val PRODUCT="22222222-2222-4222-8222-222222222222";const val LOT="33333333-3333-4333-8333-333333333333";const val REQUEST="44444444-4444-4444-8444-444444444444";val OWNER=UserSession("55555555-5555-4555-8555-555555555555","Owner",UserRole.OWNER,SHOP);val DRAFT=StockAdjustmentDraft(PRODUCT,AdjustmentType.MANUAL_ADD,AdjustmentReason.STOCK_FOUND,2,null,5000,"2026-07-28","counted");val POSTED=PostedAdjustment("66666666-6666-4666-8666-666666666666",PRODUCT,"manual_add","stock_found",2,5000,10000,9,null,LOT);val HISTORY=StockHistory(listOf(StockLot(LOT,PRODUCT,"purchase_receipt","2026-07-28T00:00:00Z",5000,10,7)),listOf(StockMovement("77777777-7777-4777-8777-777777777777",PRODUCT,LOT,"purchase",10,5000,null,"2026-07-28","2026-07-28T00:00:00Z")))}
}
