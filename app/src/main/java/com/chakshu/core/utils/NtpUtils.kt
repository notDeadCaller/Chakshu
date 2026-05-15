package com.chakshu.core.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object NtpUtils {

    private var cachedOffset: Long? = null

    suspend fun getCurrentTimeMs(): Long = withContext(Dispatchers.IO) {
        cachedOffset?.let { return@withContext System.currentTimeMillis() + it }
        return@withContext try {
            val offset = sntpSync("pool.ntp.org", 3_000)
            cachedOffset = offset
            System.currentTimeMillis() + offset
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    // Offset = serverTransmitTime - midpointLocalTime
    private fun sntpSync(host: String, timeoutMs: Int): Long {
        val socket = DatagramSocket()
        socket.soTimeout = timeoutMs
        try {
            val address = InetAddress.getByName(host)
            val buffer = ByteArray(48).also { it[0] = 0x1B }
            val request = DatagramPacket(buffer, buffer.size, address, 123)
            val sentAt = System.currentTimeMillis()
            socket.send(request)
            val response = DatagramPacket(ByteArray(48), 48)
            socket.receive(response)
            val receivedAt = System.currentTimeMillis()
            val d = response.data
            val ntpSeconds = ((d[40].toLong() and 0xFF) shl 24) or
                             ((d[41].toLong() and 0xFF) shl 16) or
                             ((d[42].toLong() and 0xFF) shl 8) or
                             (d[43].toLong() and 0xFF)
            val ntpMs = (ntpSeconds - 2_208_988_800L) * 1_000L
            return ntpMs - (sentAt + receivedAt) / 2
        } finally {
            socket.close()
        }
    }
}
