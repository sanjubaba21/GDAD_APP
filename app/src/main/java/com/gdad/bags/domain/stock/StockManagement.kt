package com.gdad.bags.domain.stock

import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserSession

data class StockLot(val id:String,val productId:String,val sourceType:String,val receivedAt:String,val unitCostPaisa:Long,val originalQuantity:Int,val remainingQuantity:Int)
data class StockMovement(val id:String,val productId:String,val lotId:String?,val type:String,val quantityDelta:Int,val unitCostPaisa:Long?,val reason:String?,val businessDate:String,val occurredAt:String)
data class StockHistory(val lots:List<StockLot> = emptyList(),val movements:List<StockMovement> = emptyList())
enum class AdjustmentType { MANUAL_ADD, MANUAL_REMOVE, DAMAGE, LOSS }
enum class AdjustmentReason { DAMAGED, LOST, COUNT_SHORTAGE, STOCK_FOUND, OPENING_BALANCE, DATA_CORRECTION }
data class StockAdjustmentDraft(val productId:String,val type:AdjustmentType,val reason:AdjustmentReason,val quantity:Int,val sourceLotId:String?,val unitCostPaisa:Long?,val businessDate:String,val note:String?)
data class PostedAdjustment(val adjustmentId:String,val productId:String,val movementType:String,val reasonCode:String,val quantityDelta:Int,val unitCostPaisa:Long,val totalCostPaisa:Long,val stockAfter:Long,val sourceLotId:String?,val createdLotId:String?)
sealed interface StockResult<out T>{data class Success<T>(val value:T,val safeMessage:String):StockResult<T>;data class Failure(val error:RemoteFailure?,val safeMessage:String):StockResult<Nothing>}
interface StockManagementRepository { suspend fun load(session:UserSession):StockResult<StockHistory>; suspend fun adjust(session:UserSession,requestId:String,draft:StockAdjustmentDraft):StockResult<PostedAdjustment> }
