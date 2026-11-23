package dev.pocketagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.pocketagent.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    // The screens get the same instance through viewModel() (the activity is their ViewModelStoreOwner).
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        vm.runtime.refreshInstalledState()
        vm.refreshBattery()
    }
}
