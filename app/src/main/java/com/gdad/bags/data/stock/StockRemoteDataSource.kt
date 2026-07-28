package com.gdad.bags.data.stock

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.*
import com.gdad.bags.domain.stock.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

interface StockRemoteDataSource { suspend fun load(owner:CacheOwner):RemoteResult<StockHistory>; suspend fun adjust(owner:CacheOwner,requestId:String,draft:StockAdjustmentDraft):RemoteResult<PostedAdjustment> }
class SupabaseStockRemoteDataSource(private val client:SupabaseClient,private val calls:RemoteCallExecutor):StockRemoteDataSource{
    override suspend fun load(owner:CacheOwner)=calls.execute(RemoteOperation.LOAD_STOCK_HISTORY,true){
        val lots=client.from("inventory_lots").select(Columns.raw("id,product_id,source_type,received_at,unit_cost_paisa,original_quantity,remaining_quantity")).decodeList<LotRow>()
        val movements=client.from("inventory_movements").select(Columns.raw("id,product_id,lot_id,movement_type,quantity_delta,unit_cost_paisa,reason,business_date,occurred_at")).decodeList<MovementRow>()
        StockHistory(lots.map{it.domain()},movements.map{it.domain()})
    }
    override suspend fun adjust(owner:CacheOwner,requestId:String,draft:StockAdjustmentDraft)=calls.execute(RemoteOperation.POST_INVENTORY_ADJUSTMENT,true){
        client.postgrest.rpc("post_inventory_adjustment",JsonObject(mapOf(
            "p_idempotency_key" to JsonPrimitive(requestId),"p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),"p_product_id" to JsonPrimitive(draft.productId),
            "p_movement_type" to JsonPrimitive(draft.type.name.lowercase()),"p_reason_code" to JsonPrimitive(draft.reason.name.lowercase()),"p_quantity" to JsonPrimitive(draft.quantity),
            "p_source_lot_id" to (draft.sourceLotId?.let(::JsonPrimitive)?:JsonNull),"p_unit_cost_paisa" to (draft.unitCostPaisa?.let(::JsonPrimitive)?:JsonNull),
            "p_business_date" to JsonPrimitive(draft.businessDate),"p_note" to (draft.note?.let(::JsonPrimitive)?:JsonNull)
        ))).decodeAs<AdjustmentRow>().domain()
    }
}
@Serializable private data class LotRow(val id:String,@SerialName("product_id")val productId:String,@SerialName("source_type")val sourceType:String,@SerialName("received_at")val receivedAt:String,@SerialName("unit_cost_paisa")val unitCost:Long,@SerialName("original_quantity")val original:Int,@SerialName("remaining_quantity")val remaining:Int){fun domain()=StockLot(id,productId,sourceType,receivedAt,unitCost,original,remaining)}
@Serializable private data class MovementRow(val id:String,@SerialName("product_id")val productId:String,@SerialName("lot_id")val lotId:String?,@SerialName("movement_type")val type:String,@SerialName("quantity_delta")val delta:Int,@SerialName("unit_cost_paisa")val cost:Long?,val reason:String?,@SerialName("business_date")val date:String,@SerialName("occurred_at")val occurred:String){fun domain()=StockMovement(id,productId,lotId,type,delta,cost,reason,date,occurred)}
@Serializable private data class AdjustmentRow(@SerialName("inventory_adjustment_id")val id:String,@SerialName("product_id")val productId:String,@SerialName("movement_type")val type:String,@SerialName("reason_code")val reason:String,@SerialName("quantity_delta")val delta:Int,@SerialName("unit_cost_paisa")val cost:Long,@SerialName("total_cost_paisa")val total:Long,@SerialName("stock_after")val stock:Long,@SerialName("source_lot_id")val source:String?,@SerialName("created_lot_id")val created:String?){fun domain()=PostedAdjustment(id,productId,type,reason,delta,cost,total,stock,source,created)}
