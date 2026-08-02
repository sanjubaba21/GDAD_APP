package com.gdad.bags.data.remote

/**
 * Explicit first-release read window. Queries request one sentinel row beyond the supported
 * in-memory window so a large data set fails closed instead of silently returning partial data.
 */
object RemoteQueryWindow {
    const val MAX_ROWS = 500
    const val REQUEST_ROWS = MAX_ROWS + 1L
    const val SINGLETON_REQUEST_ROWS = 2L
}

class RemoteDataWindowExceededException(
    val dataSet: String,
    val maximumRows: Int,
) : IllegalStateException("$dataSet exceeded the supported $maximumRows-row window")

fun <T> List<T>.requireSupportedWindow(
    dataSet: String,
    maximumRows: Int = RemoteQueryWindow.MAX_ROWS,
): List<T> {
    if (size > maximumRows) {
        throw RemoteDataWindowExceededException(dataSet, maximumRows)
    }
    return this
}
