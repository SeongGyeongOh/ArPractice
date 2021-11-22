package com.gyeong.myapplication

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.gyeong.myapplication.databinding.ActivityArBinding


class ArActivity : AppCompatActivity() {
    private lateinit var binding: ActivityArBinding
    private var renderable: Renderable? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment

//        arFragment.setOnTapPlaneGlbModel("models/halloween.glb")
        arFragment.setOnTapPlaneGlbModel("https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb")
    }
}