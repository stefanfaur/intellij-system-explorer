package ro.faur.explorer.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.BeforeAll
import java.time.Duration

abstract class ExplorerUITestBase {

    companion object {
        lateinit var robot: RemoteRobot

        @JvmStatic
        @BeforeAll
        fun setupRobot() {
            robot = RemoteRobot("http://127.0.0.1:8082")
            waitFor(Duration.ofSeconds(60)) {
                try {
                    robot.callJs<Boolean>("true")
                } catch (e: Exception) {
                    false
                }
            }
        }
    }
}
