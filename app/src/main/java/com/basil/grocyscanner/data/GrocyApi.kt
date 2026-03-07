package com.basil.grocyscanner.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GrocyApi {
    @GET("stock/products/by-barcode/{barcode}")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): ProductResponse

    @GET("stock/products/{productId}")
    suspend fun getProductById(@Path("productId") productId: Int): ProductResponse

    @GET("stock/products/{productId}/entries")
    suspend fun getStockEntries(@Path("productId") productId: Int): List<StockEntry>

    @POST("stock/products/{productId}/add")
    suspend fun addStock(@Path("productId") productId: Int, @Body request: AddStockRequest)

    @POST("stock/products/{productId}/consume")
    suspend fun consumeStock(@Path("productId") productId: Int, @Body request: ConsumeStockRequest)

    @POST("stock/products/{productId}/open")
    suspend fun openStock(@Path("productId") productId: Int, @Body request: OpenStockRequest)

    @GET("stock/products/{productId}/printlabel")
    suspend fun printLabel(@Path("productId") productId: Int)

    @GET("stock/barcodes/external-lookup/{barcode}")
    suspend fun externalBarcodeLookup(@Path("barcode") barcode: String, @Query("add") add: Boolean = true): Any

    @GET("objects/locations")
    suspend fun getLocations(): List<GrocyLocation>

    @GET("objects/product_groups")
    suspend fun getProductGroups(): List<GrocyProductGroup>

    @PUT("objects/products/{productId}")
    suspend fun updateProduct(@Path("productId") productId: Int, @Body productData: @JvmSuppressWildcards Map<String, Any>)
}