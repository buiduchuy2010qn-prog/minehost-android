package com.mineserve.mobile.data

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun acceptsEmptyAndExactLimit() {
        assertArrayEquals(byteArrayOf(), BoundedInput.read(ByteArrayInputStream(byteArrayOf()), 0))
        val bytes = ByteArray(16384) { (it % 255).toByte() }
        assertArrayEquals(bytes, BoundedInput.read(ByteArrayInputStream(bytes), bytes.size))
    }

    @Test(expected = IOException::class) fun rejectsUnknownLengthOversize() {
        BoundedInput.read(ByteArrayInputStream(ByteArray(8193)), 8192)
    }

    @Test(expected = IOException::class) fun rejectsNonemptyWithZeroBudget() {
        BoundedInput.read(ByteArrayInputStream(byteArrayOf(1)), 0)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsNegativeBudget() {
        BoundedInput.read(ByteArrayInputStream(byteArrayOf()), -1)
    }

    @Test fun propagatesCancellationCheck() {
        val marker = IllegalStateException("cancel")
        try {
            BoundedInput.read(ByteArrayInputStream(ByteArray(100)), 100) { throw marker }
            fail("Expected cancellation")
        } catch (e: IllegalStateException) {
            assertSame(marker, e)
        }
    }
}
