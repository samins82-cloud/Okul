package com.elak.okulum

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Compatibility entry retained for 0.9 migration. */
class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, ElakHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
