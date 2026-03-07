package com.basil.grocyscanner.data

data class ProductResponse(val product: ProductDetails, val stock_amount: Double?)

data class ProductDetails(
    val id: Int,
    val name: String,
    val category_id: Int?,
    val default_best_before_days: Int,
    val picture_file_name: String? = null
)

data class AddStockRequest(
    val amount: Int,
    val price: Double? = null,
    val best_before_date: String? = null,
    val transaction_type: String = "purchase"
)

data class ConsumeStockRequest(
    val amount: Int,
    val transaction_type: String = "consume",
    val spoiled: Boolean = false
)

data class OpenStockRequest(val amount: Int = 1)

data class StockEntry(
    val id: Int,
    val amount: Double,
    val best_before_date: String?
)

data class GrocyLocation(val id: Int, val name: String)

data class GrocyProductGroup(val id: Int, val name: String)