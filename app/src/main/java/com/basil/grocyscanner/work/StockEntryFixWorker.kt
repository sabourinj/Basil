package com.basil.grocyscanner.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.basil.grocyscanner.data.NetworkClient
import com.basil.grocyscanner.data.SecurePrefs
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Writes the price and best-before date the enrichment found onto the stock
// entry a shopping-mode purchase created. Runs a couple of minutes after the
// purchase: Grocy won't undo a purchase once its entry has been edited.
class StockEntryFixWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val productId = inputData.getInt(KEY_PRODUCT_ID, -1)
        val stockId = inputData.getString(KEY_STOCK_ID)
        if (productId < 0 || stockId == null) return Result.failure()
        val price = inputData.getDouble(KEY_PRICE, -1.0).takeIf { it > 0.0 }
        val shelfLifeDays = inputData.getInt(KEY_SHELF_LIFE_DAYS, -1).takeIf { it > 0 }
        if (price == null && shelfLifeDays == null) return Result.success()

        val prefs = SecurePrefs.get(applicationContext)
        val url = prefs.getString("API_URL", null)
        val token = prefs.getString("API_TOKEN", null)
        if (url.isNullOrBlank() || token.isNullOrBlank()) return Result.failure()
        val api = NetworkClient.create(url, token)

        return try {
            // Gone means it was undone or already used up: nothing to fix.
            val entry = api.getStockEntries(productId).find { it.stock_id == stockId }
                ?: return Result.success()

            val purchased = entry.purchased_date?.let {
                runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
            } ?: LocalDate.now()
            val bestBefore = shelfLifeDays
                ?.let { purchased.plusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE) }
                ?: entry.best_before_date

            val update = mutableMapOf<String, Any>(
                "amount" to entry.amount,
                "open" to (entry.open == 1),
                "purchased_date" to purchased.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
            bestBefore?.let { update["best_before_date"] = it }
            (price ?: entry.price)?.let { update["price"] = it }
            entry.location_id?.let { update["location_id"] = it }
            entry.shopping_location_id?.let { update["shopping_location_id"] = it }
            entry.note?.let { update["note"] = it }

            api.updateStockEntry(entry.id, update)
            Result.success()
        } catch (e: IOException) {
            retryOrGiveUp(e)
        } catch (e: HttpException) {
            if (e.code() >= 500) retryOrGiveUp(e) else {
                Log.e(TAG, "Stock entry fix refused: HTTP ${e.code()}")
                Result.failure()
            }
        }
    }

    private fun retryOrGiveUp(e: Exception): Result {
        Log.w(TAG, "Stock entry fix attempt ${runAttemptCount + 1} failed: ${e.message}")
        return if (runAttemptCount < 4) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "BasilEnrichment"
        const val KEY_PRODUCT_ID = "product_id"
        const val KEY_STOCK_ID = "stock_id"
        const val KEY_PRICE = "price"
        const val KEY_SHELF_LIFE_DAYS = "shelf_life_days"

        fun uniqueName(stockId: String) = "basil-stock-fix-$stockId"
    }
}
