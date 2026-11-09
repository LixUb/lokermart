package com.rayy.lokermart.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class User(val id: Int, val name: String, val email: String)
data class AuthResponse(val token: String, val user: User)
data class Credentials(val name: String? = null, val email: String, val password: String)
data class Product(val id: Int, val seller_id: Int, val title: String, val description: String?,
                   val price: String, val category: String?, val condition: String?,
                   val image_url: String?, val status: String, val seller_name: String?)
data class NewProduct(val title: String, val description: String, val price: Double, val category: String, val condition: String)
data class Locker(val id: Int, val name: String, val address: String, val free_slots: String)
data class Message(val id: Int, val sender_id: Int, val receiver_id: Int, val body: String?, val offer_price: String?)
data class MessageIn(val receiver_id: Int, val body: String?, val offer_price: Double? = null)
data class OrderIn(val product_id: Int, val locker_id: Int, val agreed_price: Double)
data class Order(val id: Int, val title: String?, val status: String, val agreed_price: String)
data class PayResult(val id: Int, val status: String, val pickup_code: String)
data class CodeIn(val code: String)
data class RateIn(val stars: Int, val comment: String?)
data class DisputeIn(val reason: String)

interface Api {
    @POST("auth/register") suspend fun register(@Body c: Credentials): AuthResponse
    @POST("auth/login") suspend fun login(@Body c: Credentials): AuthResponse
    @GET("products") suspend fun products(@Query("q") q: String? = null, @Query("category") cat: String? = null): List<Product>
    @GET("products/mine") suspend fun myProducts(): List<Product>
    @POST("products") suspend fun addProduct(@Body p: NewProduct): Product
    @DELETE("products/{id}") suspend fun removeProduct(@Path("id") id: Int)
    @GET("chat/{pid}") suspend fun chat(@Path("pid") pid: Int): List<Message>
    @POST("chat/{pid}") suspend fun send(@Path("pid") pid: Int, @Body m: MessageIn): Message
    @GET("lockers") suspend fun lockers(): List<Locker>
    @POST("orders") suspend fun createOrder(@Body o: OrderIn): Order
    @GET("orders") suspend fun orders(): List<Order>
    @POST("orders/{id}/pay") suspend fun pay(@Path("id") id: Int): PayResult
    @POST("orders/{id}/deposit") suspend fun deposit(@Path("id") id: Int)
    @POST("orders/{id}/retrieve") suspend fun retrieve(@Path("id") id: Int, @Body c: CodeIn)
    @POST("orders/{id}/confirm") suspend fun confirm(@Path("id") id: Int)
    @POST("orders/{id}/rate") suspend fun rate(@Path("id") id: Int, @Body r: RateIn)
    @POST("orders/{id}/dispute") suspend fun dispute(@Path("id") id: Int, @Body d: DisputeIn)
}

object Network {
    var token: String? = null // persist with EncryptedSharedPreferences in production
    private val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val b = chain.request().newBuilder()
        token?.let { b.addHeader("Authorization", "Bearer $it") }
        chain.proceed(b.build())
    }).build()
    val api: Api = Retrofit.Builder().baseUrl("http://10.0.2.2:3000/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
}
