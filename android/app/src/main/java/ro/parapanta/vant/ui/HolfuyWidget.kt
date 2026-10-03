package ro.parapanta.vant.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Widget-ul oficial Holfuy (fără cheie API). Linkurile din widget se deschid în browser. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HolfuyWidget(stationId: Int, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.useWideViewPort = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        return true
                    }
                }
                loadUrl("https://widget.holfuy.com/?station=$stationId&su=m/s&t=C&lang=en&mode=detailed")
            }
        },
    )
}
