package com.retro99.loops.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `POST uploads`. Reserves a slot for an image asset and returns a presigned
 * URL to PUT the bytes to (the SDK does not proxy the binary — see [UploadUrlResponse]).
 *
 * @param contentType MIME type of the file (image/jpeg | image/png | image/gif | image/webp).
 * @param contentLength Size of the file in bytes (≤ 4_000_000).
 */
@Serializable
data class CreateUploadRequest(
    @SerialName("contentType")
    val contentType: String,
    @SerialName("contentLength")
    val contentLength: Int,
)

/**
 * Response from `POST uploads`. The consumer must PUT the file bytes to [presignedUrl]
 * themselves, then call `uploads/{emailAssetId}/complete`.
 */
@Serializable
data class UploadUrlResponse(
    @SerialName("emailAssetId")
    val emailAssetId: String,
    @SerialName("presignedUrl")
    val presignedUrl: String,
)

/**
 * Response from `POST uploads/{id}/complete`, returning the asset's final hosted URL.
 */
@Serializable
data class UploadCompleteResponse(
    @SerialName("emailAssetId")
    val emailAssetId: String,
    @SerialName("finalUrl")
    val finalUrl: String,
)
