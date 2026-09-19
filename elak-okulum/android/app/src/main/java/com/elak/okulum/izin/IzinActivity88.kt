package com.elak.okulum.izin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Geriye dönük yönlendirme katmanı.
 * Eski ELAK Okulum sürümleri /izin bağlantılarını bu activity adına açıyordu.
 * WebView kullanmak yerine doğrudan native İzin Takip ekranına aktarır.
 */
class IzinActivity88 : AppCompatActivity() {
    companion object { const val EXTRA_URL = "izin_url" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = Intent(this, IzinActivity::class.java)
        intent.getStringExtra(EXTRA_URL)?.let { target.putExtra(IzinActivity.EXTRA_URL, it) }
        startActivity(target)
        finish()
    }
}
