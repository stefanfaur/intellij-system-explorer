package ro.faur.explorer.sftp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.SftpConnectionManager
import ro.faur.explorer.remote.ConnectionProfile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ConcurrentOperationsTest : EmbeddedSshTestBase() {

    @Test
    fun `concurrent directory listings from separate sessions succeed`() {
        val threadCount = 5
        val errors = CopyOnWriteArrayList<Exception>()
        val latch = CountDownLatch(threadCount)
        val startGate = CountDownLatch(1)
        // Share a single manager to avoid spawning 5 SshClient instances
        val manager = SftpConnectionManager(keyVerifier = testVerifier())
        val threads = (1..threadCount).map { i ->
            Thread {
                try {
                    startGate.await()
                    val profile = ConnectionProfile(
                        name = "thread$i", host = "localhost", port = serverPort,
                        username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
                    )
                    manager.connect(profile, TEST_PASSWORD)
                    val client = manager.getSftpClient("thread$i")!!
                    repeat(10) { client.readDir("/") }
                    manager.disconnect("thread$i")
                } catch (e: Exception) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }
        threads.forEach { it.start() }
        startGate.countDown()
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        manager.shutdown()
        assertTrue(errors.isEmpty(), "Concurrent operations failed: $errors")
    }

    @Test
    fun `concurrent uploads do not corrupt data`() {
        val threadCount = 5
        val errors = CopyOnWriteArrayList<Exception>()
        val latch = CountDownLatch(threadCount)
        val manager = SftpConnectionManager(keyVerifier = testVerifier())
        val threads = (1..threadCount).map { i ->
            Thread {
                try {
                    val profile = ConnectionProfile(
                        name = "upload$i", host = "localhost", port = serverPort,
                        username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
                    )
                    manager.connect(profile, TEST_PASSWORD)
                    val client = manager.getSftpClient("upload$i")!!
                    val data = "data from thread $i\n".repeat(100)
                    client.write("/concurrent_$i.txt").use { out ->
                        out.write(data.toByteArray())
                    }
                    val readBack = client.read("/concurrent_$i.txt").use { it.readAllBytes() }
                    assertEquals(data, String(readBack))
                    manager.disconnect("upload$i")
                } catch (e: Exception) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }
        threads.forEach { it.start() }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        manager.shutdown()
        assertTrue(errors.isEmpty(), "Concurrent uploads failed: $errors")
    }
}
