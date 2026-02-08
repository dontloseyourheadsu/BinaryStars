package com.tds.binarystars.api

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream

class StreamingRequestBody(
    private val contentType: String,
    private val inputStream: InputStream
) : RequestBody() {
    /** Returns the media type for the request body. */
    override fun contentType() = contentType.toMediaTypeOrNull()

    /** Streams the input into the sink without buffering the whole file. */
    override fun writeTo(sink: BufferedSink) {
        inputStream.source().use { source ->
            sink.writeAll(source)
        }
    }
}
