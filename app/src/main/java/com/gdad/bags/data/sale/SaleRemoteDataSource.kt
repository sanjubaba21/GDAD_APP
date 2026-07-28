package com.gdad.bags.data.sale
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.*
import com.gdad.bags.domain.sale.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
interface SaleRemoteDataSource{suspend fun post(owner:CacheOwner,requestId:String,draft:SaleDraft):RemoteResult<PostedSale>}
class SupabaseSaleRemoteDataSource(private val client:SupabaseClient,private val calls:RemoteCallExecutor):SaleRemoteDataSource{
override suspend fun post(owner:CacheOwner,requestId:String,draft:SaleDraft)=calls.execute(RemoteOperation.POST_FIFO_SALE,true){client.postgrest.rpc("post_fifo_sale",JsonObject(mapOf(
"p_idempotency_key" to JsonPrimitive(requestId),"p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),"p_business_date" to JsonPrimitive(draft.businessDate),
"p_lines" to JsonArray(draft.lines.map{JsonObject(mapOf("product_id" to JsonPrimitive(it.productId),"quantity" to JsonPrimitive(it.quantity),"effective_unit_price_paisa" to (it.effectiveUnitPricePaisa?.let(::JsonPrimitive)?:JsonNull),"line_discount_paisa" to JsonPrimitive(it.lineDiscountPaisa)))}),
"p_sale_discount_paisa" to JsonPrimitive(draft.saleDiscountPaisa),"p_is_credit" to JsonPrimitive(draft.isCredit),"p_customer_name" to (draft.customerName?.let(::JsonPrimitive)?:JsonNull),"p_customer_contact" to (draft.customerContact?.let(::JsonPrimitive)?:JsonNull),"p_due_date" to (draft.dueDate?.let(::JsonPrimitive)?:JsonNull),
"p_payments" to JsonArray(draft.payments.map{JsonObject(mapOf("method" to JsonPrimitive(it.method.name.lowercase()),"amount_paisa" to JsonPrimitive(it.amountPaisa)))})
))).decodeAs<SaleRow>().domain()}}
@Serializable private data class SaleRow(@SerialName("sale_id")val id:String,@SerialName("grand_total_paisa")val total:Long,@SerialName("paid_paisa")val paid:Long,@SerialName("due_paisa")val due:Long,@SerialName("cost_total_paisa")val cost:Long?=null,@SerialName("line_count")val lines:Int,@SerialName("allocation_count")val allocations:Int){fun domain()=PostedSale(id,total,paid,due,cost,lines,allocations)}
