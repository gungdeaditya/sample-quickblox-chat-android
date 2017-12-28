package com.gungde.example.samplequickbloxchat

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle

class ShowImageActivity : AppCompatActivity() {

    private val EXTRA_URL = "url"

    fun start(context: Context, url: String) {
        val intent = Intent(context, ShowImageActivity::class.java)
        intent.putExtra(EXTRA_URL, url)
        context.startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_image)
    }
}
