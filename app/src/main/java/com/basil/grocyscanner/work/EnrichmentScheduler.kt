package com.basil.grocyscanner.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit

// What a shopping-mode purchase left unfinished, for the background job to fill in.
data class EnrichmentRequest(
    val productId: Int,
    val isNewProduct: Boolean,
    val needsPrice: Boolean,
    // The purchase was booked without a best-before date (no shelf life known yet)
    val needsBestBefore: Boolean,
    // The stock entry the purchase created, so price and date can be written onto it
    val stockId: String?
)

class EnrichmentScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // Jobs run one at a time, in scan order. Two new ketchups scanned back to
    // back must not both create a "Ketchup" parent.
    fun enqueue(request: EnrichmentRequest): UUID {
        val work = OneTimeWorkRequestBuilder<EnrichmentWorker>()
            .setInputData(
                workDataOf(
                    EnrichmentWorker.KEY_PRODUCT_ID to request.productId,
                    EnrichmentWorker.KEY_IS_NEW to request.isNewProduct,
                    EnrichmentWorker.KEY_NEEDS_PRICE to request.needsPrice,
                    EnrichmentWorker.KEY_NEEDS_BEST_BEFORE to request.needsBestBefore,
                    EnrichmentWorker.KEY_STOCK_ID to request.stockId
                )
            )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ENRICHMENT_QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        return work.id
    }

    fun observe(id: UUID): Flow<WorkInfo?> = workManager.getWorkInfoByIdFlow(id)

    // Called on Undo: the stock entry is gone, so its pending fix has nothing to do.
    fun cancelStockEntryFix(stockId: String) {
        workManager.cancelUniqueWork(StockEntryFixWorker.uniqueName(stockId))
    }

    companion object {
        const val ENRICHMENT_QUEUE = "basil-enrichment"

        // Grocy refuses to undo a purchase whose stock entry was edited
        // afterwards, so the price/date edit waits until the Undo button is
        // long gone.
        const val STOCK_ENTRY_FIX_DELAY_SECONDS = 120L

        fun enqueueStockEntryFix(
            workManager: WorkManager,
            productId: Int,
            stockId: String,
            price: Double?,
            shelfLifeDays: Int?
        ) {
            val work = OneTimeWorkRequestBuilder<StockEntryFixWorker>()
                .setInputData(
                    workDataOf(
                        StockEntryFixWorker.KEY_PRODUCT_ID to productId,
                        StockEntryFixWorker.KEY_STOCK_ID to stockId,
                        StockEntryFixWorker.KEY_PRICE to (price ?: -1.0),
                        StockEntryFixWorker.KEY_SHELF_LIFE_DAYS to (shelfLifeDays ?: -1)
                    )
                )
                .setInitialDelay(STOCK_ENTRY_FIX_DELAY_SECONDS, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(
                StockEntryFixWorker.uniqueName(stockId),
                ExistingWorkPolicy.REPLACE,
                work
            )
        }
    }
}
