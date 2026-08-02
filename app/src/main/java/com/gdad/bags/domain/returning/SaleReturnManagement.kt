package com.gdad.bags.domain.returning
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserSession
data class SaleAllocation(val lotId:String,val quantity:Int,val unitCostPaisa:Long)
data class SaleHistoryLine(val id:String,val productId:String,val productName:String,val sku:String,val quantity:Int,val unitPricePaisa:Long,val lineTotalPaisa:Long,val returnedQuantity:Int,val returnedValuePaisa:Long,val allocations:List<SaleAllocation>){val returnableQuantity:Int get()=(quantity-returnedQuantity).coerceAtLeast(0)}
data class SaleHistoryEntry(val id:String,val status:String,val isCredit:Boolean,val customerName:String?,val customerContact:String?,val businessDate:String,val occurredAt:String,val grandTotalPaisa:Long,val paidPaisa:Long,val returnedPaisa:Long,val refundedPaisa:Long,val duePaisa:Long,val lines:List<SaleHistoryLine>)
data class SaleHistory(val sales:List<SaleHistoryEntry> = emptyList())
enum class ReturnDisposition{SELLABLE,DAMAGED};enum class RefundMethod{CASH,BANK}
data class ReturnLineDraft(val saleLineId:String,val quantity:Int,val disposition:ReturnDisposition)
data class SaleReturnDraft(val saleId:String,val businessDate:String,val reason:String,val lines:List<ReturnLineDraft>,val refundMethod:RefundMethod?)
data class PostedSaleReturn(val returnId:String,val saleId:String,val returnValuePaisa:Long,val refundPaisa:Long,val dueAfterPaisa:Long,val restoredQuantity:Int,val restoredCostPaisa:Long,val saleStatus:String)
sealed interface ReturnResult<out T>{data class Success<T>(val value:T,val safeMessage:String):ReturnResult<T>;data class Failure(val error:RemoteFailure?,val safeMessage:String):ReturnResult<Nothing>}
interface SaleReturnRepository{suspend fun load(session:UserSession):ReturnResult<SaleHistory>;suspend fun post(session:UserSession,requestId:String,draft:SaleReturnDraft):ReturnResult<PostedSaleReturn>}
