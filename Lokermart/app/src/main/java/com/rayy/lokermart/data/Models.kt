package com.rayy.lokermart.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

// Semua properti respons punya nilai default supaya Gson aman jika ada field yang tidak dikirim server.
// Harga dikirim server sebagai angka (Double).

@Keep
data class User(
    val id: Int = 0,
    val name: String = "",
    val email: String = "",
    val phone: String? = null,
    val role: String = "user",
    @SerializedName("rating_avg") val ratingAvg: Double = 0.0,
    @SerializedName("rating_count") val ratingCount: Int = 0,
)

@Keep
data class AuthResponse(val token: String = "", val user: User = User())

@Keep
data class Product(
    val id: Int = 0,
    @SerializedName("seller_id") val sellerId: Int = 0,
    val title: String = "",
    val description: String? = null,
    val price: Double = 0.0,
    val category: String = "Lainnya",
    val condition: String = "",
    @SerializedName("image_url") val imageUrl: String? = null,
    val status: String = "active",
    @SerializedName("seller_name") val sellerName: String? = null,
    @SerializedName("seller_rating") val sellerRating: Double? = null,
    @SerializedName("seller_rating_count") val sellerRatingCount: Int? = null,
)

@Keep
data class Locker(
    val id: Int = 0,
    val name: String = "",
    val address: String = "",
    @SerializedName("free_slots") val freeSlots: Int = 0,
    @SerializedName("total_slots") val totalSlots: Int = 0,
)

@Keep
data class Slot(
    val id: Int = 0,
    @SerializedName("locker_id") val lockerId: Int = 0,
    val label: String = "",
    val size: String = "M",
    val status: String = "free",
)

@Keep
data class ConversationItem(
    @SerializedName("product_id") val productId: Int = 0,
    @SerializedName("product_title") val productTitle: String = "",
    @SerializedName("product_price") val productPrice: Double = 0.0,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("product_status") val productStatus: String = "active",
    @SerializedName("other_id") val otherId: Int = 0,
    @SerializedName("other_name") val otherName: String = "",
    @SerializedName("last_body") val lastBody: String? = null,
    @SerializedName("last_offer") val lastOffer: Double? = null,
    @SerializedName("last_at") val lastAt: String? = null,
)

@Keep
data class Message(
    val id: Int = 0,
    @SerializedName("product_id") val productId: Int = 0,
    @SerializedName("sender_id") val senderId: Int = 0,
    @SerializedName("receiver_id") val receiverId: Int = 0,
    val body: String? = null,
    @SerializedName("offer_price") val offerPrice: Double? = null,
    @SerializedName("offer_status") val offerStatus: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

@Keep
data class Order(
    val id: Int = 0,
    @SerializedName("product_id") val productId: Int = 0,
    @SerializedName("buyer_id") val buyerId: Int = 0,
    @SerializedName("seller_id") val sellerId: Int = 0,
    @SerializedName("agreed_price") val agreedPrice: Double = 0.0,
    val fee: Double = 0.0,
    val total: Double = 0.0,
    val status: String = "",
    val title: String = "",
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("buyer_name") val buyerName: String = "",
    @SerializedName("seller_name") val sellerName: String = "",
    @SerializedName("slot_label") val slotLabel: String? = null,
    @SerializedName("locker_name") val lockerName: String? = null,
    @SerializedName("locker_address") val lockerAddress: String? = null,
    @SerializedName("rated_by_me") val ratedByMe: Boolean = false,
    @SerializedName("dispute_open") val disputeOpen: Boolean = false,
    val role: String = "buyer",
    // Hanya dikirim server ke pembeli, selama pesanan menunggu pengambilan
    @SerializedName("pickup_code") val pickupCode: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

@Keep
data class RetrieveResult(val unlocked: Boolean = false, val order: Order = Order())

@Keep
data class Dispute(
    val id: Int = 0,
    @SerializedName("order_id") val orderId: Int = 0,
    val reason: String = "",
    val status: String = "open",
    val resolution: String? = null,
    val title: String? = null,
    @SerializedName("buyer_name") val buyerName: String? = null,
    @SerializedName("seller_name") val sellerName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

@Keep
data class OkResult(val ok: Boolean = true)

@Keep
data class ErrorBody(val error: String? = null)

// ---------- Request ----------
@Keep data class LoginIn(val email: String, val password: String)
@Keep data class RegisterIn(val name: String, val email: String, val phone: String?, val password: String)
@Keep data class UpdateMeIn(val name: String?, val phone: String?)

@Keep
data class ProductIn(
    val title: String,
    val description: String?,
    val price: Double,
    val category: String,
    val condition: String,
    @SerializedName("image_url") val imageUrl: String?,
)

@Keep
data class MessageIn(
    @SerializedName("receiver_id") val receiverId: Int,
    val body: String? = null,
    @SerializedName("offer_price") val offerPrice: Double? = null,
)

@Keep data class OfferAction(val action: String, val amount: Double? = null)

@Keep
data class OrderIn(
    @SerializedName("product_id") val productId: Int,
    @SerializedName("locker_id") val lockerId: Int,
    @SerializedName("offer_id") val offerId: Int? = null,
    @SerializedName("pickup_code") val pickupCode: String? = null,
)

@Keep data class PayIn(val method: String)
@Keep data class CodeIn(val code: String)
@Keep data class RateIn(val stars: Int, val comment: String?)
@Keep data class DisputeIn(val reason: String)
@Keep data class ResolveIn(val decision: String, val resolution: String?)
@Keep data class SlotIn(val label: String, val size: String)
@Keep data class LockerIn(val name: String, val address: String, val slots: List<SlotIn>)
@Keep data class SlotStatusIn(val status: String)
