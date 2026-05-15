package com.chakshu

import com.chakshu.core.utils.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CryptoUtilsTest {

    @Test
    fun sha256_emptyBytes_knownHash() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, CryptoUtils.computeSha256(ByteArray(0)))
    }

    @Test
    fun sha256_outputIs64LowercaseHexChars() {
        val hash = CryptoUtils.computeSha256("test".toByteArray(Charsets.UTF_8))
        assertEquals(64, hash.length)
        assertEquals(true, hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun sha256_file_matchesBytesOverload() {
        val content = "hello chakshu".toByteArray(Charsets.UTF_8)
        val tmp = File.createTempFile("chakshu_test", ".bin")
        try {
            tmp.writeBytes(content)
            assertEquals(CryptoUtils.computeSha256(content), CryptoUtils.computeSha256(tmp))
        } finally {
            tmp.delete()
        }
    }
}
