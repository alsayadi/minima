package com.minima.os

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.minima.os.core.bus.PendingCommandBus
import com.minima.os.ui.onboarding.Onboarding
import com.minima.os.ui.onboarding.hasCompletedOnboarding
import com.minima.os.ui.theme.MinimaTheme
import com.minima.os.ui.launcher.LauncherScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val roleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* User chose whether to set Minima as default launcher */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestHomeRole()
        routeIncomingCommand(intent)

        setContent {
            MinimaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val ctx = LocalContext.current
                    var showOnboarding by remember { mutableStateOf(!hasCompletedOnboarding(ctx)) }
                    if (showOnboarding) {
                        Onboarding(onDone = { showOnboarding = false })
                    } else {
                        LauncherScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask launch mode → an existing Launcher receives new intents here.
        // Most importantly: shares from ShareReceiverActivity arrive this way.
        setIntent(intent)
        routeIncomingCommand(intent)
    }

    /**
     * Lifts a command piggy-backed on the launch Intent onto the process-level
     * [PendingCommandBus] so [com.minima.os.ui.launcher.LauncherViewModel] can
     * pick it up whether the process was cold or warm.
     */
    private fun routeIncomingCommand(intent: Intent?) {
        val cmd = intent?.getStringExtra(ShareReceiverActivity.EXTRA_SHARED_COMMAND) ?: return
        if (cmd.isBlank()) return
        PendingCommandBus.post(cmd)
        // Consume the extra so a subsequent onNewIntent without a command doesn't re-fire it.
        intent.removeExtra(ShareReceiverActivity.EXTRA_SHARED_COMMAND)
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                roleRequest.launch(intent)
            }
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        // Launcher should not go back — it IS home
    }
}
