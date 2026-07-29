package com.gdad.bags.data.stock

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.*
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.stock.*
import java.util.UUID

class ProductionStockManagementRepository(private val remote:StockRemoteDataSource,private val products:ProductCatalogRepository):StockManagementRepository{
    override suspend fun load(session:UserSession):StockResult<StockHistory>{
        if(session.role==UserRole.SUPER_ADMIN||!session.shopId.isUuid())return denied()
        if(session.role==UserRole.SALESMAN)return StockResult.Success(StockHistory(),"Stock summary loaded without cost history.")
        return when(val r=remote.load(session.owner())){is RemoteResult.Success->StockResult.Success(r.value,"Stock history refreshed.");is RemoteResult.Failure->r.error.failure("Unable to refresh stock history.")}
    }
    override suspend fun adjust(session:UserSession,requestId:String,draft:StockAdjustmentDraft):StockResult<PostedAdjustment>{
        if(session.role!=UserRole.OWNER||!session.shopId.isUuid())return denied()
        if(!requestId.isUuid()||!draft.valid())return invalid()
        return when(val r=remote.adjust(session.owner(),requestId,draft)){is RemoteResult.Failure->r.error.failure("Unable to post the stock adjustment.");is RemoteResult.Success->{products.refresh(session);StockResult.Success(r.value,"Stock adjustment posted and audited.")}}
    }
    private fun StockAdjustmentDraft.valid():Boolean{
        if(!productId.isUuid()||quantity<=0||runCatching{kotlinx.datetime.LocalDate.parse(businessDate)}.isFailure||note?.trim()?.length?.let{it !in 1..1000}==true)return false
        val reasons=when(type){AdjustmentType.MANUAL_ADD->setOf(AdjustmentReason.STOCK_FOUND,AdjustmentReason.OPENING_BALANCE,AdjustmentReason.DATA_CORRECTION);AdjustmentType.MANUAL_REMOVE->setOf(AdjustmentReason.COUNT_SHORTAGE,AdjustmentReason.DATA_CORRECTION);AdjustmentType.DAMAGE->setOf(AdjustmentReason.DAMAGED);AdjustmentType.LOSS->setOf(AdjustmentReason.LOST)}
        val addCost = unitCostPaisa
        return reason in reasons&&if(type==AdjustmentType.MANUAL_ADD)sourceLotId==null&&addCost!=null&&addCost>=0&&runCatching{Math.multiplyExact(addCost,quantity.toLong())}.isSuccess else sourceLotId.isUuid()&&unitCostPaisa==null
    }
    private fun RemoteFailure.failure(default:String)=StockResult.Failure(this,when(kind){RemoteErrorKind.UNAUTHORIZED->"This Owner stock operation is not allowed.";RemoteErrorKind.VALIDATION->"Review the reason, quantity, lot, cost, and business date.";RemoteErrorKind.CONFLICT->"The lot has insufficient stock or the accounting period/resource is unavailable.";RemoteErrorKind.OFFLINE->"Stock adjustments require an internet connection.";RemoteErrorKind.TIMEOUT->"The request timed out. Retry the same adjustment safely.";RemoteErrorKind.RATE_LIMITED->"Too many attempts. Wait before retrying.";RemoteErrorKind.UNKNOWN->default})
    private fun denied()=StockResult.Failure(null,"This stock operation is not allowed.");private fun invalid()=StockResult.Failure(null,"Review the reason, quantity, lot, cost, and business date.")
    private fun UserSession.owner()=CacheOwner(userId,shopId);private fun String?.isUuid()=this!=null&&runCatching{UUID.fromString(this)}.isSuccess
}
