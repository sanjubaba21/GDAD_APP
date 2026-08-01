package com.gdad.bags.data.finance
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.*
import com.gdad.bags.domain.finance.*
import com.gdad.bags.domain.model.MoneyAmounts
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
interface FinanceRemoteDataSource{suspend fun load(owner:CacheOwner):RemoteResult<FinanceLedger>;suspend fun expense(owner:CacheOwner,id:String,draft:ExpenseDraft):RemoteResult<PostedExpense>;suspend fun movement(owner:CacheOwner,id:String,draft:CashMovementDraft):RemoteResult<PostedCashMovement>;suspend fun transfer(owner:CacheOwner,id:String,draft:TransferDraft):RemoteResult<PostedTransfer>;suspend fun reverse(owner:CacheOwner,id:String,draft:FinancialReversalDraft):RemoteResult<PostedFinancialReversal>}
class SupabaseFinanceRemoteDataSource(private val client:SupabaseClient,private val calls:RemoteCallExecutor):FinanceRemoteDataSource{
 override suspend fun load(owner:CacheOwner)=calls.execute(RemoteOperation.LOAD_FINANCE_LEDGER,true){
  val accounts=client.from("financial_accounts").select(Columns.raw("id,display_name,account_type,active")){
   limit(RemoteQueryWindow.REQUEST_ROWS);order("id",Order.ASCENDING)
  }.decodeList<AccountRow>().requireSupportedWindow("financial accounts")
  val tx=client.from("journal_transactions").select(Columns.raw("id,kind,description,business_date,occurred_at,reversal_of_id")){
   limit(RemoteQueryWindow.REQUEST_ROWS);order("id",Order.ASCENDING)
  }.decodeList<TransactionRow>().requireSupportedWindow("journal transactions")
  val entries=client.from("journal_entries").select(Columns.raw("journal_transaction_id,financial_account_id,debit_paisa,credit_paisa")){
   limit(RemoteQueryWindow.REQUEST_ROWS);order("journal_transaction_id",Order.ASCENDING);order("line_number",Order.ASCENDING)
  }.decodeList<EntryRow>().requireSupportedWindow("journal entries")
  val expenses=client.from("expenses").select(Columns.raw("id,journal_transaction_id,category,payee,note,amount_paisa")){
   limit(RemoteQueryWindow.REQUEST_ROWS);order("id",Order.ASCENDING)
  }.decodeList<ExpenseRow>().requireSupportedWindow("expenses")
  val entriesByAccount=entries.groupBy{it.accountId}
  val entriesByJournal=entries.groupBy{it.journalId}
  val reversals=tx.mapNotNull{it.reversalOf}.toSet()
  FinanceLedger(
   accounts.filter{it.type in setOf("cash","bank")}.map{a->
    val effects=entriesByAccount[a.id].orEmpty().map{Math.subtractExact(it.debit,it.credit)}
    FinanceAccount(a.id,a.name,a.type,requireNotNull(MoneyAmounts.sumPaisa(effects)),a.active)
   },
   tx.map{t->FinanceTransaction(t.id,t.kind,t.description,t.date,t.occurred,t.reversalOf,t.id in reversals,entriesByJournal[t.id].orEmpty().map{AccountEffect(it.accountId,it.debit,it.credit)})}.sortedByDescending{it.occurredAt},
   expenses.map{ExpenseDetail(it.id,it.journalId,it.category,it.payee,it.note,it.amount)},
  )
 }
 override suspend fun expense(owner:CacheOwner,id:String,draft:ExpenseDraft)=calls.execute(RemoteOperation.POST_EXPENSE,true){client.postgrest.rpc("post_expense",JsonObject(mapOf("p_idempotency_key" to JsonPrimitive(id),"p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),"p_source_account_id" to JsonPrimitive(draft.sourceAccountId),"p_amount_paisa" to JsonPrimitive(draft.amountPaisa),"p_business_date" to JsonPrimitive(draft.businessDate),"p_category" to JsonPrimitive(draft.category),"p_payee" to draft.payee.json(),"p_note" to draft.note.json()))).decodeAs<ExpenseResultRow>().domain()}
 override suspend fun movement(owner:CacheOwner,id:String,draft:CashMovementDraft)=calls.execute(RemoteOperation.POST_CASH_MOVEMENT,true){client.postgrest.rpc("post_cash_movement",JsonObject(mapOf("p_idempotency_key" to JsonPrimitive(id),"p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),"p_movement_type" to JsonPrimitive(draft.type.name.lowercase()),"p_account_id" to JsonPrimitive(draft.accountId),"p_amount_paisa" to JsonPrimitive(draft.amountPaisa),"p_business_date" to JsonPrimitive(draft.businessDate),"p_description" to JsonPrimitive(draft.description)))).decodeAs<MovementResultRow>().domain()}
 override suspend fun transfer(owner:CacheOwner,id:String,draft:TransferDraft)=calls.execute(RemoteOperation.POST_ACCOUNT_TRANSFER,true){client.postgrest.rpc("post_account_transfer",JsonObject(mapOf("p_idempotency_key" to JsonPrimitive(id),"p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),"p_from_account_id" to JsonPrimitive(draft.fromAccountId),"p_to_account_id" to JsonPrimitive(draft.toAccountId),"p_amount_paisa" to JsonPrimitive(draft.amountPaisa),"p_business_date" to JsonPrimitive(draft.businessDate),"p_description" to JsonPrimitive(draft.description)))).decodeAs<TransferResultRow>().domain()}
 override suspend fun reverse(owner:CacheOwner,id:String,draft:FinancialReversalDraft)=calls.execute(RemoteOperation.REVERSE_FINANCIAL_OPERATION,true){client.postgrest.rpc("reverse_financial_operation",JsonObject(mapOf("p_idempotency_key" to JsonPrimitive(id),"p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),"p_journal_transaction_id" to JsonPrimitive(draft.journalId),"p_business_date" to JsonPrimitive(draft.businessDate),"p_reason" to JsonPrimitive(draft.reason)))).decodeAs<ReversalResultRow>().domain()}}
private fun String?.json():JsonElement=this?.let(::JsonPrimitive)?:JsonNull
@Serializable private data class AccountRow(val id:String,@SerialName("display_name")val name:String,@SerialName("account_type")val type:String,val active:Boolean)
@Serializable private data class TransactionRow(val id:String,val kind:String,val description:String,@SerialName("business_date")val date:String,@SerialName("occurred_at")val occurred:String,@SerialName("reversal_of_id")val reversalOf:String?)
@Serializable private data class EntryRow(@SerialName("journal_transaction_id")val journalId:String,@SerialName("financial_account_id")val accountId:String,@SerialName("debit_paisa")val debit:Long,@SerialName("credit_paisa")val credit:Long)
@Serializable private data class ExpenseRow(val id:String,@SerialName("journal_transaction_id")val journalId:String,val category:String,val payee:String?,val note:String?,@SerialName("amount_paisa")val amount:Long)
@Serializable private data class ExpenseResultRow(@SerialName("expense_id")val id:String,@SerialName("journal_transaction_id")val journal:String,@SerialName("amount_paisa")val amount:Long,@SerialName("source_balance_after_paisa")val balance:Long){fun domain()=PostedExpense(id,journal,amount,balance)}
@Serializable private data class MovementResultRow(@SerialName("movement_type")val type:String,@SerialName("journal_transaction_id")val journal:String,@SerialName("amount_paisa")val amount:Long,@SerialName("account_balance_after_paisa")val balance:Long){fun domain()=PostedCashMovement(type,journal,amount,balance)}
@Serializable private data class TransferResultRow(@SerialName("journal_transaction_id")val journal:String,@SerialName("amount_paisa")val amount:Long,@SerialName("from_balance_after_paisa")val from:Long,@SerialName("to_balance_after_paisa")val to:Long){fun domain()=PostedTransfer(journal,amount,from,to)}
@Serializable private data class ReversalResultRow(@SerialName("journal_transaction_id")val journal:String,@SerialName("reversal_journal_id")val reversal:String,@SerialName("original_kind")val kind:String){fun domain()=PostedFinancialReversal(journal,reversal,kind)}
