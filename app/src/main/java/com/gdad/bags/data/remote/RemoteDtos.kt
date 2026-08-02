package com.gdad.bags.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PinLoginRequestDto(
    @SerialName("login_id") val loginId: String,
    val pin: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
internal data class PinLoginResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String,
)

@Serializable
internal data class ProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("platform_role") val platformRole: String,
    val disabled: Boolean,
)

@Serializable
internal data class MembershipDto(
    @SerialName("shop_id") val shopId: String,
    val role: String,
    val active: Boolean,
)

@Serializable
internal data class ShopDto(
    val id: String,
    val active: Boolean,
)
