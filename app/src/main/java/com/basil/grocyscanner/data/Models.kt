package com.basil.grocyscanner.data

data class ProductResponse(
    val product: ProductDetails,
    val stock_amount: Double?,
    val last_price: Double? = null
)
data class ProductDetails(
    val id: Int,
    val name: String,
    val category_id: Int?,
    val product_group_id: Int? = null,
    val default_best_before_days: Int,
    val default_price: Double? = null,
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

data class GrocyProductGroup(
    val id: Int,
    val name: String,
    val userfields: GroupUserfields? = null
)

data class GroupUserfields(
    val expiration_strategy: String? = null
)

data class UserfieldDefinition(
    val id: Int,
    val name: String,
    val entity: String
)

data class UserfieldCreateRequest(
    val entity: String = "product_groups",
    val name: String = "expiration_strategy",
    val caption: String = "Basil Expiration Date Strategy",
    val type: String = "preset-list",
    val config: String = "{\"options\":\"user_entry,ai_estimate,not_required\"}",
    val show_as_column_in_tables: Int = 1
)