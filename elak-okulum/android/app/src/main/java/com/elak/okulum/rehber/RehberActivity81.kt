package com.elak.okulum.rehber

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.elak.okulum.OkulumSession
import kotlin.concurrent.thread

class RehberActivity81 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rehberSession = RehberSession(this)
        if (!rehberSession.token.isNullOrBlank()) {
            openRehber()
            return
        }

        val okulumSession = OkulumSession(this)
        val username = okulumSession.username.trim()
        val password = okulumSession.password

        if (username.isBlank() || password.isBlank()) {
            openRehber()
            return
        }

        // ELAK Okulum'a giriş yapılmışsa Akıllı Rehber için ayrıca hesap
        // bağlatma. Aynı kullanıcı bilgileriyle sessizce Rehber tokenı üret.
        thread {
            try {
                val login = RehberApi.login(username, password)
                rehberSession.token = login.token
                rehberSession.username = username
            } catch (_: Exception) {
                // Rehber ekranı yine açılsın; mevcut önbellek çevrimdışı kullanılabilir.
            }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) openRehber()
            }
        }
    }

    private fun openRehber() {
        startActivity(Intent(this, RehberActivity84::class.java).apply {
            intent.extras?.let { putExtras(it) }
        })
        finish()
    }
}
