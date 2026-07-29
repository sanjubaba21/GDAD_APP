package com.gdad.bags.data.finance
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.*
import com.gdad.bags.domain.finance.*
import com.gdad.bags.domain.model.*
import java.util.UUID
class ProductionFinanceRepository(private val remote:FinanceRemoteDataSource):FinanceRepository{
 override suspend fun load(session:UserSession)=if(!session.owner())denied()else remote.load(session.cache()).result("Unable to refresh finance data.")
 override suspend fun postExpense(session:UserSession,requestId:String,draft:ExpenseDraft):FinanceResult<PostedExpense>{if(!session.owner())return denied();if(!requestId.uuid()||!draft.valid())return invalid();return remote.expense(session.cache(),requestId,draft).result("Unable to post the expense.")}
 override suspend fun postMovement(session:UserSession,requestId:String,draft:CashMovementDraft):FinanceResult<PostedCashMovement>{if(!session.owner())return denied();if(!requestId.uuid()||!draft.valid())return invalid();return remote.movement(session.cache(),requestId,draft).result("Unable to post the cash movement.")}
 override suspend fun postTransfer(session:UserSession,requestId:String,draft:TransferDraft):FinanceResult<PostedTransfer>{if(!session.owner())return denied();if(!requestId.uuid()||!draft.valid())return invalid();return remote.transfer(session.cache(),requestId,draft).result("Unable to post the transfer.")}
 override suspend fun reverse(session:UserSession,requestId:String,draft:FinancialReversalDraft):FinanceResult<PostedFinancialReversal>{if(!session.owner())return denied();if(!requestId.uuid()||!draft.valid())return invalid();return remote.reverse(session.cache(),requestId,draft).result("Unable to reverse the financial operation.")}
 private fun ExpenseDraft.valid()=sourceAccountId.uuid()&&amountPaisa>0&&date(businessDate)&&category.trim().length in 1..120&&(payee==null||payee.trim().length in 1..200)&&(note==null||note.trim().length in 1..500)
 private fun CashMovementDraft.valid()=accountId.uuid()&&amountPaisa>0&&date(businessDate)&&description.trim().length in 1..500
 private fun TransferDraft.valid()=fromAccountId.uuid()&&toAccountId.uuid()&&fromAccountId!=toAccountId&&amountPaisa>0&&date(businessDate)&&description.trim().length in 1..500
 private fun FinancialReversalDraft.valid()=journalId.uuid()&&date(businessDate)&&reason.trim().length in 1..500
 private fun date(v:String)=runCatching{kotlinx.datetime.LocalDate.parse(v)}.isSuccess
 private fun<T>RemoteResult<T>.result(default:String)=when(this){is RemoteResult.Success->FinanceResult.Success(value,"Financial operation completed with authoritative balances.");is RemoteResult.Failure->FinanceResult.Failure(error,when(error.kind){RemoteErrorKind.UNAUTHORIZED->"This Owner finance operation is not allowed.";RemoteErrorKind.VALIDATION->"Review the account, amount, date, description, and reason.";RemoteErrorKind.CONFLICT->"An accounting resource changed. Refresh and review.";RemoteErrorKind.OFFLINE->"Financial operations require an internet connection.";RemoteErrorKind.TIMEOUT->"The request timed out. Retry the same operation safely.";RemoteErrorKind.RATE_LIMITED->"Too many attempts. Wait before retrying.";RemoteErrorKind.UNKNOWN->default})}
 private fun denied()=FinanceResult.Failure(null,"Owner finance access is required.");private fun invalid()=FinanceResult.Failure(null,"Review the account, amount, date, description, and reason.");private fun UserSession.owner()=role==UserRole.OWNER&&shopId.uuid();private fun UserSession.cache()=CacheOwner(userId,shopId);private fun String?.uuid()=this!=null&&runCatching{UUID.fromString(this)}.isSuccess
}
