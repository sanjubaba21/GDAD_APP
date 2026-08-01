package com.gdad.bags.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteQueryWindowTest {
    @Test
    fun supportedWindowAcceptsTheDocumentedMaximum() {
        val rows = List(RemoteQueryWindow.MAX_ROWS) { it }

        assertEquals(rows, rows.requireSupportedWindow("products"))
    }

    @Test
    fun sentinelRowRejectsSilentPartialData() {
        val rows = List(RemoteQueryWindow.MAX_ROWS + 1) { it }

        val error = assertThrows(RemoteDataWindowExceededException::class.java) {
            rows.requireSupportedWindow("products")
        }

        assertEquals("products", error.dataSet)
        assertEquals(RemoteQueryWindow.MAX_ROWS, error.maximumRows)
    }
}
