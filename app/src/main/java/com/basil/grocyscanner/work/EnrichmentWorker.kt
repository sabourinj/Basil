package com.basil.grocyscanner.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.basil.grocyscanner.data.GeminiConfig
import com.basil.grocyscanner.data.GrocyApi
import com.basil.grocyscanner.data.GrocyLocation
import com.basil.grocyscanner.data.GrocyProductGroup
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.basil.grocyscanner.data.NetworkClient
import com.basil.grocyscanner.data.ProductDetails
import com.basil.grocyscanner.data.SecurePrefs
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

// Fills in what a shopping-mode purchase skipped so the scan never waited on
// it: product details for a brand-new product, the price, the best-before
// date, and the generic parent product. Everything comes from one Gemini call.
//
// The parent rules match the nightly parent Apps Script, so the two agree:
// only products with no parent that track their own stock get one; the parent
// is matched by case-insensitive name, or created as a no-own-stock,
// hidden-from-overview product flagged with the parent_product userfield.
private data class Lookups(
    val product: ProductDetails,
    val locations: List<GrocyLocation>,
    val groups: List<GrocyProductGroup>,
    val products: List<ProductDetails>
)

class EnrichmentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val productId = inputData.getInt(KEY_PRODUCT_ID, -1)
        if (productId < 0) return Result.failure()
        val isNew = inputData.getBoolean(KEY_IS_NEW, false)
        val needsPrice = inputData.getBoolean(KEY_NEEDS_PRICE, false)
        val needsBestBefore = inputData.getBoolean(KEY_NEEDS_BEST_BEFORE, false)
        val stockId = inputData.getString(KEY_STOCK_ID)

        val prefs = SecurePrefs.get(applicationContext)
        val url = prefs.getString("API_URL", null)
        val token = prefs.getString("API_TOKEN", null)
        if (url.isNullOrBlank() || token.isNullOrBlank()) return Result.failure()
        val geminiKey = prefs.getString("GEMINI_KEY", null)
            ?.takeIf { prefs.getBoolean("AI_ENABLED", false) && it.isNotBlank() }
            ?: return Result.success() // AI is off: nothing to fill in

        val api = NetworkClient.create(url, token)

        return try {
            enrich(api, geminiKey, GeminiConfig.model(prefs), productId, isNew, needsPrice, needsBestBefore, stockId)
            Result.success()
        } catch (e: IOException) {
            retryOrGiveUp(e)
        } catch (e: HttpException) {
            if (e.code() >= 500) retryOrGiveUp(e) else {
                Log.e(TAG, "Enrichment refused by Grocy: HTTP ${e.code()}")
                Result.failure()
            }
        } catch (e: Exception) {
            // Gemini errors land here (quota, a bad reply). Worth a couple of tries.
            retryOrGiveUp(e)
        }
    }

    private fun retryOrGiveUp(e: Exception): Result {
        Log.w(TAG, "Enrichment attempt ${runAttemptCount + 1} failed: ${e.message}")
        return if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
    }

    private suspend fun enrich(
        api: GrocyApi,
        geminiKey: String,
        modelName: String,
        productId: Int,
        isNew: Boolean,
        needsPrice: Boolean,
        needsBestBefore: Boolean,
        stockId: String?
    ) {
        // All four lookups at once; the open purchase sheet is waiting on this job.
        // The product list is fetched once and reused for parent matching: jobs
        // run one at a time, so a parent an earlier job created is already in it.
        val (product, locations, groups, allProducts) = coroutineScope {
            val product = async { api.getProductById(productId).product }
            val locations = async { api.getLocations() }
            val groups = async { api.getProductGroups() }
            val products = async { api.getProducts() }
            Lookups(product.await(), locations.await(), groups.await(), products.await())
        }
        val needsParent = product.parent_product_id == null && (product.no_own_stock ?: 0) == 0
        val needsShelfLife = isNew || (needsBestBefore && product.default_best_before_days <= 0)

        if (!needsParent && !needsPrice && !needsShelfLife) return

        val locationIds = locations.associate { it.name.lowercase() to it.id }
        val groupIds = groups.associate { it.name.lowercase() to it.id }
        val wantsPlacement = isNew || needsParent

        val existingParents = if (needsParent) parentNames(allProducts) else emptyList()

        val prompt = buildPrompt(
            productName = product.name,
            parent = needsParent,
            placement = wantsPlacement,
            shelfLife = needsShelfLife,
            price = needsPrice,
            locationNames = locations.map { it.name },
            groupNames = groups.map { it.name },
            existingParents = existingParents
        )

        val model = GenerativeModel(
            modelName = modelName,
            apiKey = geminiKey,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.0f
            }
        )
        val text = model.generateContent(prompt).text ?: throw IllegalStateException("Empty Gemini reply")
        val json = JSONObject(text)

        val locationId = json.optString("location").lowercase().let { locationIds[it] }
        val groupId = json.optString("group").lowercase().let { groupIds[it] }

        // 1. Details for a product created from this scan
        var shelfLifeDays: Int? = null
        if (needsShelfLife) {
            val days = json.optInt("default_best_before_days", 0)
            shelfLifeDays = days.takeIf { it > 0 }
            if (isNew) {
                val update = mutableMapOf<String, Any>(
                    "default_best_before_days" to days,
                    "default_best_before_days_after_open" to json.optInt("default_best_before_days_after_open", 0),
                    "default_best_before_days_after_freezing" to json.optInt("default_best_before_days_after_freezing", 0),
                    "default_best_before_days_after_thawing" to json.optInt("default_best_before_days_after_thawing", 0),
                    "should_not_be_frozen" to json.optInt("should_not_be_frozen", 0)
                )
                groupId?.let { update["product_group_id"] = it }
                locationId?.let { update["location_id"] = it }
                api.updateProduct(productId, update)
            }
        } else if (needsBestBefore && product.default_best_before_days > 0) {
            shelfLifeDays = product.default_best_before_days
        }

        // 2. Parent product
        if (needsParent) {
            val parentName = json.optString("name").trim()
            if (parentName.isNotEmpty()) {
                linkParent(api, product, allProducts, parentName, locationId ?: product.location_id, groupId)
            }
        }

        // 3. Price and best-before on the stock entry the purchase created
        if (stockId != null) {
            val price = if (needsPrice) json.optDouble("price", 0.0).takeIf { it > 0.0 } else null
            // A product group that doesn't track expiry keeps no date.
            val finalGroupId = if (isNew) groupId ?: product.product_group_id else product.product_group_id
            val strategy = groups.find { it.id == finalGroupId }?.userfields?.expiration_strategy ?: "ai_estimate"
            val days = if (needsBestBefore && strategy != "not_required" && strategy.isNotEmpty()) shelfLifeDays else null
            if (price != null || days != null) {
                EnrichmentScheduler.enqueueStockEntryFix(
                    WorkManager.getInstance(applicationContext), productId, stockId, price, days
                )
            }
        }
    }

    private suspend fun linkParent(
        api: GrocyApi,
        child: ProductDetails,
        allProducts: List<ProductDetails>,
        parentName: String,
        locationId: Int?,
        groupId: Int?
    ) {
        var parentId = allProducts.firstOrNull { it.name.equals(parentName, ignoreCase = true) }?.id

        if (parentId == null) {
            val payload = mutableMapOf<String, Any>(
                "name" to parentName,
                "description" to "Automated generic parent product",
                "no_own_stock" to 1,
                "hide_on_stock_overview" to 1
            )
            locationId?.let { payload["location_id"] = it }
            groupId?.let { payload["product_group_id"] = it }
            child.qu_id_purchase?.let { payload["qu_id_purchase"] = it }
            child.qu_id_stock?.let { payload["qu_id_stock"] = it }
            child.qu_factor_purchase_to_stock?.let { payload["qu_factor_purchase_to_stock"] = it }

            parentId = api.createProduct(payload).created_object_id ?: return
            try {
                api.updateProductUserfields(parentId, mapOf("parent_product" to "1"))
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't flag new parent $parentId: ${e.message}")
            }
        }

        if (parentId != child.id) {
            api.updateProduct(child.id, mapOf("parent_product_id" to parentId))
        }
    }

    companion object {
        private const val TAG = "BasilEnrichment"
        private const val MAX_ATTEMPTS = 4

        const val KEY_PRODUCT_ID = "product_id"
        const val KEY_IS_NEW = "is_new"
        const val KEY_NEEDS_PRICE = "needs_price"
        const val KEY_NEEDS_BEST_BEFORE = "needs_best_before"
        const val KEY_STOCK_ID = "stock_id"

        // Products acting as parents: flagged no-own-stock, or already someone's parent
        fun parentNames(products: List<ProductDetails>): List<String> {
            val referenced = products.mapNotNull { it.parent_product_id }.toSet()
            return products
                .filter { it.no_own_stock == 1 || it.id in referenced }
                .map { it.name }
                .distinct()
                .sorted()
        }

        // Same wording and examples as the nightly parent script, with the
        // other details Basil needs asked for in the same call.
        fun buildPrompt(
            productName: String,
            parent: Boolean,
            placement: Boolean,
            shelfLife: Boolean,
            price: Boolean,
            locationNames: List<String>,
            groupNames: List<String>,
            existingParents: List<String>
        ): String {
            val asks = mutableListOf<String>()
            val keys = mutableListOf<String>()
            if (parent) {
                var ask = "A generic parent category name like how a recipe would refer to the product. Use the key \"name\"."
                if (existingParents.isNotEmpty()) {
                    ask += " If one of these existing parent names fits, reuse it exactly as written: [${existingParents.joinToString(", ")}]."
                }
                asks += ask
                keys += "name"
            }
            if (placement) {
                asks += "The most logical storage location strictly from this exact list: [${locationNames.joinToString(", ")}]. Use the key \"location\"."
                asks += "The most logical product group strictly from this exact list: [${groupNames.joinToString(", ")}]. Use the key \"group\"."
                keys += listOf("location", "group")
            }
            if (shelfLife) {
                asks += "Estimated shelf life in whole days: \"default_best_before_days\", \"default_best_before_days_after_open\", \"default_best_before_days_after_freezing\", \"default_best_before_days_after_thawing\" (integers), and \"should_not_be_frozen\" (1 for yes, 0 for no)."
                keys += listOf(
                    "default_best_before_days", "default_best_before_days_after_open",
                    "default_best_before_days_after_freezing", "default_best_before_days_after_thawing",
                    "should_not_be_frozen"
                )
            }
            if (price) {
                asks += "The estimated current USD price as a number. Use the key \"price\"."
                keys += "price"
            }

            val examples = if (parent) """

                Examples:
                Product: '80/20 Ground Hamburger' -> {"name": "Ground Beef", "location": "fridge", "group": "meat"}
                Product: 'GOODLES® Dreamy Creamy Mac & Cheese' -> {"name": "Mac & Cheese", "location": "cabinets", "group": "pantry"}
                Product: 'Schweppes Ginger Ale, Raspberry' -> {"name": "Soda", "location": "basement", "group": "beverage"}
            """.trimIndent() else ""

            return buildString {
                appendLine("I will provide a grocery product name. You must determine:")
                asks.forEachIndexed { i, a -> appendLine("${i + 1}. $a") }
                appendLine()
                append("Respond ONLY with valid JSON using exactly these keys: ")
                append(keys.joinToString(", ") { "\"$it\"" })
                appendLine(". Do not invent new keys.")
                if (examples.isNotEmpty()) appendLine(examples)
                appendLine()
                append("Product: $productName")
            }
        }
    }
}
