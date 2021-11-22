package com.gyeong.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.camera2.CameraDevice
import android.opengl.GLES20
import android.opengl.GLSurfaceView
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
import com.gyeong.myapplication.databinding.ActivityMainBinding
import com.gyeong.myapplication.helpers.DisplayRotationHelper
import com.gyeong.myapplication.helpers.SnackbarHelper
import com.gyeong.myapplication.helpers.TrackingStateHelper
import com.gyeong.myapplication.rendering.*
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private lateinit var binding: ActivityMainBinding
    private var session: Session? = null

    private lateinit var gestureDetector: GestureDetector
    private lateinit var trackingStateHelper: TrackingStateHelper
    private lateinit var displayRotationHelper: DisplayRotationHelper

    private val backgroundRenderer: BackgroundRenderer = BackgroundRenderer()
    private val planeRenderer: PlaneRenderer = PlaneRenderer()
    private val pointCloudRenderer: PointCloudRenderer = PointCloudRenderer()

    private val vikingObject = ObjectRenderer()
    private val cannonObject = ObjectRenderer()
    private val targetObject = ObjectRenderer()

    private var vikingAttachment: PlaneAttachment? = null
    private var cannonAttachment: PlaneAttachment? = null
    private var targetAttachment: PlaneAttachment? = null

    private val maxAllocationSize = 16
    private val anchorMatrix = FloatArray(maxAllocationSize)
    private val queuedSingleTaps = ArrayBlockingQueue<MotionEvent>(1)

    private var installRequested: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setupTapDetector()
        setupSurfaceView()

        trackingStateHelper = TrackingStateHelper(this)
        displayRotationHelper = DisplayRotationHelper(this)

        installRequested = false
    }

    fun hasCameraPermission(activity: Activity?): Boolean {
        return (ContextCompat.checkSelfPermission(
            activity!!,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED)
    }

    private val permissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            session = Session(this)
            Toast.makeText(this, "session created", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "카메라 권한 필요", Toast.LENGTH_SHORT).show()
        }
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
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "exception: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "${e.localizedMessage}")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSurfaceView() {
        // Set up renderer.
        binding.surfaceview.preserveEGLContextOnPause = true
        binding.surfaceview.setEGLContextClientVersion(2)
        binding.surfaceview.setEGLConfigChooser(8, 8, 8, 8, maxAllocationSize, 0)
        binding.surfaceview.setRenderer(this)
        binding.surfaceview.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        binding.surfaceview.setWillNotDraw(false)
        binding.surfaceview.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun setupTapDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onSingleTap(e)
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        })
    }

    override fun onResume() {
        super.onResume()

        if (session == null) {
            createSession()
        }

        try {
            session?.resume()
        } catch (e: Exception) {
            Toast.makeText(this, "exception!!!!!!!!!!!!!!", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "${e.localizedMessage}")
        }

        binding.surfaceview.onResume()
        displayRotationHelper.onResume()
    }

    private fun onSingleTap(e: MotionEvent) {
        // Queue tap if there is space. Tap is lost if queue is full.
        queuedSingleTaps.offer(e)
    }

    override fun onPause() {
        super.onPause()

        session?.let {
            displayRotationHelper.onPause()
            binding.surfaceview.onPause()
            it.pause()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        session?.close()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        vikingObject.createOnGlThread(
            this@MainActivity,
            getString(R.string.model_viking_obj),
            getString(R.string.model_viking_png)
        )

        cannonObject.createOnGlThread(
            this@MainActivity,
            getString(R.string.model_cannon_obj),
            getString(R.string.model_cannon_png)
        )

        targetObject.createOnGlThread(
            this@MainActivity,
            getString(R.string.model_target_obj),
            getString(R.string.model_target_png)
        )

        targetObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)
        vikingObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)
        cannonObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)

        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(this@MainActivity)
            planeRenderer.createOnGlThread(this@MainActivity, getString(R.string.model_grid_png))
            pointCloudRenderer.createOnGlThread(this@MainActivity)

            // TODO - set up the objects
        } catch (e: IOException) {
            Log.e(TAG, getString(R.string.failed_to_read_asset), e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
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

    private fun isInTrackingState(camera: Camera): Boolean {
        if (camera.trackingState == TrackingState.PAUSED) {
//            messageSnackbarHelper.showMessage(
//                this@MainActivity, TrackingStateHelper.getTrackingFailureReasonString(camera)
//            )
            return false
        }

        return true
    }

    override fun onDrawFrame(gl: GL10?) {

        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        session?.let {
            // Notify ARCore session that the view size changed
            displayRotationHelper.updateSessionIfNeeded(it)

            try {
                it.setCameraTextureName(backgroundRenderer.textureId)

                val frame = it.update()
                val camera = frame.camera

                // Handle one tap per frame.
                handleTap(frame, camera)
                drawBackground(frame)

                // Keeps the screen unlocked while tracking, but allow it to lock when tracking stops.
                trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

                // If not tracking, don't draw 3D objects, show tracking failure reason instead.
                if (!isInTrackingState(camera)) return

                val projectionMatrix = computeProjectionMatrix(camera)
                val viewMatrix = computeViewMatrix(camera)
                val lightIntensity = computeLightIntensity(frame)

                visualizeTrackedPoints(frame, projectionMatrix, viewMatrix)
                checkPlaneDetected()
                visualizePlanes(camera, projectionMatrix)

                drawObject(
                    vikingObject,
                    vikingAttachment,
                    Mode.VIKING.scaleFactor,
                    projectionMatrix,
                    viewMatrix,
                    lightIntensity
                )

                drawObject(
                    cannonObject,
                    cannonAttachment,
                    Mode.CANNON.scaleFactor,
                    projectionMatrix,
                    viewMatrix,
                    lightIntensity
                )

                drawObject(
                    targetObject,
                    targetAttachment,
                    Mode.TARGET.scaleFactor,
                    projectionMatrix,
                    viewMatrix,
                    lightIntensity
                )
            } catch (t: Throwable) {
                Log.e(TAG, "오브젝트 그리는데 에러남")
            }
        }
    }

    private fun handleTap(frame: Frame, camera: Camera) {
        val tap = queuedSingleTaps.poll()

        if (tap != null && camera.trackingState == TrackingState.TRACKING) {
            // Check if any plane was hit, and if it was hit inside the plane polygon
            for (hit in frame.hitTest(tap)) {
                val trackable = hit.trackable

                if ((trackable is Plane
                            && trackable.isPoseInPolygon(hit.hitPose)
                            && PlaneRenderer.calculateDistanceToPlane(hit.hitPose, camera.pose) > 0)
                    || (trackable is Point
                            && trackable.orientationMode
                            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                ) {
                    vikingAttachment = addSessionAnchorFromAttachment(vikingAttachment, hit)
//                    when (mode) {
//                        Mode.VIKING ->  {
//
//                        }
//                        Mode.CANNON -> {
//                            cannonAttachment = addSessionAnchorFromAttachment(cannonAttachment, hit)
//                        }
//                        Mode.TARGET -> {
//                            targetAttachment = addSessionAnchorFromAttachment(targetAttachment, hit)
//                        }
//                    }
                    // TODO: Create an anchor if a plane or an oriented point was hit
                    break
                }
            }
        }
    }

    private fun addSessionAnchorFromAttachment(
        previousAttachment: PlaneAttachment?,
        hit: HitResult
    ): PlaneAttachment? {
        // 기존에 previousAttachment가 있으면 remove 시키기
        previousAttachment?.anchor?.detach()

        // 화면을 hilt 한 위치를 plane으로, 거기에 anchor을 내림
        val plane = hit.trackable as Plane
        val anchor = session!!.createAnchor(hit.hitPose)

        // PlaneAttachment 리
        return PlaneAttachment(plane, anchor)
    }

    private fun drawBackground(frame: Frame) {
        backgroundRenderer.draw(frame)
    }

    private fun computeProjectionMatrix(camera: Camera): FloatArray {
        val projectionMatrix = FloatArray(maxAllocationSize)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

        return projectionMatrix
    }

    private fun computeViewMatrix(camera: Camera): FloatArray {
        val viewMatrix = FloatArray(maxAllocationSize)
        camera.getViewMatrix(viewMatrix, 0)

        return viewMatrix
    }

    /**
     * Compute lighting from average intensity of the image.
     */
    private fun computeLightIntensity(frame: Frame): FloatArray {
        val lightIntensity = FloatArray(4)
        frame.lightEstimate.getColorCorrection(lightIntensity, 0)

        return lightIntensity
    }

    /**
     * Visualizes tracked points.
     */
    private fun visualizeTrackedPoints(
        frame: Frame,
        projectionMatrix: FloatArray,
        viewMatrix: FloatArray
    ) {
        // Use try-with-resources to automatically release the point cloud.
        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRenderer.update(pointCloud)
            pointCloudRenderer.draw(viewMatrix, projectionMatrix)
        }
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

    /**
     * Checks if any tracking plane exists then, hide the message UI, otherwise show searchingPlane message.
     */
    private fun checkPlaneDetected() {
        if (hasTrackingPlane()) {
//            messageSnackbarHelper.hide(this@MainActivity)
        } else {
//            messageSnackbarHelper.showMessage(
//                this@MainActivity,
//                getString(com.google.ar.core.R.string.searching_for_surfaces)
//            )
        }
    }

    /**
     * Checks if we detected at least one plane.
     */
    private fun hasTrackingPlane(): Boolean {
        val allPlanes = session!!.getAllTrackables(Plane::class.java)

        for (plane in allPlanes) {
            if (plane.trackingState == TrackingState.TRACKING) {
                return true
            }
        }

        return false
    }

    companion object {
        const val TAG = "MainActivity"
    }
}