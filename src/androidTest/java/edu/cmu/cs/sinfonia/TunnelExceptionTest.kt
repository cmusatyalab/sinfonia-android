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
            fail("Should throw test_ALREADY_EXIST exception")
        } catch (throwable: TunnelException) {
            assertEquals(Reason.ALREADY_EXIST, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_ALREADY_EXIST")
        } finally {
            try {
                wireGuardClient.destroyTunnel(tunnelName)
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }
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
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }
        }

        val invalidTunnelName = "!@#$%^&?:>'^&*()_"
        try {
            wireGuardClient.createTunnel(invalidTunnelName, config, true)
            fail("Should throw INVALID_NAME exception")
        } catch (throwable: TunnelException) {
            assertEquals(Reason.INVALID_NAME, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_INVALID_NAME")
        } finally {
            try {
                wireGuardClient.destroyTunnel(invalidTunnelName)
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }
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
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }
        }

        val notFoundTunnelName = "TunnelNotFound"
        wireGuardClient.saveTunnel(notFoundTunnelName)
        test_NOT_FOUND(notFoundTunnelName, wireGuardClient::setTunnelUp)
        test_NOT_FOUND(notFoundTunnelName, wireGuardClient::setTunnelDown)
        test_NOT_FOUND(notFoundTunnelName, wireGuardClient::setTunnelToggle)
        test_NOT_FOUND(notFoundTunnelName, wireGuardClient::destroyTunnel)
        wireGuardClient.removeTunnel(notFoundTunnelName)
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
        test_ALREADY_STATE(tunnelName, wireGuardClient::setTunnelUp, Reason.ALREADY_UP)
    }

    @Test
    fun test_ALREADY_DOWN() {
        val tunnelName = "ALREADY_DOWN"
        createTunnel(tunnelName, "working-helloworld", "test_ALREADY_DOWN")
        test_ALREADY_STATE(tunnelName, wireGuardClient::setTunnelDown, Reason.ALREADY_DOWN)
    }

    @Test
    fun test_UNAUTHORIZED_ACCESS() {
        val tunnelName = "UNAUTH_ACCESS"
        createTunnel(tunnelName, "working-helloworld", "test_UNAUTHORIZED_ACCESS")
        wireGuardClient.removeTunnel(tunnelName)
        test_UNAUTHORIZED_ACCESS(tunnelName, wireGuardClient::setTunnelUp)
        test_UNAUTHORIZED_ACCESS(tunnelName, wireGuardClient::setTunnelDown)
        test_UNAUTHORIZED_ACCESS(tunnelName, wireGuardClient::setTunnelToggle)
        test_UNAUTHORIZED_ACCESS(tunnelName, wireGuardClient::destroyTunnel)
        wireGuardClient.saveTunnel(tunnelName)
        try {
            wireGuardClient.destroyTunnel(tunnelName)
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
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

    private fun test_NOT_FOUND(tunnelName: String, method: (tunnelName: String) -> Unit) {
        try {
            method(tunnelName)
            fail("Should throw NOT_FOUND exception")
        } catch (throwable: TunnelException) {
            assertEquals(Reason.NOT_FOUND, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_NOT_FOUND")
        }
    }

    private fun test_ALREADY_STATE(tunnelName: String, method: (tunnelName: String) -> Unit, reason: Reason) {
        try {
            method(tunnelName)
            fail("Should throw $reason exception")
        } catch (throwable: TunnelException) {
            when (reason) {
                Reason.ALREADY_UP -> assertEquals(Reason.ALREADY_UP, throwable.getReason())
                Reason.ALREADY_DOWN -> assertEquals(Reason.ALREADY_DOWN, throwable.getReason())
                else -> fail("Other exception thrown in test_$reason")
            }
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_$reason")
        } finally {
            try {
                wireGuardClient.destroyTunnel(tunnelName)
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
            }
        }
    }

    private fun test_UNAUTHORIZED_ACCESS(tunnelName: String, method: (tunnelName: String) -> Unit) {
        try {
            method(tunnelName)
            fail("Should throw UNAUTHORIZED_ACCESS exception")
        } catch (throwable: TunnelException) {
            assertEquals(Reason.UNAUTHORIZED_ACCESS, throwable.getReason())
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            fail("Other exception thrown in test_UNAUTHORIZED_ACCESS")
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