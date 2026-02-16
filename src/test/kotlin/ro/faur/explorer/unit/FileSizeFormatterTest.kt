package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ro.faur.explorer.util.FileSizeFormatter

class FileSizeFormatterTest {

    @ParameterizedTest
    @CsvSource(
        "0, '0 B'",
        "1, '1 B'",
        "512, '512 B'",
        "1023, '1023 B'",
    )
    fun `formats bytes correctly`(bytes: Long, expected: String) {
        assertEquals(expected, FileSizeFormatter.format(bytes))
    }

    @ParameterizedTest
    @CsvSource(
        "1024, '1.0 KB'",
        "1536, '1.5 KB'",
        "10240, '10.0 KB'",
        "102400, '100.0 KB'",
    )
    fun `formats kilobytes correctly`(bytes: Long, expected: String) {
        assertEquals(expected, FileSizeFormatter.format(bytes))
    }

    @ParameterizedTest
    @CsvSource(
        "1048576, '1.0 MB'",
        "5242880, '5.0 MB'",
        "1572864, '1.5 MB'",
    )
    fun `formats megabytes correctly`(bytes: Long, expected: String) {
        assertEquals(expected, FileSizeFormatter.format(bytes))
    }

    @ParameterizedTest
    @CsvSource(
        "1073741824, '1.0 GB'",
        "5368709120, '5.0 GB'",
    )
    fun `formats gigabytes correctly`(bytes: Long, expected: String) {
        assertEquals(expected, FileSizeFormatter.format(bytes))
    }

    @Test
    fun `formats terabytes correctly`() {
        assertEquals("1.0 TB", FileSizeFormatter.format(1099511627776L))
    }

    @Test
    fun `negative size returns 0 B`() {
        assertEquals("0 B", FileSizeFormatter.format(-1))
    }
}
