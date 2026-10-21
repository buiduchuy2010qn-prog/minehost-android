package com.mineserve.mobile.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Bounded binary reads for small remote assets; never buffer an unlimited response. */
object BoundedInput {
    fun read(input: InputStream, maxBytes: Int, checkCancelled: () -> Unit = {}): ByteArray {
        require(maxBytes >= 0)
        val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            checkCancelled()
            // Read at most one byte beyond the limit to detect an oversized stream.
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), maxBytes.toLong() - total + 1).toInt())
            if (count < 0) break
            if (count == 0) continue
            if (count > maxBytes - total) throw IOException("Remote asset exceeds $maxBytes bytes")
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }
}
