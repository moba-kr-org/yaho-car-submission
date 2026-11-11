package ai.yaho.hyundai.data

import org.json.JSONObject

data class BookingStatus(
    val bookingId: String, val serviceType: String, val requestedAt: String,
    val status: String, val cancellationRequested: Boolean, val updatedAt: String,
    val workshopName: String? = null,
)
internal fun parseBookingStatus(json: JSONObject): BookingStatus {
    val id = json.getString("bookingId")
    require(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(id))
    return BookingStatus(id,json.getString("serviceType"),json.getString("requestedAt"),json.getString("status"),
        !json.isNull("cancellationRequestedAt") && json.optString("cancellationRequestedAt").isNotBlank(),json.getString("updatedAt"), json.optString("workshopName").takeIf(String::isNotBlank))
}
