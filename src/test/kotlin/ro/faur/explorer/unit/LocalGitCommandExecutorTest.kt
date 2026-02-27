package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalGitCommandExecutorTest {
    @Test
    fun `does not deadlock when stderr output is large`() {
        val script = "for i in \$(seq 1 2000); do echo \"stderr line \$i\" >&2; done; echo ok"
        val pb = ProcessBuilder("bash", "-c", script).start()
        var stdout = ByteArray(0)
        var stderr = ByteArray(0)
        val t1 = Thread { stdout = pb.inputStream.readBytes() }.also { it.isDaemon = true; it.start() }
        val t2 = Thread { stderr = pb.errorStream.readBytes() }.also { it.isDaemon = true; it.start() }
        val done = pb.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        t1.join(1_000); t2.join(1_000)
        assertTrue(done, "process should complete without deadlock")
        assertTrue(stderr.isNotEmpty(), "stderr should have content")
    }
}
