package com.elak.okulum.rehber

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elak.okulum.MainActivity

class RehberActivity81 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = RehberSession(this)
        if (!session.token.isNullOrBlank()) {
            openRehber()
            return
        }

        // Akıllı Rehber kendi içinde kullanıcı adı/şifre istemez.
        // Rehber tokenı yalnızca ana ELAK Okulum oturumu sırasında oluşturulur.
        Toast.makeText(
            this,
            "Akıllı Rehber, ELAK Okulum oturumunuzla bağlanacak.",
            Toast.LENGTH_SHORT
        ).show()

        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra("elak_force_web", true)
            putExtra("elak_start_url", "https://elak.mcoaihl.com/okulum/")
        })
        finish()
    }

    private fun openRehber() {
        startActivity(Intent(this, RehberActivity84::class.java).apply {
            intent.extras?.let { putExtras(it) }
        })
        finish()
    }
}
