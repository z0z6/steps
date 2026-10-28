package pl.dotsteps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StepStore.init(this)
        StepStore.setStopped(false)
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val settings by StepStore.settings.collectAsState()
    val pal = if (settings.eink) EInkPal else NothingPal

    DisposableEffect(settings.eink) {
        val act = ctx as ComponentActivity
        val t = android.graphics.Color.TRANSPARENT
        val style = if (settings.eink) SystemBarStyle.light(t, t) else SystemBarStyle.dark(t)
        act.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    var granted by remember { mutableStateOf(Perms.hasActivity(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = Perms.hasActivity(ctx)
    }
    LaunchedEffect(granted) {
        if (granted) {
            try { StepService.start(ctx) } catch (_: Exception) { }
        }
    }

    CompositionLocalProvider(LocalPal provides pal) {
        Box(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
            if (granted) MainScreen() else PermissionScreen { launcher.launch(Perms.toRequest()) }
        }
    }
}
