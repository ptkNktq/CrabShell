package feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScopedViewModelStoreOwnerTest {
    private class FakeViewModel : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    private fun ImageComposeScene.recompose() {
        Snapshot.sendApplyNotifications()
        render()
    }

    @Test
    fun clearsViewModelsWhenLeavingComposition() {
        var signedIn by mutableStateOf(true)
        var vm: FakeViewModel? = null
        ImageComposeScene(width = 10, height = 10) {
            if (signedIn) {
                ScopedViewModelStoreOwner(scopeKey = "scope") { vm = viewModel { FakeViewModel() } }
            }
        }.use { scene ->
            scene.render()
            val first = assertNotNull(vm)
            assertFalse(first.cleared)

            signedIn = false
            scene.recompose()

            assertTrue(first.cleared)
        }
    }

    @Test
    fun createsNewViewModelAfterReEntering() {
        var signedIn by mutableStateOf(true)
        var vm: FakeViewModel? = null
        ImageComposeScene(width = 10, height = 10) {
            if (signedIn) {
                ScopedViewModelStoreOwner(scopeKey = "scope") { vm = viewModel { FakeViewModel() } }
            }
        }.use { scene ->
            scene.render()
            val first = assertNotNull(vm)

            signedIn = false
            scene.recompose()
            signedIn = true
            scene.recompose()

            val second = assertNotNull(vm)
            assertNotSame(first, second)
            assertFalse(second.cleared)
        }
    }

    @Test
    fun keepsViewModelAcrossRecompositionWithSameKey() {
        var uid by mutableStateOf("user-a")
        var tick by mutableStateOf(0)
        var vm: FakeViewModel? = null
        ImageComposeScene(width = 10, height = 10) {
            ScopedViewModelStoreOwner(scopeKey = uid) {
                tick
                vm = viewModel { FakeViewModel() }
            }
        }.use { scene ->
            scene.render()
            val first = assertNotNull(vm)

            // 同じ key のまま content が再コンポーズされても維持される
            tick++
            scene.recompose()
            assertSame(first, vm)
            assertFalse(first.cleared)

            // キーが変わると破棄されて新規生成される
            uid = "user-b"
            scene.recompose()
            assertTrue(first.cleared)
            assertNotSame(first, vm)
        }
    }
}
