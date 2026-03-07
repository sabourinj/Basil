package com.basil.grocyscanner.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    fun create(url: String, token: String): GrocyApi {
        var safeUrl = if (url.endsWith("/")) url else "$url/"
        if (!safeUrl.endsWith("api/")) safeUrl += "api/"

        val logging = HttpLoggingInterceptor { Log.d("GrocyNetwork", it) }
        logging.level = HttpLoggingInterceptor.Level.BODY

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("GROCY-API-KEY", token)
                    .header("Accept", "application/json")

                if (original.body != null) {
                    val oldBody = original.body
                    val newBody = object : RequestBody() {
                        override fun contentType() = "application/json".toMediaTypeOrNull()
                        override fun writeTo(sink: BufferedSink) { oldBody?.writeTo(sink) }
                    }
                    requestBuilder.method(original.method, newBody)
                }
                chain.proceed(requestBuilder.build())
            }.addInterceptor(logging).build()

        return Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GrocyApi::class.java)
    }
}