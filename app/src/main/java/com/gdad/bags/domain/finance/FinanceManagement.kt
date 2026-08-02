package com.gdad.bags.domain.finance
import com.gdad.bags.data.remote.RemoteFailure
import com.gdad.bags.domain.model.UserSession
data class FinanceAccount(val id:String,val name:String,val type:String,val balancePaisa:Long,val active:Boolean)
data class AccountEffect(val accountId:String,val debitPaisa:Long,val creditPaisa:Long)
data class FinanceTransaction(val id:String,val kind:String,val description:String,val businessDate:String,val occurredAt:String,val reversalOfId:String?,val reversed:Boolean,val effects:List<AccountEffect>)
data class ExpenseDetail(val id:String,val journalId:String,val category:String,val payee:String?,val note:String?,val amountPaisa:Long)
data class FinanceLedger(val accounts:List<FinanceAccount> = emptyList(),val transactions:List<FinanceTransaction> = emptyList(),val expenses:List<ExpenseDetail> = emptyList())
data class ExpenseDraft(val sourceAccountId:String,val amountPaisa:Long,val businessDate:String,val category:String,val payee:String?,val note:String?)
enum class CashMovementType{DEPOSIT,WITHDRAWAL}
data class CashMovementDraft(val type:CashMovementType,val accountId:String,val amountPaisa:Long,val businessDate:String,val description:String)
data class TransferDraft(val fromAccountId:String,val toAccountId:String,val amountPaisa:Long,val businessDate:String,val description:String)
data class FinancialReversalDraft(val journalId:String,val businessDate:String,val reason:String)
data class PostedExpense(val expenseId:String,val journalId:String,val amountPaisa:Long,val sourceBalanceAfterPaisa:Long)
data class PostedCashMovement(val type:String,val journalId:String,val amountPaisa:Long,val accountBalanceAfterPaisa:Long)
data class PostedTransfer(val journalId:String,val amountPaisa:Long,val fromBalanceAfterPaisa:Long,val toBalanceAfterPaisa:Long)
data class PostedFinancialReversal(val journalId:String,val reversalJournalId:String,val originalKind:String)
sealed interface FinanceResult<out T>{data class Success<T>(val value:T,val safeMessage:String):FinanceResult<T>;data class Failure(val error:RemoteFailure?,val safeMessage:String):FinanceResult<Nothing>}
interface FinanceRepository{suspend fun load(session:UserSession):FinanceResult<FinanceLedger>;suspend fun postExpense(session:UserSession,requestId:String,draft:ExpenseDraft):FinanceResult<PostedExpense>;suspend fun postMovement(session:UserSession,requestId:String,draft:CashMovementDraft):FinanceResult<PostedCashMovement>;suspend fun postTransfer(session:UserSession,requestId:String,draft:TransferDraft):FinanceResult<PostedTransfer>;suspend fun reverse(session:UserSession,requestId:String,draft:FinancialReversalDraft):FinanceResult<PostedFinancialReversal>}
