package edu.cmu.cs.sinfonia

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wireguard.config.Config
import edu.cmu.cs.sinfonia.util.TunnelException
import edu.cmu.cs.sinfonia.util.TunnelException.Reason
import edu.cmu.cs.sinfonia.wireguard.WireGuardClient
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TunnelExceptionTest {

    @Test
    fun test_ALREADY_EXIST() {
        val configName = "working-helloworld"
        val config = CONFIG_MAP[configName]!!
        val tunnelName = "ALREADY_EXIST"
        try {
            wireGuardClient.createTunnel(tunnelName, config, true)
            wireGuardClient.createTunnel(tunnelName, config, false)
        } catch (throwable: TunnelException) {
            assertEquals(Reason.ALREADY_EXIST, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_ALREADY_EXIST")
        } finally {
            try {
                wireGuardClient.destroyTunnel(tunnelName)
            } catch (_: Throwable) {}
        }
    }

    @Test
    fun test_INVALID_NAME() {
        val configName = "working-helloworld"
        val config = CONFIG_MAP[configName]!!
        val validTunnelName = "he.l_-orl1v=l+d"
        try {
            wireGuardClient.createTunnel(validTunnelName, config, true)
        } catch (throwable: Throwable) {
            if (throwable is TunnelException && throwable.getReason() == Reason.INVALID_NAME)
                fail("$validTunnelName should be valid")
            throwable.printStackTrace()
            fail("Other exception thrown in test_INVALID_NAME")
        } finally {
            try {
                wireGuardClient.destroyTunnel(validTunnelName)
            } catch (_: Throwable) {}
        }

        val invalidTunnelName = "!@#$%^&?:>'^&*()_"
        try {
            wireGuardClient.createTunnel(invalidTunnelName, config, true)
        } catch (throwable: TunnelException) {
            assertEquals(Reason.INVALID_NAME, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_INVALID_NAME")
        } finally {
            try {
                wireGuardClient.destroyTunnel(invalidTunnelName)
            } catch (_: Throwable) {}
        }
    }

    @Test
    fun test_NOT_FOUND() {
        val tunnelName = "NOT_FOUND"
        createTunnel(tunnelName, "working-helloworld", "test_NOT_FOUND")
        try {
            wireGuardClient.setTunnelUp(tunnelName)
            wireGuardClient.setTunnelDown(tunnelName)
            wireGuardClient.setTunnelToggle(tunnelName)
        } catch (throwable: Throwable) {
            if (throwable is TunnelException && throwable.getReason() == Reason.NOT_FOUND)
                fail("$tunnelName exists, should not throw NOT_FOUND error")
            throwable.printStackTrace()
            fail("Other exception thrown in test_NOT_FOUND")
        } finally {
            try {
                wireGuardClient.destroyTunnel(tunnelName)
            } catch (_: Throwable) {}
        }

        val notFoundTunnelName = "TunnelNotFound"
        try {
            wireGuardClient.setTunnelUp(notFoundTunnelName)
        } catch (throwable: TunnelException) {
            assertEquals(Reason.NOT_FOUND, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_NOT_FOUND")
        }
        try {
            wireGuardClient.setTunnelDown(notFoundTunnelName)
        } catch (throwable: TunnelException) {
            assertEquals(Reason.NOT_FOUND, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_NOT_FOUND")
        }
        try {
            wireGuardClient.setTunnelToggle(notFoundTunnelName)
        } catch (throwable: TunnelException) {
            assertEquals(Reason.NOT_FOUND, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_NOT_FOUND")
        }
    }

    @Test
    fun test_ALREADY_UP() {
        val tunnelName = "ALREADY_UP"
        createTunnel(tunnelName, "working-helloworld", "test_ALREADY_UP")
        try {
            wireGuardClient.setTunnelUp(tunnelName)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_ALREADY_UP")
        }
        try {
            wireGuardClient.setTunnelUp(tunnelName)
        } catch (throwable: TunnelException) {
            assertEquals(Reason.ALREADY_UP, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_ALREADY_UP")
        } finally {
            try {
                wireGuardClient.destroyTunnel(tunnelName)
            } catch (_: Throwable) {}
        }
    }

    @Test
    fun test_ALREADY_DOWN() {
        val tunnelName = "ALREADY_DOWN"
        createTunnel(tunnelName, "working-helloworld", "test_ALREADY_DOWN")
        try {
            wireGuardClient.setTunnelDown(tunnelName)
        } catch (throwable: TunnelException) {
            assertEquals(Reason.ALREADY_DOWN, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_ALREADY_DOWN")
        } finally {
            try {
                wireGuardClient.destroyTunnel(tunnelName)
            } catch (_: Throwable) {}
        }
    }

    @Test
    fun test_ALREADY_TOGGLE() {
        val tunnelName = "ALREADY_TOGGLE"
        createTunnel(tunnelName, "working-helloworld", "test_ALREADY_TOGGLE")
        try {
            wireGuardClient.setTunnelToggle(tunnelName)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_ALREADY_TOGGLE")
        }
        try {
            wireGuardClient.setTunnelToggle(tunnelName)
        } catch (throwable: TunnelException) {
            assertEquals(Reason.ALREADY_TOGGLE, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_ALREADY_TOGGLE")
        } finally {
            try {
                wireGuardClient.destroyTunnel(tunnelName)
            } catch (_: Throwable) {}
        }
    }

    private fun createTunnel(tunnelName: String, configName: String, functionName: String) {
        val config = CONFIG_MAP[configName]!!
        try {
            wireGuardClient.createTunnel(tunnelName, config, true)
        } catch (throwable: Throwable) {
            fail("Fail to create $tunnelName in $functionName")
        }
    }

    companion object {
        private var CONFIG_MAP: Map<String, Config> = mapOf()
        private val CONFIG_NAMES = arrayOf(
            "working-helloworld"
        )
        private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        private val wireGuardClient = WireGuardClient(appContext)

        @JvmStatic
        @BeforeClass
        fun init() {
            wireGuardClient.bind()
            for (configName in CONFIG_NAMES) {
                val configInputStream = TunnelExceptionTest::class.java.classLoader
                    ?.getResourceAsStream("$configName.conf")!!
                CONFIG_MAP += configName to Config.parse(configInputStream)
                try {
                    configInputStream.close()
                } catch (_: Throwable) {}
            }
        }

        @JvmStatic
        @AfterClass
        fun fini() {
            wireGuardClient.unbind()
        }
    }
}