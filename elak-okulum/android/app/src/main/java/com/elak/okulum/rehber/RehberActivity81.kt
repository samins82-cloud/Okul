package com.elak.okulum.rehber

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elak.okulum.MainActivity
import com.elak.okulum.OkulumSession
import kotlin.concurrent.thread

class RehberActivity81 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val core = OkulumSession(this)
        if (!core.moduleEnabled("akilli_rehber")) {
            Toast.makeText(this, "Akıllı Rehber kurum lisansında pasif.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val session = RehberSession(this)
        if (!session.token.isNullOrBlank()) {
            openRehber()
            return
        }

        val user = core.username
        val pass = core.password
        if (user.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "ELAK CORE oturumu gerekli.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        thread {
            var ok = false
            try {
                val login = RehberApi.login(user, pass)
                session.token = login.token
                session.username = user
                CallerCache(this).replaceFromSync(RehberApi.sync(login.token))
                session.lastSync = System.currentTimeMillis()
                ok = true
            } catch (_: Exception) { }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) openRehber()
                else {
                    Toast.makeText(
                        this,
                        "Akıllı Rehber bağlantısı hazırlanamadı. ELAK hesabınızla yeniden giriş yapın.",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
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
