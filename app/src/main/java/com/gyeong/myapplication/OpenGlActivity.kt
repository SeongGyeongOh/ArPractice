package com.gyeong.myapplication

import android.annotation.SuppressLint
import android.opengl.GLES10
import android.opengl.GLES10.*
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.gyeong.myapplication.databinding.ActivityOpenGlBinding
import com.gyeong.myapplication.helpers.CameraPermissionHelper.hasCameraPermission
import com.gyeong.myapplication.helpers.TrackingStateHelper
import com.gyeong.myapplication.rendering.BackgroundRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGlActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private lateinit var binding: ActivityOpenGlBinding
    private lateinit var triangle: Triangle
    private val vPMatrix = FloatArray(16) // Model View Projection Matrix
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val backgroundRenderer: BackgroundRenderer = BackgroundRenderer()
//    private var session: Session? = null
    private lateinit var trackingStateHelper: TrackingStateHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("openglactivity", "오픈지엘")
        binding = ActivityOpenGlBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setupSurfaceView()
//        trackingStateHelper = TrackingStateHelper(this)
    }

    override fun onResume() {
        super.onResume()

//        if (session == null && hasCameraPermission(this)) {
//            session = Session(this)
//            session?.resume()
//        }

        binding.surfaceView.onResume()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSurfaceView() {
        // Set up renderer.
        binding.surfaceView.setEGLContextClientVersion(2) // OpenGL ES 2.0 context 생성
        binding.surfaceView.setRenderer(this)
        binding.surfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY // surface가생성될 때 GLSurfaceView의 requestREnder 메소드가 호출될 때만 화면을 다시 그린다

//        binding.surfaceView.preserveEGLContextOnPause = true
//        binding.surfaceView.setEGLContextClientVersion(2)
//        binding.surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
//        binding.surfaceView.setRenderer(this)
//        binding.surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
//        binding.surfaceView.setWillNotDraw(false)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.2f, 0.3f, 0.8f, 1.0f) // red, green, blue, alpha 0~1사이의 값
//        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

//        try {
//            backgroundRenderer.createOnGlThread(this@OpenGlActivity)
//        } catch (e: Exception) {
//            Log.e("오픈지엘", e.localizedMessage)
//        }

        triangle = Triangle()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height) // gl view가 그려지는 영역

        val ratio: Float = width.toFloat() / height.toFloat()

        // 투영(projection) 정의 - 해당 projection matrix는 onDrawFrame()의 좌표에 적용된다
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
    }

    override fun onDrawFrame(gl: GL10?) {
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
//        session?.let {
//            Log.d("세션", "ondrawframe")
//            try {
//                it.setCameraTextureName(backgroundRenderer.textureId)
//                val frame = it.update()
//                val camera = frame.camera
//
//                trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)
//                if (!isInTrackingState(camera)) return
//                drawBackground(frame)
//
//
//                val projectionMatrix = computeProjectionMatrix(camera)
//                val viewMatrix = computeViewMatrix(camera)
//                val lightIntensity = computeLightIntensity(frame)
//
//                visualizeTrackedPoints(frame, projectionMatrix, viewMatrix)
////                checkPlaneDetected()
//                visualizePlanes(camera, projectionMatrix)
//
//            } catch (e: Exception) {
//                Log.e("오픈지엘", "카메라 백그라운드 에러남 ${e.message}")
//            }
//        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT) // 어떤 버퍼를 지울 것인지 결정(배경색을 지워, 위에 설정한 glClearColor가 배경색으로 나타날 수 있도록 해줌)

        // 카메라 뷰 변환
        // 카메라 뷰란 : 가상 카메라 위치를 기준으로 그릴 객체의 좌표를 조정
        // 카메라 위치를 나타내는 Camera view matrix 정의
        // 1: ViewMatrix, 2: Mtrixoffset, 3~5: eye(카메라 위치), 6~8: center(카메라가 바라보는 방향), 9~11: up vector(카메라의 위 아래 방향)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        // projection과 view transformation 계산
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // 도형 그리기
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
     * Visualizes tracked points.
     */
    private fun visualizeTrackedPoints(
        frame: Frame,
        projectionMatrix: FloatArray,
        viewMatrix: FloatArray
    ) {
        // Use try-with-resources to automatically release the point cloud.
        frame.acquirePointCloud().use { pointCloud ->
//            pointCloudRenderer.update(pointCloud)
//            pointCloudRenderer.draw(viewMatrix, projectionMatrix)
        }
    }

    /**
     *  Visualizes planes.
     */
    private fun visualizePlanes(camera: Camera, projectionMatrix: FloatArray) {
//        planeRenderer.drawPlanes(
//            session!!.getAllTrackables(Plane::class.java),
//            camera.displayOrientedPose,
//            projectionMatrix
//        )
    }
}
