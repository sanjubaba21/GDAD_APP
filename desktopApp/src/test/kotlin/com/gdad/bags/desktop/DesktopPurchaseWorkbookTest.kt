package com.gdad.bags.desktop

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.CachedProductEntity
import com.gdad.bags.data.local.CachedStockSummaryEntity
import com.gdad.bags.data.local.CachedVendorEntity
import com.gdad.bags.data.product.ProductRemoteDataSource
import com.gdad.bags.data.product.ProductRemoteSnapshot
import com.gdad.bags.data.purchase.PurchaseRemoteDataSource
import com.gdad.bags.data.purchase.PurchaseRemoteSnapshot
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.domain.purchase.PostedPurchase
import com.gdad.bags.domain.purchase.PurchaseDraft
import com.gdad.bags.domain.purchase.PurchasePaymentMethod
import com.gdad.bags.domain.purchase.PurchaseResult
import com.gdad.bags.domain.purchase.Vendor
import com.gdad.bags.domain.purchase.VendorDraft
import com.gdad.bags.domain.purchase.VendorMutation
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.apache.poi.ss.usermodel.WorkbookFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DesktopPurchaseWorkbookTest {
    private val parser = DesktopPurchaseWorkbookParser()

    @Test
    fun officialTemplateParsesCompleteBillWithoutRetyping() {
        val workbook = validWorkbook()
        try {
            val result = assertIs<PurchaseWorkbookResult.Success>(parser.parse(workbook))

            assertEquals("Everest Bags", result.bill.vendorName)
            assertEquals("INV-2026-100", result.bill.invoiceReference)
            assertEquals("2026-09-10", result.bill.businessDate)
            assertEquals(500_00, result.bill.paymentAmountPaisa)
            assertEquals(PurchasePaymentMethod.CASH, result.bill.paymentMethod)
            assertEquals(1, result.bill.lines.size)
            assertEquals("Travel Bag", result.bill.lines.single().productName)
            assertEquals("TB-100", result.bill.lines.single().sku)
            assertEquals(3, result.bill.lines.single().quantity)
            assertEquals(450_00, result.bill.lines.single().unitCostPaisa)
            assertEquals(799_00, result.bill.lines.single().suggestedSellingPricePaisa)
            assertEquals(600_00, result.bill.lines.single().minimumSellingPricePaisa)
            assertEquals(2, result.bill.lines.single().lowStockThreshold)
            assertEquals(1_350_00, result.bill.totalPaisa)
        } finally {
            Files.deleteIfExists(workbook)
        }
    }

    @Test
    fun duplicateSkuIsRejectedBeforeAnyHostedChange() {
        val workbook = validWorkbook()
        try {
            WorkbookFactory.create(workbook.toFile()).use { book ->
                val sheet = book.getSheet("Purchase Bill")
                val source = sheet.getRow(14)
                val duplicate = sheet.getRow(15)
                for (column in 0..7) {
                    duplicate.getCell(column).setCellValue(source.getCell(column).toString())
                }
                Files.newOutputStream(workbook).use(book::write)
            }

            val result = assertIs<PurchaseWorkbookResult.Failure>(parser.parse(workbook))
            assertEquals("Each SKU may appear only once in a purchase workbook.", result.safeMessage)
        } finally {
            Files.deleteIfExists(workbook)
        }
    }

    @Test
    fun previewIdentifiesExistingAndNewProducts() {
        val existing = product(UUID.randomUUID().toString(), "TB-100")
        val bill = importedBill(
            listOf(
                line("Travel Bag", "TB-100"),
                line("Laptop Bag", "LB-200"),
            ),
        )

        val preview = buildPurchaseImportPreview(
            bill,
            com.gdad.bags.domain.purchase.PurchaseDirectory(
                vendors = listOf(Vendor(UUID.randomUUID().toString(), "Everest Bags", null, null, null, 0, true)),
            ),
            listOf(existing),
        )

        assertEquals(1, preview.existingProductCount)
        assertEquals(1, preview.newProductCount)
        assertEquals(false, preview.vendorWillBeCreated)
        assertNull(preview.blockingMessage)
    }

    @Test
    fun importCreatesMissingMasterDataThenPostsOneAuthoritativeBill() = runBlocking {
        val shopId = UUID.randomUUID().toString()
        val productRemote = StatefulImportProductRemote(shopId)
        val productRepository = DesktopProductCatalogRepository(productRemote)
        val purchaseRemote = StatefulImportPurchaseRemote(shopId)
        val purchaseRepository = DesktopPurchaseManagementRepository(purchaseRemote, productRepository)
        val service = DesktopPurchaseImportService(purchaseRepository, productRepository)
        val bill = importedBill(listOf(line("Travel Bag", "TB-100")))
        val requestIds = PurchaseImportRequestIds.create(bill)

        val result = service.execute(owner(shopId), bill, requestIds)

        val success = assertIs<PurchaseImportExecutionResult.Success>(result)
        assertEquals(true, success.vendorCreated)
        assertEquals(1, success.createdProductCount)
        assertEquals(1, purchaseRemote.postCount)
        assertEquals(requestIds.vendorRequestId, purchaseRemote.vendorRequestId)
        assertEquals(requestIds.productRequestIdsBySku.getValue("tb-100"), productRemote.productRequestId)
        assertEquals(requestIds.purchaseRequestId, purchaseRemote.purchaseRequestId)
        assertEquals(productRemote.productId, purchaseRemote.lastPurchase?.lines?.single()?.productId)
        assertEquals(purchaseRemote.vendorId, purchaseRemote.lastPurchase?.vendorId)
        assertEquals(799_00, productRemote.createdDraft?.sellingPricePaisa)
        assertEquals(600_00, productRemote.createdDraft?.minimumSellingPricePaisa)
        assertEquals(2, productRemote.createdDraft?.lowStockThreshold)
    }

    private fun validWorkbook(): Path {
        val output = Files.createTempFile("gdad-purchase-", ".xlsx")
        parser.saveBlankTemplate(output)
        WorkbookFactory.create(output.toFile()).use { book ->
            val sheet = book.getSheet("Purchase Bill")
            sheet.getRow(4).getCell(1).setCellValue("Everest Bags")
            sheet.getRow(5).getCell(1).setCellValue("9800000000")
            sheet.getRow(6).getCell(1).setCellValue("PAN-100")
            sheet.getRow(7).getCell(1).setCellValue("INV-2026-100")
            sheet.getRow(8).getCell(1).setCellValue("2026-09-10")
            sheet.getRow(9).getCell(1).setCellValue(500.0)
            sheet.getRow(10).getCell(1).setCellValue("Cash")
            val row = sheet.getRow(14)
            row.getCell(0).setCellValue("Travel Bag")
            row.getCell(1).setCellValue("TB-100")
            row.getCell(2).setCellValue("123456789")
            row.getCell(3).setCellValue(3.0)
            row.getCell(4).setCellValue(450.0)
            row.getCell(5).setCellValue(799.0)
            row.getCell(6).setCellValue(600.0)
            row.getCell(7).setCellValue(2.0)
            Files.newOutputStream(output).use(book::write)
        }
        return output
    }

    private fun importedBill(lines: List<ImportedPurchaseLine>) = ImportedPurchaseBill(
        sourceFileName = "purchase.xlsx",
        vendorName = "Everest Bags",
        vendorPhone = "9800000000",
        vendorTaxReference = null,
        invoiceReference = "INV-100",
        businessDate = "2026-09-10",
        paymentAmountPaisa = 0,
        paymentMethod = null,
        lines = lines,
    )

    private fun line(name: String, sku: String) = ImportedPurchaseLine(
        productName = name,
        sku = sku,
        barcode = null,
        quantity = 2,
        unitCostPaisa = 450_00,
        suggestedSellingPricePaisa = 799_00,
        minimumSellingPricePaisa = 600_00,
        lowStockThreshold = 2,
    )

    private fun product(id: String, sku: String) = CatalogProduct(
        id, "Travel Bag", sku, null, 799_00, 2, 0, 0, true,
    )

    private fun owner(shopId: String) = UserSession(
        UUID.randomUUID().toString(), "Owner", UserRole.OWNER, shopId,
    )
}

private class StatefulImportProductRemote(private val shopId: String) : ProductRemoteDataSource {
    private val products = mutableListOf<CachedProductEntity>()
    var productRequestId: String? = null
    var createdDraft: ProductDraft? = null
    var productId: String? = null

    override suspend fun load(owner: CacheOwner, canSeeCost: Boolean) = RemoteResult.Success(
        ProductRemoteSnapshot(
            products.toList(),
            products.map {
                CachedStockSummaryEntity(owner.userId, shopId, it.id, 0, 0, true, 1)
            },
        ),
    )

    override suspend fun mutate(
        owner: CacheOwner,
        requestId: String,
        mutation: ProductMutation,
        draft: ProductDraft,
    ): RemoteResult<Unit> {
        productRequestId = requestId
        createdDraft = draft
        val id = productId ?: UUID.randomUUID().toString().also { productId = it }
        if (products.none { it.id == id }) {
            products += CachedProductEntity(
                owner.userId, shopId, id, draft.name, draft.sku, draft.barcode,
                draft.sellingPricePaisa, draft.lowStockThreshold, true, 1,
                draft.minimumSellingPricePaisa,
            )
        }
        return RemoteResult.Success(Unit)
    }
}

private class StatefulImportPurchaseRemote(private val shopId: String) : PurchaseRemoteDataSource {
    private val vendors = mutableListOf<CachedVendorEntity>()
    var vendorRequestId: String? = null
    var purchaseRequestId: String? = null
    var vendorId: String? = null
    var lastPurchase: PurchaseDraft? = null
    var postCount = 0

    override suspend fun load(owner: CacheOwner) = RemoteResult.Success(
        PurchaseRemoteSnapshot(vendors.toList(), emptyList()),
    )

    override suspend fun manageVendor(
        owner: CacheOwner,
        requestId: String,
        mutation: VendorMutation,
        draft: VendorDraft,
    ): RemoteResult<Unit> {
        vendorRequestId = requestId
        val id = vendorId ?: UUID.randomUUID().toString().also { vendorId = it }
        if (vendors.none { it.id == id }) {
            vendors += CachedVendorEntity(
                owner.userId, shopId, id, draft.name, draft.phone, draft.taxReference,
                draft.notes, 0, true,
            )
        }
        return RemoteResult.Success(Unit)
    }

    override suspend fun postPurchase(
        owner: CacheOwner,
        requestId: String,
        draft: PurchaseDraft,
    ): RemoteResult<PostedPurchase> {
        purchaseRequestId = requestId
        lastPurchase = draft
        postCount += 1
        val total = draft.lines.sumOf { it.lineTotalPaisa }
        return RemoteResult.Success(
            PostedPurchase("bill", "receipt", null, total, draft.paymentAmountPaisa, total, draft.lines.size),
        )
    }
}
