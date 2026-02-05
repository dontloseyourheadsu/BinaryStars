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
    override fun contentType() = contentType.toMediaTypeOrNull()

    override fun writeTo(sink: BufferedSink) {
        inputStream.source().use { source ->
            sink.writeAll(source)
        }
    }
}
