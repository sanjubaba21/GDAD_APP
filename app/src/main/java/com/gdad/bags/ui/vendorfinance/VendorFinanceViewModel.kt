package com.gdad.bags.ui.vendorfinance

import androidx.lifecycle.*
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.vendorfinance.*
import com.gdad.bags.ui.components.ContentState
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface VendorFinanceReceipt {
    data class Payment(val value: PostedVendorPayment) : VendorFinanceReceipt
    data class Return(val value: PostedVendorReturn) : VendorFinanceReceipt
    data class Reversal(val value: PostedVendorReversal) : VendorFinanceReceipt
}
data class VendorFinanceUiState(
    val content: ContentState<VendorLedger> = ContentState.Loading,
    val isMutating: Boolean = false,
    val safeMessage: String? = null,
    val receipt: VendorFinanceReceipt? = null,
)
private sealed interface PendingVendorFinance {
    val id: String
    data class Payment(override val id:String,val draft:VendorPaymentDraft):PendingVendorFinance
    data class Return(override val id:String,val draft:VendorReturnDraft):PendingVendorFinance
    data class Reversal(override val id:String,val draft:VendorReversalDraft):PendingVendorFinance
}

class VendorFinanceViewModel(private val repository:VendorFinanceRepository):ViewModel(){
    private val mutable=MutableStateFlow(VendorFinanceUiState());val state:StateFlow<VendorFinanceUiState> = mutable.asStateFlow()
    private var session:UserSession?=null;private var pending:PendingVendorFinance?=null
    fun activate(active:UserSession?){if(session==active)return;session=active;pending=null;if(active==null||active.role!=UserRole.OWNER){mutable.value=VendorFinanceUiState(content=ContentState.Empty("Owner vendor-finance access is required."));return};mutable.value=VendorFinanceUiState();refresh()}
    fun refresh(){val active=session?:return;viewModelScope.launch{when(val result=repository.load(active)){is VendorFinanceResult.Success->mutable.update{it.copy(content=if(result.value.bills.isEmpty()&&result.value.payments.isEmpty()&&result.value.returns.isEmpty())ContentState.Empty("No vendor bills or financial events yet.")else ContentState.Ready(result.value),safeMessage=result.safeMessage)};is VendorFinanceResult.Failure->mutable.update{it.copy(content=ContentState.Error(result.safeMessage))}}}}
    fun postPayment(draft:VendorPaymentDraft){start(PendingVendorFinance.Payment(UUID.randomUUID().toString(),draft))}
    fun postReturn(draft:VendorReturnDraft){start(PendingVendorFinance.Return(UUID.randomUUID().toString(),draft))}
    fun reverse(draft:VendorReversalDraft){start(PendingVendorFinance.Reversal(UUID.randomUUID().toString(),draft))}
    fun retry(){if(pending==null)refresh()else execute()};fun dismissReceipt()=mutable.update{it.copy(receipt=null)}
    private fun start(operation:PendingVendorFinance){if(mutable.value.isMutating)return;pending=operation;execute()}
    private fun execute(){val active=session?:return;val operation=pending?:return;if(mutable.value.isMutating)return;mutable.update{it.copy(isMutating=true,safeMessage=null)};viewModelScope.launch{
        val result=when(operation){is PendingVendorFinance.Payment->repository.postPayment(active,operation.id,operation.draft);is PendingVendorFinance.Return->repository.postReturn(active,operation.id,operation.draft);is PendingVendorFinance.Reversal->repository.reverse(active,operation.id,operation.draft)}
        when(result){is VendorFinanceResult.Failure->{if(result.error?.kind in setOf(RemoteErrorKind.VALIDATION,RemoteErrorKind.CONFLICT)){val loaded=repository.load(active);if(loaded is VendorFinanceResult.Success)mutable.update{it.copy(content=ContentState.Ready(loaded.value))}};mutable.update{it.copy(isMutating=false,safeMessage=result.safeMessage)}};is VendorFinanceResult.Success<*>->{pending=null;val loaded=repository.load(active);val content=if(loaded is VendorFinanceResult.Success)ContentState.Ready(loaded.value)else mutable.value.content;val receipt=when(val value=result.value){is PostedVendorPayment->VendorFinanceReceipt.Payment(value);is PostedVendorReturn->VendorFinanceReceipt.Return(value);is PostedVendorReversal->VendorFinanceReceipt.Reversal(value);else->null};mutable.update{it.copy(content=content,isMutating=false,safeMessage=result.safeMessage,receipt=receipt)}}}
    }}
    class Factory(private val repository:VendorFinanceRepository):ViewModelProvider.Factory{override fun<T:ViewModel>create(modelClass:Class<T>):T{require(modelClass.isAssignableFrom(VendorFinanceViewModel::class.java));@Suppress("UNCHECKED_CAST")return VendorFinanceViewModel(repository)as T}}
}
