package com.elak.okulum.rehber

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elak.okulum.OkulumSession
import com.elak.okulum.auth.SecureOkulumCredentials
import kotlin.concurrent.thread

class RehberActivity81 : AppCompatActivity() {
    private var connecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rehberSession = RehberSession(this)
        if (!rehberSession.token.isNullOrBlank()) {
            openRehber()
            return
        }

        connectFromSavedCredentials()
    }

    private fun connectFromSavedCredentials() {
        val okulum = OkulumSession(this)
        var username = okulum.username.trim()
        var password = okulum.password

        if (username.isBlank() || password.isBlank()) {
            SecureOkulumCredentials(this).load()?.let {
                username = it.username.trim()
                password = it.password
            }
        }

        if (username.isNotBlank() && password.isNotBlank()) {
            connect(username, password, false)
        } else {
            showCredentialDialog()
        }
    }

    private fun connect(username: String, password: String, fromDialog: Boolean) {
        if (connecting) return
        connecting = true

        thread {
            var error: String? = null
            var count = 0
            try {
                val login = RehberApi.login(username, password)
                val session = RehberSession(this)
                session.token = login.token
                session.username = username
                count = CallerCache(this).replaceFromSync(RehberApi.sync(login.token))
                session.lastSync = System.currentTimeMillis()

                OkulumSession(this).saveCredentials(username, password)
                SecureOkulumCredentials(this).save(username, password)
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Akıllı Rehber bağlantısı kurulamadı."
            }

            runOnUiThread {
                connecting = false
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (error == null) {
                    Toast.makeText(this, "Akıllı Rehber bağlandı: $count telefon eşitlendi.", Toast.LENGTH_SHORT).show()
                    openRehber()
                } else {
                    showCredentialDialog(username, error!!, fromDialog)
                }
            }
        }
    }

    private fun showCredentialDialog(
        prefillUsername: String = "",
        error: String? = null,
        wasManualAttempt: Boolean = false
    ) {
        if (isFinishing || isDestroyed) return

        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        box.addView(TextView(this).apply {
            text = if (error.isNullOrBlank()) {
                "Akıllı Rehber, ELAK Okulum hesabınızla otomatik bağlanır. Bu cihazda giriş bilgisi henüz kaydedilmemiş. ELAK kullanıcı bilgilerinizi bir kez doğrulayın."
            } else {
                "Bağlantı kurulamadı: $error\n\nELAK Okulum kullanıcı bilgilerinizi kontrol edip tekrar deneyin."
            }
            textSize = 14f
            setPadding(0, 0, 0, pad / 2)
        })

        val user = EditText(this).apply {
            hint = "ELAK kullanıcı adı"
            setSingleLine(true)
            setText(prefillUsername)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val pass = EditText(this).apply {
            hint = "ELAK şifre"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(user, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(pass, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Akıllı Rehber Bağlantısı")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Geri") { _, _ -> finish() }
            .setPositiveButton("ELAK Hesabımla Bağlan", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u = user.text.toString().trim()
                val p = pass.text.toString()
                if (u.isBlank() || p.isBlank()) {
                    Toast.makeText(this, "Kullanıcı adı ve şifreyi girin.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                connect(u, p, true)
            }
            if (prefillUsername.isNotBlank()) pass.requestFocus() else user.requestFocus()
        }
        dialog.show()
    }

    private fun openRehber() {
        startActivity(Intent(this, RehberActivity84::class.java).apply {
            intent.extras?.let { putExtras(it) }
        })
        finish()
    }
}
