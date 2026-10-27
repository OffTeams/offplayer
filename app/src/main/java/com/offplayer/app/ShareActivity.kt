package com.offplayer.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.offplayer.app.download.Downloader
import com.offplayer.app.ui.download.ShareSheet
import com.offplayer.app.ui.theme.OffPlayerTheme

/**
 * «Поделиться» → OFFPlayer из YouTube / YouTube Music: поверх текущего приложения
 * всплывает окно загрузки, приложение целиком не открывается.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = Regex("""https?://\S+""").find(text)?.value
        if (url == null || !Downloader.isSupportedUrl(url)) {
            Toast.makeText(this, "Поддерживаются ссылки YouTube и YouTube Music", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            OffPlayerTheme {
                ShareSheet(
                    url = url,
                    onClose = { finish() },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                )
            }
        }
    }
}
