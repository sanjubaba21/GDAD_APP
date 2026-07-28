package com.gdad.bags.ui.sale
import androidx.lifecycle.*
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.product.*
import com.gdad.bags.domain.sale.*
import com.gdad.bags.ui.components.ContentState
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
data class SaleUiState(val content:ContentState<List<CatalogProduct>> = ContentState.Loading,val isPosting:Boolean=false,val safeMessage:String?=null,val posted:PostedSale?=null)
private data class PendingSale(val id:String,val draft:SaleDraft)
class SaleCheckoutViewModel(private val repository:SaleCheckoutRepository,private val products:ProductCatalogRepository):ViewModel(){private val mutable=MutableStateFlow(SaleUiState());val state:StateFlow<SaleUiState> = mutable.asStateFlow();private var session:UserSession?=null;private var job:Job?=null;private var pending:PendingSale?=null
fun activate(active:UserSession?){if(session==active)return;session=active;job?.cancel();pending=null;if(active==null||active.role==UserRole.SUPER_ADMIN){mutable.value=SaleUiState(content=ContentState.Empty("No sales access."));return};job=viewModelScope.launch{products.observe(active).collect{rows->val activeRows=rows.filter{it.active&&it.quantityOnHand>0};mutable.update{it.copy(content=if(activeRows.isEmpty())ContentState.Empty("No products are in stock.")else ContentState.Ready(activeRows))}}};refresh()}
fun refresh(){session?.let{active->viewModelScope.launch{val r=products.refresh(active);if(r is ProductResult.Failure)mutable.update{it.copy(content=ContentState.Error(r.safeMessage))}}}}
fun post(draft:SaleDraft){if(mutable.value.isPosting)return;pending=PendingSale(UUID.randomUUID().toString(),draft);execute()};fun retry()=if(pending==null)refresh()else execute();fun dismiss()=mutable.update{it.copy(posted=null)}
private fun execute(){val active=session?:return;val op=pending?:return;if(mutable.value.isPosting)return;mutable.update{it.copy(isPosting=true,safeMessage=null)};viewModelScope.launch{when(val r=repository.post(active,op.id,op.draft)){is SaleResult.Failure->mutable.update{it.copy(isPosting=false,safeMessage=r.safeMessage)};is SaleResult.Success->{pending=null;mutable.update{it.copy(isPosting=false,safeMessage=r.safeMessage,posted=r.value)}}}}}
class Factory(private val repository:SaleCheckoutRepository,private val products:ProductCatalogRepository):ViewModelProvider.Factory{override fun<T:ViewModel>create(modelClass:Class<T>):T{require(modelClass.isAssignableFrom(SaleCheckoutViewModel::class.java));@Suppress("UNCHECKED_CAST")return SaleCheckoutViewModel(repository,products)as T}}}
