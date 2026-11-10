package com.rayy.lokermart.data

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface Api {
    // Auth
    @POST("auth/register") suspend fun register(@Body body: RegisterIn): AuthResponse
    @POST("auth/login") suspend fun login(@Body body: LoginIn): AuthResponse
    @GET("auth/me") suspend fun me(): User
    @PUT("auth/me") suspend fun updateMe(@Body body: UpdateMeIn): User

    // Produk
    @GET("products") suspend fun products(
        @Query("q") q: String? = null,
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = null,
    ): List<Product>
    @GET("products/mine") suspend fun myProducts(): List<Product>
    @GET("products/{id}") suspend fun product(@Path("id") id: Int): Product
    @POST("products") suspend fun addProduct(@Body body: ProductIn): Product
    @PUT("products/{id}") suspend fun updateProduct(@Path("id") id: Int, @Body body: ProductIn): Product
    @DELETE("products/{id}") suspend fun removeProduct(@Path("id") id: Int): OkResult

    // Chat & negosiasi
    @GET("chat") suspend fun conversations(): List<ConversationItem>
    @GET("chat/{pid}") suspend fun chat(@Path("pid") pid: Int, @Query("with") other: Int): List<Message>
    @POST("chat/{pid}") suspend fun send(@Path("pid") pid: Int, @Body body: MessageIn): Message
    @PATCH("chat/offers/{id}") suspend fun respondOffer(@Path("id") id: Int, @Body body: OfferAction): Message

    // Loker
    @GET("lockers") suspend fun lockers(): List<Locker>
    @POST("lockers") suspend fun addLocker(@Body body: LockerIn): Locker
    @GET("lockers/{id}/slots") suspend fun slots(@Path("id") id: Int): List<Slot>
    @PATCH("lockers/slots/{id}") suspend fun setSlot(@Path("id") id: Int, @Body body: SlotStatusIn): Slot

    // Pesanan, pembayaran, pengambilan
    @GET("orders") suspend fun orders(@Query("role") role: String? = null): List<Order>
    @GET("orders/{id}") suspend fun order(@Path("id") id: Int): Order
    @POST("orders") suspend fun createOrder(@Body body: OrderIn): Order
    @POST("orders/{id}/pay") suspend fun pay(@Path("id") id: Int, @Body body: PayIn): Order
    @POST("orders/{id}/deposit") suspend fun deposit(@Path("id") id: Int): Order
    @POST("orders/{id}/retrieve") suspend fun retrieve(@Path("id") id: Int, @Body body: CodeIn): RetrieveResult
    @POST("orders/{id}/confirm") suspend fun confirm(@Path("id") id: Int): Order
    @POST("orders/{id}/cancel") suspend fun cancel(@Path("id") id: Int): Order

    // Rating & sengketa
    @POST("orders/{id}/rate") suspend fun rate(@Path("id") id: Int, @Body body: RateIn): OkResult
    @POST("orders/{id}/dispute") suspend fun dispute(@Path("id") id: Int, @Body body: DisputeIn): Dispute
    @GET("disputes/mine") suspend fun myDisputes(): List<Dispute>
    @GET("admin/disputes") suspend fun adminDisputes(): List<Dispute>
    @POST("admin/disputes/{id}/resolve") suspend fun resolveDispute(@Path("id") id: Int, @Body body: ResolveIn): Dispute
}

object Network {
    /**
     * Alamat backend.
     * - Emulator Android: http://10.0.2.2:3000/
     * - HP fisik (satu Wi-Fi dengan laptop): http://IP-LAPTOP:3000/  (cek IP dengan ipconfig)
     * - Produksi: wajib https://
     */
    const val BASE_URL = "http://10.0.2.2:3000/"

    val gson = Gson()

    val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val b = chain.request().newBuilder()
            Session.token?.let { b.addHeader("Authorization", "Bearer $it") }
            chain.proceed(b.build())
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val api: Api = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(Api::class.java)
}
