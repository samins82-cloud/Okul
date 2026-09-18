package com.elak.okulum.rehber

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class RehberActivity81 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, RehberActivity82::class.java))
        finish()
    }
}
