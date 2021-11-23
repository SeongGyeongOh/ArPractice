package com.gyeong.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.gyeong.myapplication.databinding.ActivityOpenGlArcoreBinding
import com.gyeong.myapplication.helpers.DisplayRotationHelper
import com.gyeong.myapplication.helpers.TrackingStateHelper
import com.gyeong.myapplication.rendering.*
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGlArcore : AppCompatActivity(), GLSurfaceView.Renderer {

    lateinit var binding: ActivityOpenGlArcoreBinding
    private lateinit var triangle: Triangle
    private val vPMatrix = FloatArray(16) // Model View Projection Matrix
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val backgroundRenderer: BackgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()
    private var session: Session? = null
    private lateinit var trackingStateHelper: TrackingStateHelper
    private lateinit var displayRotationHelper: DisplayRotationHelper
    private lateinit var gestureDetector: GestureDetector
    private val queuedSingleTaps = ArrayBlockingQueue<MotionEvent>(1)
    private val anchorMatrix = FloatArray(16)

    private val vikingObject = ObjectRenderer()
    private var vikingAttachment: PlaneAttachment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("openglactivity", "오픈지엘")
        binding = ActivityOpenGlArcoreBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setupTapDetector()
        setupSurfaceView()

        trackingStateHelper = TrackingStateHelper(this)
        displayRotationHelper = DisplayRotationHelper(this)
    }

    override fun onResume() {
        super.onResume()

        if (session == null) {
            createSession()
        }

        try {
            session?.resume()
        } catch (e: Exception) {
            Log.e("에이알코어", "세션 시작 실패함 ${e.localizedMessage}")
        }

        binding.surfaceView.onResume()
        displayRotationHelper.onResume()
    }

    override fun onPause() {
        super.onPause()

        session?.let {
            displayRotationHelper.onPause()
            binding.surfaceView.onPause()
            it.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSurfaceView() {
        // Set up renderer.
        binding.surfaceView.preserveEGLContextOnPause = true
        binding.surfaceView.setEGLContextClientVersion(2)
        binding.surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.surfaceView.setRenderer(this)
        binding.surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        binding.surfaceView.setWillNotDraw(false)
        binding.surfaceView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun setupTapDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                queuedSingleTaps.offer(e)
                return true
            }
        })
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        vikingObject.createOnGlThread(
            this@OpenGlArcore,
            getString(R.string.model_viking_obj),
            getString(R.string.model_viking_png)
        )

        vikingObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)

        try {
            backgroundRenderer.createOnGlThread(this@OpenGlArcore)
            planeRenderer.createOnGlThread(this@OpenGlArcore, getString(R.string.model_grid_png))
        } catch (e: Exception) {
            Log.e("오픈지엘", e.localizedMessage)
        }

        triangle = Triangle()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
        GLES20.glViewport(0, 0, width, height) // gl view가 그려지는 영역
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        session?.let {
            displayRotationHelper.updateSessionIfNeeded(it)
            try {
                it.setCameraTextureName(backgroundRenderer.textureId)
                val frame = it.update()
                val camera = frame.camera

                handleTap(frame, camera)
                drawBackground(frame)

                trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)
                if (!isInTrackingState(camera)) return


                val projectionMatrix = computeProjectionMatrix(camera)
                val viewMatrix = computeViewMatrix(camera)
                val lightIntensity = computeLightIntensity(frame)

//                checkPlaneDetected()
                visualizePlanes(camera, projectionMatrix)

                drawObject(
                    vikingObject,
                    vikingAttachment,
                    Mode.VIKING.scaleFactor,
                    projectionMatrix,
                    viewMatrix,
                    lightIntensity
                )

            } catch (e: Exception) {
                Log.e("오픈지엘", "카메라 백그라운드 에러남 ${e.message}")
            }
        }

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        // projection과 view transformation 계산
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        triangle.draw(vPMatrix)
    }

    private fun drawBackground(frame: Frame) {
        backgroundRenderer.draw(frame)
    }

    private fun isInTrackingState(camera: Camera): Boolean {
        if (camera.trackingState == TrackingState.PAUSED) {
//            messageSnackbarHelper.showMessage(
//                this@MainActivity, TrackingStateHelper.getTrackingFailureReasonString(camera)
//            )
            return false
        }

        return true
    }

    private fun handleTap(frame: Frame, camera: Camera) {
        val tap = queuedSingleTaps.poll()

        if (tap != null && camera.trackingState == TrackingState.TRACKING) {
            for (hit in frame.hitTest(tap)) {
                val trackable = hit.trackable

                if (trackable is Plane
                    && trackable.isPoseInPolygon(hit.hitPose)
                    && PlaneRenderer.calculateDistanceToPlane(hit.hitPose, camera.pose) > 0) {

                    vikingAttachment = addSessionAnchorFromAttachment(vikingAttachment, hit)
                }
            }
        }
    }

    private fun addSessionAnchorFromAttachment(
        previousAttachment: PlaneAttachment?,
        hit: HitResult
    ): PlaneAttachment? {
        // 기존에 previousAttachment가 있으면 remove 시키기
//        previousAttachment?.anchor?.detach()

        // 화면을 hit 한 위치를 plane으로, 거기에 anchor을 내림
        val plane = hit.trackable as Plane
        val anchor = session!!.createAnchor(hit.hitPose)

        // PlaneAttachment 리
        return PlaneAttachment(plane, anchor)
    }

    fun computeProjectionMatrix(camera: Camera): FloatArray {
        val projectionMatrix = FloatArray(16)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

        return projectionMatrix
    }

    fun computeViewMatrix(camera: Camera): FloatArray {
        val viewMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)

        return viewMatrix
    }

    /**
     * Compute lighting from average intensity of the image.
     */
    fun computeLightIntensity(frame: Frame): FloatArray {
        val lightIntensity = FloatArray(4)
        frame.lightEstimate.getColorCorrection(lightIntensity, 0)

        return lightIntensity
    }

    /**
     *  Visualizes planes.
     */
    private fun visualizePlanes(camera: Camera, projectionMatrix: FloatArray) {
        planeRenderer.drawPlanes(
            session!!.getAllTrackables(Plane::class.java),
            camera.displayOrientedPose,
            projectionMatrix
        )
    }

    private fun createSession() {
        try {
            if (session == null) {
                when (ArCoreApk.getInstance().requestInstall(this, true)) {
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        Toast.makeText(this, "session creation is failed", Toast.LENGTH_SHORT).show()
                    }
                }

                if (!hasCameraPermission(this)) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    session = Session(this)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "exception: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            Log.e(MainActivity.TAG, "${e.localizedMessage}")
        }
    }

    fun hasCameraPermission(activity: Activity?): Boolean {
        return (ContextCompat.checkSelfPermission(
            activity!!,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            session = Session(this)
            Toast.makeText(this, "session created", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "카메라 권한 필요", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawObject(
        objectRenderer: ObjectRenderer,
        planeAttachment: PlaneAttachment?,
        scaleFactor: Float,
        projectionMatrix: FloatArray,
        viewMatrix: FloatArray,
        lightIntensity: FloatArray
    ) {
        if (planeAttachment?.isTracking == true) {
            planeAttachment.pose.toMatrix(anchorMatrix, 0)

            // Update and draw the model
            objectRenderer.updateModelMatrix(anchorMatrix, scaleFactor)
            objectRenderer.draw(viewMatrix, projectionMatrix, lightIntensity)
        }
    }
}