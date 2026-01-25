package cz.hcasc.adminhcasc

import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import cz.hcasc.adminhcasc.storage.AdminGateStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val store = AdminGateStore(this)

        setContent {
            MaterialTheme {
                AdminRoot(store = store)
            }
        }
    }
}

private enum class AdminTarget { HOTEL, DAGMAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminRoot(store: AdminGateStore) {
    var unlocked by remember { mutableStateOf(store.isUnlocked()) }
    var pass by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(AdminTarget.HOTEL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AdminHCASC") },
                actions = {
                    if (unlocked) {
                        IconButton(onClick = { store.lock(); unlocked = false }) {
                            Icon(Icons.Filled.Lock, contentDescription = "Zamknout")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!unlocked) {
            UnlockScreen(
                padding = padding,
                pass = pass,
                onPassChange = { pass = it },
                onUnlock = {
                    if (store.unlock(pass)) {
                        unlocked = true
                        pass = ""
                    }
                },
            )
        } else {
            AdminSwitchAndWeb(
                padding = padding,
                target = target,
                onTargetChange = { target = it },
            )
        }
    }
}

@Composable
private fun UnlockScreen(
    padding: PaddingValues,
    pass: String,
    onPassChange: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Zadejte admin heslo", style = MaterialTheme.typography.titleLarge)
        Text(
            "Heslo slouží jen jako lokální ochrana aplikace. Přihlášení do web adminu zůstává serverové.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = pass,
            onValueChange = onPassChange,
            label = { Text("Admin heslo") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
        )
        Button(onClick = onUnlock, enabled = pass.isNotBlank()) {
            Icon(Icons.Filled.Lock, contentDescription = null)
            Text("Odemknout", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminSwitchAndWeb(
    padding: PaddingValues,
    target: AdminTarget,
    onTargetChange: (AdminTarget) -> Unit,
) {
    var webViewRef: WebView? by remember { mutableStateOf(null) }
    val adminUrl = when (target) {
        AdminTarget.HOTEL -> BuildConfig.HOTEL_ADMIN_URL
        AdminTarget.DAGMAR -> BuildConfig.DAGMAR_ADMIN_URL
    }
    val origin = adminUrl.substringBefore("/admin")

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = target == AdminTarget.HOTEL,
                    onClick = { onTargetChange(AdminTarget.HOTEL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Hotel admin") },
                )
                SegmentedButton(
                    selected = target == AdminTarget.DAGMAR,
                    onClick = { onTargetChange(AdminTarget.DAGMAR) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Dagmar admin") },
                )
            }
            IconButton(onClick = { webViewRef?.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setupAdminWebView(this, origin)
                    loadUrl(adminUrl)
                    webViewRef = this
                }
            },
            update = { wv ->
                webViewRef = wv
                if (wv.url != adminUrl) {
                    wv.loadUrl(adminUrl)
                }
            },
        )
    }
}

private fun setupAdminWebView(webView: WebView, origin: String) {
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
    webView.settings.userAgentString = webView.settings.userAgentString + " AdminHCASC/1.0"

    // Admin exports may download files.
    webView.settings.allowFileAccess = true
    webView.settings.allowContentAccess = true

    // Hardening
    webView.settings.setSupportMultipleWindows(false)
    webView.settings.javaScriptCanOpenWindowsAutomatically = false
    webView.settings.builtInZoomControls = false
    webView.settings.displayZoomControls = false
    webView.settings.mediaPlaybackRequiresUserGesture = true

    webView.webChromeClient = WebChromeClient()
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            return !url.startsWith(origin)
        }
    }
}
