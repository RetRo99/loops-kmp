package com.retro99.loops.sdk.api

import com.retro99.loops.sdk.LoopsHttp
import com.retro99.loops.sdk.ksp.JvmAsync
import com.retro99.loops.sdk.model.CreateUploadRequest
import com.retro99.loops.sdk.model.UploadCompleteResponse
import com.retro99.loops.sdk.model.UploadUrlResponse
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Uploads API group for image assets used in email content.
 *
 * Accessed via [LoopsClient.uploads]:
 * ```kotlin
 * val upload = client.uploads.create(CreateUploadRequest("image/png", bytes.size))
 * // PUT the bytes to upload.presignedUrl yourself, then:
 * val asset = client.uploads.complete(upload.emailAssetId)
 * ```
 *
 * Note: the SDK only obtains the presigned URL — the caller PUTs the file bytes to
 * [UploadUrlResponse.presignedUrl] directly; the SDK does not proxy the binary.
 */
@JvmAsync
class UploadsApi internal constructor(
    internal val http: LoopsHttp,
) {

    /**
     * Reserve an upload slot (POST uploads) and get a presigned URL to PUT the file bytes to.
     */
    suspend fun create(request: CreateUploadRequest): UploadUrlResponse =
        http.execute {
            post("uploads") {
                setBody(request)
            }.body()
        }

    /**
     * Mark an upload complete after the bytes have been PUT to the presigned URL
     * (POST uploads/{emailAssetId}/complete). No request body. Returns the final hosted URL.
     */
    suspend fun complete(emailAssetId: String): UploadCompleteResponse =
        http.execute {
            post("uploads/$emailAssetId/complete").body()
        }
}
