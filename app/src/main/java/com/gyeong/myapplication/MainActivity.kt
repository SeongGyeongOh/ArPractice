package com.gyeong.myapplication

import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.gyeong.myapplication.databinding.ActivityMainBinding
import com.gyeong.myapplication.rendering.*
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val html = "<html>" +
                "<body style='margin:0;padding-bottom: 10%;'>\n" +
                "<a href='intent://arvr.google.com/scene-viewer/1.0?file=https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/Avocado/glTF/Avocado.gltf&mode=ar_only#Intent;scheme=https;package=com.google.ar.core;action=android.intent.action.VIEW;S.browser_fallback_url=https://developers.google.com/ar;end;'>ㅁㄴㅇㅁ</a>" +
                "</body>" +
                "</html>"

        binding.webView.loadDataWithBaseURL("", html, "text/html", "utf-8", null)
//        binding.btn.setOnClickListener {
//            val sceneViewerIntent = Intent(Intent.ACTION_VIEW)
//            val intentUri: Uri = Uri.parse("https://arvr.google.com/scene-viewer/1.0").buildUpon()
//                .appendQueryParameter(
//                    "file",
//                    "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/Avocado/glTF/Avocado.gltf"
//                )
//                .appendQueryParameter("mode", "ar_only")
//                .build()
//            sceneViewerIntent.data = intentUri
//            sceneViewerIntent.setPackage("com.google.ar.core")
//            startActivity(sceneViewerIntent)
//        }
//    }

    }

}