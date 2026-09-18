package com.elak.okulum.rehber

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class RehberActivity81 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, RehberActivity84::class.java).apply {
            intent.extras?.let { putExtras(it) }
        })
        finish()
    }
}
