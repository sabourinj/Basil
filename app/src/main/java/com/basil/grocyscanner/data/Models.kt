package com.basil.grocyscanner.data

data class ProductResponse(
    val product: ProductDetails,
    val last_price: Double? = null,
    val stock_amount: Double? = null,
    val stock_amount_aggregated: Double? = null,
    val barcode: BarcodeDetails? = null
)

// Stock held by the scanned product's siblings (parent family total minus the scanned product)
data class ParentStock(
    val name: String,
    val otherAmount: Double
)

data class ProductDetails(
    val id: Int,
    val name: String,
    val description: String? = null,
    val location_id: Int? = null,
    val shopping_location_id: Int? = null,
    val product_group_id: Int? = null,
    val default_best_before_days: Int = 0,
    val default_price: Double? = null,
    val stock_amount: Double? = null,
    val picture_file_name: String? = null,
    val is_parent: Int? = 0,
    val parent_product_id: Int? = null,
    val no_own_stock: Int? = 0,
    val qu_id_purchase: Int? = null,
    val qu_id_stock: Int? = null,
    val qu_factor_purchase_to_stock: Double? = null
)

// One row of GET /stock: a product that currently has stock
data class CurrentStock(
    val product_id: Int,
    val amount: Double = 0.0
)

// A sibling under the same parent, for the shopping purchase sheet
data class SiblingStock(
    val name: String,
    val amount: Double
)

data class CreatedObjectResponse(
    val created_object_id: Int? = null
)

data class BarcodeDetails(
    val id: Int? = null,
    val barcode: String,
    val product_id: Int? = null,
    val amount: Double? = null
)

data class GrocyLocation(
    val id: Int,
    val name: String
)

data class GrocyProductGroup(
    val id: Int,
    val name: String,
    val userfields: ProductGroupUserfields? = null
)

data class ProductGroupUserfields(
    val expiration_strategy: String? = null
)

data class UserfieldDefinition(
    val name: String,
    val entity: String
)

data class UserfieldCreateRequest(
    val name: String = "expiration_strategy",
    val caption: String = "Expiration Strategy",
    val type: String = "preset-list",
    val entity: String = "product_groups",
    val config: String? = "{\"presets\":{\"not_required\":\"Not Required\",\"ai_estimate\":\"AI Estimate\",\"user_entry\":\"User Entry\"},\"default_value\":\"ai_estimate\"}"
)

data class AddStockRequest(
    val amount: Double,
    val price: Double?,
    val best_before_date: String?,
    val location_id: Int? = null
)

data class TransferStockRequest(
    val amount: Double,
    val location_id_to: Int,
    val location_id_from: Int? = null
)

data class StockLogEntry(
    val id: Int,
    val product_id: Int,
    val amount: Double,
    val best_before_date: String?,
    val stock_id: String?,
    // Shared by every booking of one add/consume; undoing it reverts them all.
    val transaction_id: String? = null
)

data class ConsumeStockRequest(
    val amount: Double,
    val location_id: Int? = null
)

data class OpenStockRequest(
    val amount: Int = 1
)

data class ShoppingListItem(
    val id: Int,
    val product_id: Int? = null,
    val amount: Double,
    val note: String? = null,
    val product_name: String? = null,
    val product_picture_file_name: String? = null,
    val done: Any? = 0,
    val shopping_location_id: Int? = null,
    val category_name: String? = null
)

data class ShoppingLocation(
    val id: Int,
    val name: String
)

data class AddToShoppingListRequest(
    val product_id: Int,
    val amount: Int = 1
)

data class StockEntry(
    val id: Int,
    val amount: Double,
    val best_before_date: String? = null,
    val open: Int = 0,
    val location_id: Int? = null,
    val stock_id: String? = null,
    val purchased_date: String? = null,
    val price: Double? = null,
    val shopping_location_id: Int? = null,
    val note: String? = null
)