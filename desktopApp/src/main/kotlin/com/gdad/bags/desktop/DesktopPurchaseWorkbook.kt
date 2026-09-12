package com.gdad.bags.desktop

import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.domain.model.MoneyAmounts
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.domain.product.ProductResult
import com.gdad.bags.domain.purchase.PostedPurchase
import com.gdad.bags.domain.purchase.PurchaseDirectory
import com.gdad.bags.domain.purchase.PurchaseDraft
import com.gdad.bags.domain.purchase.PurchaseLineDraft
import com.gdad.bags.domain.purchase.PurchasePaymentMethod
import com.gdad.bags.domain.purchase.PurchaseResult
import com.gdad.bags.domain.purchase.VendorDraft
import com.gdad.bags.domain.purchase.VendorMutation
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory

private const val TEMPLATE_RESOURCE = "/templates/GDAD-BAGS-Purchase-Import-Template.xlsx"
private const val TEMPLATE_FILE_NAME = "GDAD-BAGS-Purchase-Import-Template.xlsx"
private const val TEMPLATE_MARKER = "GDAD_BAGS_PURCHASE_V1"
private const val MAX_WORKBOOK_BYTES = 5L * 1024 * 1024

data class ImportedPurchaseLine(
    val productName: String,
    val sku: String,
    val barcode: String?,
    val quantity: Int,
    val unitCostPaisa: Long,
    val suggestedSellingPricePaisa: Long,
    val minimumSellingPricePaisa: Long,
    val lowStockThreshold: Int,
)

data class ImportedPurchaseBill(
    val sourceFileName: String,
    val vendorName: String,
    val vendorPhone: String?,
    val vendorTaxReference: String?,
    val invoiceReference: String?,
    val businessDate: String,
    val paymentAmountPaisa: Long,
    val paymentMethod: PurchasePaymentMethod?,
    val lines: List<ImportedPurchaseLine>,
) {
    val totalPaisa: Long = requireNotNull(
        MoneyAmounts.sumPaisa(
            lines.map { Math.multiplyExact(it.quantity.toLong(), it.unitCostPaisa) },
        ),
    )
}

sealed interface PurchaseWorkbookResult {
    data class Success(val bill: ImportedPurchaseBill) : PurchaseWorkbookResult
    data class Failure(val safeMessage: String) : PurchaseWorkbookResult
}

data class PurchaseImportPreview(
    val bill: ImportedPurchaseBill,
    val existingProductCount: Int,
    val newProductCount: Int,
    val vendorWillBeCreated: Boolean,
    val blockingMessage: String?,
)

data class PurchaseImportRequestIds(
    val vendorRequestId: String,
    val productRequestIdsBySku: Map<String, String>,
    val purchaseRequestId: String,
) {
    companion object {
        fun create(bill: ImportedPurchaseBill) = PurchaseImportRequestIds(
            vendorRequestId = UUID.randomUUID().toString(),
            productRequestIdsBySku = bill.lines.associate { it.sku.normalizedSku() to UUID.randomUUID().toString() },
            purchaseRequestId = UUID.randomUUID().toString(),
        )
    }
}

sealed interface PurchaseImportExecutionResult {
    data class Success(
        val purchase: PostedPurchase,
        val createdProductCount: Int,
        val vendorCreated: Boolean,
    ) : PurchaseImportExecutionResult

    data class Failure(val safeMessage: String) : PurchaseImportExecutionResult
}

class DesktopPurchaseWorkbookParser {
    fun parse(path: Path): PurchaseWorkbookResult = runCatching { parseChecked(path) }
        .fold(
            onSuccess = PurchaseWorkbookResult::Success,
            onFailure = {
                PurchaseWorkbookResult.Failure(
                    it.message?.takeIf(String::isNotBlank)
                        ?: "The workbook could not be read safely. Use the GDAD BAGS purchase template.",
                )
            },
        )

    fun saveBlankTemplate(destination: Path) {
        require(destination.fileName.toString().lowercase(Locale.ROOT).endsWith(".xlsx")) {
            "Save the template with an .xlsx extension."
        }
        val resource = requireNotNull(javaClass.getResourceAsStream(TEMPLATE_RESOURCE)) {
            "The embedded purchase template is unavailable."
        }
        resource.use { source ->
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun defaultTemplateFileName(): String = TEMPLATE_FILE_NAME

    private fun parseChecked(path: Path): ImportedPurchaseBill {
        require(Files.isRegularFile(path)) { "Choose a valid Excel workbook." }
        require(path.fileName.toString().lowercase(Locale.ROOT).endsWith(".xlsx")) {
            "Only .xlsx purchase workbooks are accepted."
        }
        val size = Files.size(path)
        require(size in 1..MAX_WORKBOOK_BYTES) { "The purchase workbook must be 5 MB or smaller." }

        Files.newInputStream(path).use { input ->
            WorkbookFactory.create(input).use { workbook ->
                val sheet = requireNotNull(workbook.getSheet("Purchase Bill")) {
                    "The Purchase Bill worksheet is missing. Use the GDAD BAGS template."
                }
                require(text(sheet.getRow(0)?.getCell(9)) == TEMPLATE_MARKER) {
                    "This is not a supported GDAD BAGS purchase template."
                }

                val vendorName = text(sheet.getRow(4)?.getCell(1)).trim()
                val vendorPhone = text(sheet.getRow(5)?.getCell(1)).trim().ifEmpty { null }
                val vendorTax = text(sheet.getRow(6)?.getCell(1)).trim().ifEmpty { null }
                val invoice = text(sheet.getRow(7)?.getCell(1)).trim().ifEmpty { null }
                val businessDate = dateText(sheet.getRow(8)?.getCell(1))
                val payment = money(sheet.getRow(9)?.getCell(1), "payment amount", blankAsZero = true)
                val paymentLabel = text(sheet.getRow(10)?.getCell(1)).trim().lowercase(Locale.ROOT)
                val paymentMethod = when (paymentLabel) {
                    "", "none" -> null
                    "cash" -> PurchasePaymentMethod.CASH
                    "bank" -> PurchasePaymentMethod.BANK
                    else -> throw IllegalArgumentException("Payment method must be None, Cash, or Bank.")
                }

                require(vendorName.length in 1..160) { "Vendor name is required and must be 160 characters or fewer." }
                require(vendorPhone == null || vendorPhone.length <= 40) { "Vendor phone must be 40 characters or fewer." }
                require(vendorTax == null || vendorTax.length <= 80) { "Vendor tax reference must be 80 characters or fewer." }
                require(invoice == null || invoice.length <= 120) { "Invoice reference must be 120 characters or fewer." }
                require(com.gdad.bags.domain.model.NepalDateTime.isValidIsoDate(businessDate)) {
                    "Business date must use YYYY-MM-DD."
                }
                require((payment == 0L) == (paymentMethod == null)) {
                    "Use payment method None for zero payment, or choose Cash/Bank for a positive payment."
                }

                val lines = (14..113).mapNotNull { rowIndex ->
                    val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
                    val raw = (0..7).map { column -> text(row.getCell(column)).trim() }
                    if (raw.all(String::isEmpty)) return@mapNotNull null

                    val excelRow = rowIndex + 1
                    val name = raw[0]
                    val sku = raw[1]
                    val barcode = raw[2].ifEmpty { null }
                    require(name.length in 1..160) { "Row $excelRow: product name is required and must be 160 characters or fewer." }
                    require(sku.length in 1..64) { "Row $excelRow: SKU is required and must be 64 characters or fewer." }
                    require(barcode == null || barcode.length in 3..64) { "Row $excelRow: barcode must be 3–64 characters." }

                    ImportedPurchaseLine(
                        productName = name,
                        sku = sku,
                        barcode = barcode,
                        quantity = wholeNumber(row.getCell(3), "Row $excelRow quantity", minimum = 1),
                        unitCostPaisa = money(row.getCell(4), "Row $excelRow unit cost"),
                        suggestedSellingPricePaisa = money(row.getCell(5), "Row $excelRow suggested selling price"),
                        minimumSellingPricePaisa = money(row.getCell(6), "Row $excelRow minimum selling price"),
                        lowStockThreshold = wholeNumber(
                            row.getCell(7),
                            "Row $excelRow low-stock threshold",
                            minimum = 0,
                        ),
                    )
                }

                require(lines.size in 1..100) { "Enter between 1 and 100 complete product rows." }
                val duplicateSku = lines.groupBy { it.sku.normalizedSku() }.entries.firstOrNull { it.value.size > 1 }
                require(duplicateSku == null) { "Each SKU may appear only once in a purchase workbook." }
                require(lines.all { it.minimumSellingPricePaisa <= it.suggestedSellingPricePaisa }) {
                    "Minimum selling price cannot exceed the suggested selling price."
                }
                val total = MoneyAmounts.sumPaisa(
                    lines.map { Math.multiplyExact(it.quantity.toLong(), it.unitCostPaisa) },
                ) ?: throw IllegalArgumentException("Purchase line totals are too large.")
                require(payment <= total) { "Payment amount cannot exceed the purchase total." }

                return ImportedPurchaseBill(
                    sourceFileName = path.fileName.toString(),
                    vendorName = vendorName,
                    vendorPhone = vendorPhone,
                    vendorTaxReference = vendorTax,
                    invoiceReference = invoice,
                    businessDate = businessDate,
                    paymentAmountPaisa = payment,
                    paymentMethod = paymentMethod,
                    lines = lines,
                )
            }
        }
    }

    private fun text(cell: Cell?): String {
        if (cell == null || cell.cellType == CellType.BLANK) return ""
        require(cell.cellType != CellType.FORMULA) { "Formulas are not allowed in purchase input cells." }
        return DataFormatter(Locale.ROOT).formatCellValue(cell)
    }

    private fun dateText(cell: Cell?): String {
        if (cell != null && cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.localDateTimeCellValue.toLocalDate().toString()
        }
        return text(cell).trim()
    }

    private fun money(cell: Cell?, label: String, blankAsZero: Boolean = false): Long {
        if (cell == null || cell.cellType == CellType.BLANK || text(cell).isBlank()) {
            if (blankAsZero) return 0
            throw IllegalArgumentException("$label is required.")
        }
        val value = when (cell.cellType) {
            CellType.NUMERIC -> BigDecimal.valueOf(cell.numericCellValue)
            CellType.STRING -> cell.stringCellValue.trim().toBigDecimalOrNull()
                ?: throw IllegalArgumentException("$label must be a valid NPR amount.")
            else -> throw IllegalArgumentException("$label must be a value, not a formula or error.")
        }
        return runCatching {
            value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        }.getOrElse { throw IllegalArgumentException("$label must have no more than two decimal places.") }
            .also { require(it >= 0) { "$label cannot be negative." } }
    }

    private fun wholeNumber(cell: Cell?, label: String, minimum: Int): Int {
        val raw = when (cell?.cellType) {
            CellType.NUMERIC -> BigDecimal.valueOf(cell.numericCellValue)
            CellType.STRING -> cell.stringCellValue.trim().toBigDecimalOrNull()
            else -> null
        } ?: throw IllegalArgumentException("$label must be a whole number.")
        val value = runCatching { raw.setScale(0, RoundingMode.UNNECESSARY).intValueExact() }
            .getOrElse { throw IllegalArgumentException("$label must be a whole number.") }
        require(value >= minimum) { "$label must be at least $minimum." }
        return value
    }
}

class DesktopPurchaseImportService(
    private val purchases: DesktopPurchaseManagementRepository,
    private val products: DesktopProductCatalogRepository,
) {
    suspend fun execute(
        session: UserSession,
        bill: ImportedPurchaseBill,
        requestIds: PurchaseImportRequestIds,
    ): PurchaseImportExecutionResult {
        when (val refreshed = purchases.refresh(session)) {
            is PurchaseResult.Failure -> return PurchaseImportExecutionResult.Failure(refreshed.safeMessage)
            is PurchaseResult.Success -> Unit
        }
        when (val refreshed = products.refresh(session)) {
            is ProductResult.Failure -> return PurchaseImportExecutionResult.Failure(refreshed.safeMessage)
            is ProductResult.Success -> Unit
        }

        var vendorCreated = false
        val initialVendorMatches = purchases.snapshot().vendors.filter { it.name.equals(bill.vendorName, true) }
        if (initialVendorMatches.count { it.active } > 1) {
            return PurchaseImportExecutionResult.Failure("The workbook vendor matches multiple active vendors.")
        }
        var vendorId = initialVendorMatches.singleOrNull { it.active }?.id
        if (vendorId == null) {
            if (initialVendorMatches.any { !it.active }) {
                return PurchaseImportExecutionResult.Failure(
                    "The workbook vendor is archived. Reactivate or choose another vendor before importing.",
                )
            }
            when (
                val created = purchases.manageVendor(
                    session,
                    requestIds.vendorRequestId,
                    VendorMutation.CREATE,
                    VendorDraft(
                        vendorId = null,
                        name = bill.vendorName,
                        phone = bill.vendorPhone,
                        taxReference = bill.vendorTaxReference,
                        notes = "Created from ${bill.sourceFileName}",
                    ),
                )
            ) {
                is PurchaseResult.Failure -> return PurchaseImportExecutionResult.Failure(created.safeMessage)
                is PurchaseResult.Success -> Unit
            }
            vendorCreated = true
            vendorId = findActiveVendorId(bill.vendorName)
                ?: return PurchaseImportExecutionResult.Failure("The imported vendor could not be verified after creation.")
        }

        var createdProducts = 0
        val purchaseLines = mutableListOf<PurchaseLineDraft>()
        for (line in bill.lines) {
            val matching = products.snapshot().filter { it.sku.equals(line.sku, true) }
            if (matching.size > 1) {
                return PurchaseImportExecutionResult.Failure("SKU ${line.sku} matches multiple products. Resolve it before importing.")
            }
            if (matching.singleOrNull()?.active == false) {
                return PurchaseImportExecutionResult.Failure("SKU ${line.sku} is archived. Reactivate it before importing.")
            }
            var productId = matching.singleOrNull()?.id
            if (productId == null) {
                val requestId = requestIds.productRequestIdsBySku[line.sku.normalizedSku()]
                    ?: return PurchaseImportExecutionResult.Failure("The import request is incomplete. Reload the workbook.")
                when (
                    val created = products.mutate(
                        session,
                        requestId,
                        ProductMutation.CREATE,
                        ProductDraft(
                            productId = null,
                            name = line.productName,
                            sku = line.sku,
                            barcode = line.barcode,
                            sellingPricePaisa = line.suggestedSellingPricePaisa,
                            lowStockThreshold = line.lowStockThreshold,
                            minimumSellingPricePaisa = line.minimumSellingPricePaisa,
                        ),
                    )
                ) {
                    is ProductResult.Failure -> {
                        if (created.error?.kind == RemoteErrorKind.CONFLICT) products.refresh(session)
                        productId = products.snapshot().singleOrNull { it.sku.equals(line.sku, true) && it.active }?.id
                        if (productId == null) return PurchaseImportExecutionResult.Failure(created.safeMessage)
                    }
                    is ProductResult.Success -> {
                        createdProducts += 1
                        productId = products.snapshot().singleOrNull { it.sku.equals(line.sku, true) && it.active }?.id
                    }
                }
            }
            val verifiedProductId = productId
                ?: return PurchaseImportExecutionResult.Failure("SKU ${line.sku} could not be verified after creation.")
            purchaseLines += PurchaseLineDraft(
                productId = verifiedProductId,
                productName = line.productName,
                quantity = line.quantity,
                unitCostPaisa = line.unitCostPaisa,
            )
        }

        val draft = PurchaseDraft(
            vendorId = vendorId,
            invoiceReference = bill.invoiceReference,
            businessDate = bill.businessDate,
            lines = purchaseLines,
            paymentAmountPaisa = bill.paymentAmountPaisa,
            paymentMethod = bill.paymentMethod,
        )
        return when (val posted = purchases.postPurchase(session, requestIds.purchaseRequestId, draft)) {
            is PurchaseResult.Failure -> PurchaseImportExecutionResult.Failure(posted.safeMessage)
            is PurchaseResult.Success -> PurchaseImportExecutionResult.Success(
                purchase = posted.value,
                createdProductCount = createdProducts,
                vendorCreated = vendorCreated,
            )
        }
    }

    private fun findActiveVendorId(name: String): String? = purchases.snapshot().vendors
        .filter { it.active && it.name.equals(name, true) }
        .singleOrNull()
        ?.id
}

internal fun buildPurchaseImportPreview(
    bill: ImportedPurchaseBill,
    directory: PurchaseDirectory,
    products: List<CatalogProduct>,
): PurchaseImportPreview {
    val duplicateProduct = bill.lines.firstNotNullOfOrNull { line ->
        products.takeIf { list -> list.count { it.sku.equals(line.sku, true) } > 1 }
            ?.let { "SKU ${line.sku} matches multiple products." }
    }
    val archivedProduct = bill.lines.firstOrNull { line ->
        products.singleOrNull { it.sku.equals(line.sku, true) }?.active == false
    }
    val matchingVendors = directory.vendors.filter { it.name.equals(bill.vendorName, true) }
    val blocker = duplicateProduct ?: archivedProduct?.let { "SKU ${it.sku} is archived." }
        ?: when {
            matchingVendors.count { it.active } > 1 -> "The vendor name matches multiple active vendors."
            matchingVendors.none { it.active } && matchingVendors.isNotEmpty() -> "The vendor is archived."
            else -> null
        }
    val existing = bill.lines.count { line ->
        products.singleOrNull { it.sku.equals(line.sku, true) }?.active == true
    }
    return PurchaseImportPreview(
        bill = bill,
        existingProductCount = existing,
        newProductCount = bill.lines.size - existing,
        vendorWillBeCreated = matchingVendors.none { it.active },
        blockingMessage = blocker,
    )
}

private fun String.normalizedSku(): String = trim().lowercase(Locale.ROOT)
