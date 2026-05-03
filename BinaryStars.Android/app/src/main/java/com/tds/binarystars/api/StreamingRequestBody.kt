package com.tds.binarystars.api

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream

class StreamingRequestBody(
    private val contentType: String,
    private val inputStream: InputStream,
    private val expectedLength: Long = -1L
) : RequestBody() {
    /** Returns the media type for the request body. */
    override fun contentType() = contentType.toMediaTypeOrNull()
    
    override fun contentLength(): Long {
        return expectedLength
    }

    /** Streams the input into the sink without buffering the whole file. */
    override fun writeTo(sink: BufferedSink) {
        val source = inputStream.source()
        try {
            sink.writeAll(source)
        } catch (e: Exception) {
            android.util.Log.e("StreamingRequestBody", "Error writing to sink", e)
            throw e
        } finally {
            // We do NOT close the source here because OkHttp might retry.
            // Caller is responsible for closing the inputStream if it's not a retryable request.
            // However, most InputStreams can't be reset, so retry will fail anyway.
            // At least we won't crash here.
        }
    }
}
