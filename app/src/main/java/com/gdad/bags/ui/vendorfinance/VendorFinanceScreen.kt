package com.gdad.bags.ui.vendorfinance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.model.*
import com.gdad.bags.domain.purchase.Vendor
import com.gdad.bags.domain.vendorfinance.*
import com.gdad.bags.ui.components.BusinessDateField
import com.gdad.bags.ui.components.ContentStateHost
import com.gdad.bags.ui.components.StatusMessage
import java.time.LocalDate

@Composable
fun VendorFinanceScreen(
    session: UserSession,
    vendors: List<Vendor>,
    state: VendorFinanceUiState,
    onRefresh: () -> Unit,
    onPayment: (VendorPaymentDraft) -> Unit,
    onReturn: (VendorReturnDraft) -> Unit,
    onReverse: (VendorReversalDraft) -> Unit,
    onDismissReceipt: () -> Unit,
) {
    if (session.role != UserRole.OWNER) {
        ContentStateHost<Unit>(com.gdad.bags.ui.components.ContentState.Error("Owner vendor-finance access is required."), {}) { }
        return
    }
    var payingVendor by remember { mutableStateOf<String?>(null) }
    var returningBill by remember { mutableStateOf<VendorBill?>(null) }
    var reversing by remember { mutableStateOf<Pair<VendorEventType, String>?>(null) }
    val names = vendors.associate { it.id to it.name }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.safeMessage?.let { StatusMessage(it) }
        ContentStateHost(state.content, onRefresh) { ledger ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Vendor bills and dues", style = MaterialTheme.typography.headlineSmall) }
                items(ledger.bills, key = { "bill-${it.id}" }) { bill ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(names[bill.vendorId] ?: "Vendor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${bill.businessDate} • ${bill.status.humanize()}")
                        bill.invoiceReference?.let { Text("Invoice $it") }
                        Text("Total ${money(bill.grandTotalPaisa)} • Due ${money(bill.duePaisa)}")
                        bill.lines.forEach { Text("${it.productName}: ${it.returnableQuantity} returnable @ ${money(it.unitCostPaisa)}") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (bill.duePaisa > 0) Button(onClick = { payingVendor = bill.vendorId }, enabled = !state.isMutating) { Text("Pay bills") }
                            if (bill.duePaisa > 0 && bill.lines.any { it.returnableQuantity > 0 } && bill.status in setOf("received", "partially_returned")) OutlinedButton(onClick = { returningBill = bill }, enabled = !state.isMutating) { Text("Return stock") }
                        }
                    } }
                }
                item { Text("Payments and returns", style = MaterialTheme.typography.headlineSmall) }
                items(ledger.payments, key = { "payment-${it.id}" }) { event ->
                    EventCard("Payment", names[event.vendorId] ?: "Vendor", event.status, event.businessDate, "${event.method.humanize()} • ${money(event.amountPaisa)}", event.reversalReason) {
                        reversing = VendorEventType.PAYMENT to event.id
                    }
                }
                items(ledger.returns, key = { "return-${it.id}" }) { event ->
                    EventCard("Vendor return", names[event.vendorId] ?: "Vendor", event.status, event.businessDate, "${money(event.totalPaisa)} • ${event.reason}", event.reversalReason) {
                        reversing = VendorEventType.RETURN to event.id
                    }
                }
            }
        }
    }
    val ledger = (state.content as? com.gdad.bags.ui.components.ContentState.Ready)?.value
    payingVendor?.let { vendorId -> ledger?.let { PaymentDialog(vendorId, names[vendorId] ?: "Vendor", it.bills.filter { bill -> bill.vendorId == vendorId && bill.duePaisa > 0 }, { payingVendor = null }) { payingVendor = null; onPayment(it) } } }
    returningBill?.let { bill -> ReturnDialog(bill, { returningBill = null }) { returningBill = null; onReturn(it) } }
    reversing?.let { event -> ReversalDialog(event.first, event.second, { reversing = null }) { reversing = null; onReverse(it) } }
    state.receipt?.let { ReceiptDialog(it, onDismissReceipt) }
}

@Composable private fun EventCard(kind:String,vendor:String,status:String,date:String,detail:String,reversalReason:String?,onReverse:()->Unit){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text("$kind • $vendor",fontWeight=FontWeight.Bold);Text("$date • ${status.humanize()}");Text(detail);reversalReason?.let{Text("Reversal reason: $it")};if(status=="posted")TextButton(onClick=onReverse){Text("Reverse event")}}}}

@Composable private fun PaymentDialog(vendorId:String,name:String,bills:List<VendorBill>,onDismiss:()->Unit,onSubmit:(VendorPaymentDraft)->Unit){
    val amounts=remember{mutableStateMapOf<String,String>()};var date by remember{mutableStateOf(NepalDateTime.todayIso())};var method by remember{mutableStateOf(VendorPaymentMethod.CASH)}
    val allocations=bills.mapNotNull{bill->val paisa=amounts[bill.id]?.let{MoneyAmounts.parsePaisa(it,1)}?:0;if(paisa>0)VendorPaymentAllocationDraft(bill.id,paisa)else null};val paymentTotal=MoneyAmounts.sumPaisa(allocations.map{it.amountPaisa});val valid=allocations.isNotEmpty()&&paymentTotal!=null&&allocations.all{a->a.amountPaisa<=bills.single{it.id==a.billId}.duePaisa}&&runCatching{LocalDate.parse(date)}.isSuccess
    AlertDialog(onDismissRequest=onDismiss,title={Text("Pay $name")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){bills.forEach{bill->Text("${bill.invoiceReference?:bill.id.take(8)} • Due ${money(bill.duePaisa)}");OutlinedTextField(amounts[bill.id].orEmpty(),{amounts[bill.id]=it.filter{c->c.isDigit()||c=='.'}},label={Text("Allocate payment")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))};BusinessDateField(date,{date=it});Row{VendorPaymentMethod.entries.forEach{value->TextButton(onClick={method=value}){Text((if(method==value)"Selected: " else "")+value.name.humanize())}}};Text("Payment total ${money(paymentTotal?:0)}; server due is final.")}},confirmButton={Button(enabled=valid,onClick={onSubmit(VendorPaymentDraft(vendorId,method,date,allocations))}){Text("Post payment once")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun ReturnDialog(bill:VendorBill,onDismiss:()->Unit,onSubmit:(VendorReturnDraft)->Unit){val quantities=remember{mutableStateMapOf<String,String>()};var date by remember{mutableStateOf(NepalDateTime.todayIso())};var reason by remember{mutableStateOf("")};val lines=bill.lines.mapNotNull{line->val q=quantities[line.id]?.toIntOrNull()?:0;if(q>0)VendorReturnLineDraft(line.id,q)else null};val lineValues=lines.map{draft->val line=bill.lines.single{it.id==draft.receiptLineId};MoneyAmounts.multiplyPaisa(line.unitCostPaisa,draft.quantity)};val value=lineValues.takeIf{it.all{amount->amount!=null}}?.let{MoneyAmounts.sumPaisa(it.filterNotNull())};val valid=lines.isNotEmpty()&&value!=null&&lines.all{d->d.quantity<=bill.lines.single{it.id==d.receiptLineId}.returnableQuantity}&&value<=bill.duePaisa&&reason.trim().isNotEmpty()&&runCatching{LocalDate.parse(date)}.isSuccess;AlertDialog(onDismissRequest=onDismiss,title={Text("Return purchase stock")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("Bill due ${money(bill.duePaisa)}");bill.lines.filter{it.returnableQuantity>0}.forEach{line->Text("${line.productName} • ${line.returnableQuantity} available");OutlinedTextField(quantities[line.id].orEmpty(),{quantities[line.id]=it.filter(Char::isDigit)},label={Text("Return quantity")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))};BusinessDateField(date,{date=it});OutlinedTextField(reason,{reason=it.take(500)},label={Text("Required reason")});Text("Estimated value ${money(value?:0)}; server value and due are final.")}},confirmButton={Button(enabled=valid,onClick={onSubmit(VendorReturnDraft(bill.id,date,reason.trim(),lines))}){Text("Post return once")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}

@Composable private fun ReversalDialog(type:VendorEventType,id:String,onDismiss:()->Unit,onSubmit:(VendorReversalDraft)->Unit){var date by remember{mutableStateOf(NepalDateTime.todayIso())};var reason by remember{mutableStateOf("")};val valid=reason.trim().isNotEmpty()&&runCatching{LocalDate.parse(date)}.isSuccess;AlertDialog(onDismissRequest=onDismiss,title={Text("Reverse ${type.name.humanize()}")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text("Posted records are preserved; the server creates a compensating journal.");BusinessDateField(date,{date=it});OutlinedTextField(reason,{reason=it.take(500)},label={Text("Required reversal reason")})}},confirmButton={Button(enabled=valid,onClick={onSubmit(VendorReversalDraft(type,id,date,reason.trim()))}){Text("Reverse once")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}

@Composable private fun ReceiptDialog(receipt:VendorFinanceReceipt,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text("Vendor operation posted")},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text("Server-authoritative result");when(receipt){is VendorFinanceReceipt.Payment->{Text("Payment ${money(receipt.value.amountPaisa)}");Text("Vendor due ${money(receipt.value.vendorDueAfterPaisa)}");Text("${receipt.value.allocationCount} bill allocation(s)")};is VendorFinanceReceipt.Return->{Text("Return value ${money(receipt.value.returnValuePaisa)}");Text("Bill due ${money(receipt.value.billDueAfterPaisa)}");Text("${receipt.value.lineCount} returned line(s)")};is VendorFinanceReceipt.Reversal->{Text("${receipt.value.eventType.humanize()} reversed");Text("Event ${receipt.value.eventId}");receipt.value.reversalJournalId?.let{Text("Reversal journal $it")}}}}},confirmButton={Button(onClick=onDismiss){Text("Done")}})}
private fun String.humanize()=lowercase().replace('_',' ').replaceFirstChar(Char::uppercase)
private fun money(paisa:Long)=MoneyAmounts.formatNpr(paisa)
