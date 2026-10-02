package server.di

import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloseOnStopTest {
    private class Resource : AutoCloseable {
        var closeCount = 0

        override fun close() {
            closeCount++
        }
    }

    private class FailingResource : AutoCloseable {
        var closeCalled = false

        override fun close() {
            closeCalled = true
            throw IllegalStateException("close failed")
        }
    }

    @Test
    fun closesCreatedInstanceWhenKoinIsClosed() {
        val resource = Resource()
        val app = koinApplication { modules(module { single { resource }.closeOnStop() }) }
        app.koin.get<Resource>()

        app.close()

        assertEquals(1, resource.closeCount)
    }

    @Test
    fun failureInOneDoesNotPreventClosingOthers() {
        val failing = FailingResource()
        val resource = Resource()
        val app =
            koinApplication {
                modules(
                    module {
                        single { failing }.closeOnStop()
                        single { resource }.closeOnStop()
                    },
                )
            }
        app.koin.get<FailingResource>()
        app.koin.get<Resource>()

        app.close()

        assertTrue(failing.closeCalled)
        assertEquals(1, resource.closeCount)
    }

    @Test
    fun doesNothingForInstanceNotCreated() {
        var created = false
        val app =
            koinApplication {
                modules(
                    module {
                        single {
                            created = true
                            Resource()
                        }.closeOnStop()
                    },
                )
            }

        // 一度も get していないので作成されず、close 時にも作成・close されない
        app.close()

        assertFalse(created)
    }
}
