package com.zorro.otpview

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zorro.optview.OnOtpCompletionListener
import com.zorro.otpview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var vb: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        vb.apply {
            validateButton.setOnClickListener {
                Toast.makeText(this@MainActivity, otpView1.text, Toast.LENGTH_SHORT).show()
                Toast.makeText(this@MainActivity, otpView2.text, Toast.LENGTH_SHORT).show()
            }
            otpView1.setOtpCompletionListener(object : OnOtpCompletionListener {
                override fun onOtpCompleted(otp: String) {
                    Toast.makeText(
                        this@MainActivity,
                        "OnOtpCompletionListener called",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            })
            otpView2.setOtpCompletionListener(object : OnOtpCompletionListener {
                override fun onOtpCompleted(otp: String) {
                    Toast.makeText(
                        this@MainActivity,
                        "OnOtpCompletionListener called",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }
}