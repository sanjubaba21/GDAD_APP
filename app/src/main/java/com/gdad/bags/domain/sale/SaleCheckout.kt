package com.gdad.bags.domain.sale
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserSession
enum class SalePaymentMethod{CASH,BANK}
data class SaleLineDraft(val productId:String,val productName:String,val quantity:Int,val effectiveUnitPricePaisa:Long?,val lineDiscountPaisa:Long=0)
data class SalePaymentDraft(val method:SalePaymentMethod,val amountPaisa:Long)
data class SaleDraft(val businessDate:String,val lines:List<SaleLineDraft>,val saleDiscountPaisa:Long=0,val isCredit:Boolean=false,val customerName:String?=null,val customerContact:String?=null,val dueDate:String?=null,val payments:List<SalePaymentDraft>)
data class PostedSale(val saleId:String,val grandTotalPaisa:Long,val paidPaisa:Long,val duePaisa:Long,val costTotalPaisa:Long?,val lineCount:Int,val allocationCount:Int)
sealed interface SaleResult<out T>{data class Success<T>(val value:T,val safeMessage:String):SaleResult<T>;data class Failure(val error:RemoteFailure?,val safeMessage:String):SaleResult<Nothing>}
interface SaleCheckoutRepository{suspend fun post(session:UserSession,requestId:String,draft:SaleDraft):SaleResult<PostedSale>}
