package edu.cmu.cs.sinfonia

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wireguard.config.Config
import edu.cmu.cs.sinfonia.util.TunnelException
import edu.cmu.cs.sinfonia.wireguard.WireGuardClient
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class TunnelExceptionTest {
    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val wireGuardClient = WireGuardClient(appContext)

    @Test
    fun test_ALREADY_EXIST() {
        val configName = "working-helloworld"
        val config = Config.parse(CONFIG_MAP[configName]!!)
        try {
            wireGuardClient.createTunnel(configName, config, true)
            wireGuardClient.createTunnel(configName, config, false)
        } catch (throwable: TunnelException) {
            assertEquals(throwable.getReason(), TunnelException.Reason.ALREADY_EXIST)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown during test")
        }
    }

    companion object {
        private val CONFIG_MAP: Map<String, InputStream> = mapOf()
        private val CONFIG_NAMES = arrayOf(
            "working-helloworld"
        )

        @JvmStatic
        @BeforeClass
        fun readConfig() {
            for (configName in CONFIG_NAMES) {
                CONFIG_MAP.map {
                    configName to Companion::class.java.classLoader?.getResourceAsStream("$configName.conf")
                }
            }
        }
    }
}