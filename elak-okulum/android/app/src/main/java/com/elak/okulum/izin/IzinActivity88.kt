package com.elak.okulum.izin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Geriye dönük yönlendirme katmanı.
 * ELAK Okulum içindeki /izin bağlantıları modern native İzin Takip ekranına aktarılır.
 */
class IzinActivity88 : AppCompatActivity() {
    companion object { const val EXTRA_URL = "izin_url" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = Intent(this, IzinActivityModern::class.java)
        intent.getStringExtra(EXTRA_URL)?.let { target.putExtra(IzinActivityModern.EXTRA_URL, it) }
        startActivity(target)
        finish()
    }
}
