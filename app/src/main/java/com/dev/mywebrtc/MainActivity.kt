package com.dev.mywebrtc

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dev.mywebrtc.databinding.ActivityMainBinding
import com.permissionx.guolindev.PermissionX

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

//        checkAllPermission()

        binding.apply {
            enterBtn.setOnClickListener {
                checkAllPermission()
            }
        }
    }

    private fun checkAllPermission() {
        PermissionX.init(this)
            .permissions(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            ).request { allgranted, _, _ ->
                if (allgranted) {
                    startActivity(Intent(this@MainActivity, CallActivity::class.java).apply {
                        putExtra("username", binding.username.text.toString())
                    })
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "you should accept all permissions",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }
    }
}