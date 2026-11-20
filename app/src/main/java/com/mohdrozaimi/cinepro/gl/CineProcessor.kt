package com.mohdrozaimi.cinepro.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import com.mohdrozaimi.cinepro.FilterParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

/**
 * Terima bingkai kamera, lukis semula dengan shader cinematic, dan hantar ke
 * skrin (Preview) dan fail video (VideoCapture). Semua kerja GPU di satu thread.
 */
class CineProcessor : SurfaceProcessor {

    private val thread = HandlerThread("CineGL").apply { start() }
    private val handler = Handler(thread.looper)
    val executor: Executor = Executor { handler.post(it) }

    @Volatile
    var params: FilterParams = FilterParams()

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    private var program = 0
    private var texId = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private val uniforms = HashMap<String, Int>()

    private var inputW = 1920
    private var inputH = 1080
    private val outputs = LinkedHashMap<SurfaceOutput, EGLSurface>()
    private val texMatrix = FloatArray(16)
    private val outMatrix = FloatArray(16)
    private var frameCount = 0
    private var released = false
    private var glReady = false

    private val posBuf: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texBuf: FloatBuffer = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

    init {
        handler.post { initGl() }
    }

    private fun floatBuffer(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(a); position(0)
        }

    private fun initGl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        EGL14.eglInitialize(display, ver, 0, ver, 1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
        config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)

        program = buildProgram(CineShader.VERTEX, CineShader.FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        listOf(
            "uTexMatrix", "sTex", "uLook", "uLookAmt", "uLens", "uLensAmt", "uHal", "uVig",
            "uGrain", "uLetterbox", "uOutAspect", "uOutSize", "uTexel", "uTime"
        ).forEach { uniforms[it] = GLES20.glGetUniformLocation(program, it) }

        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        texId = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        glReady = true
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw RuntimeException("Shader gagal: $log")
        }
        return s
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Program gagal: " + GLES20.glGetProgramInfoLog(p))
        return p
    }

    override fun onInputSurface(request: SurfaceRequest) {
        handler.post {
            if (released || !glReady) {
                request.willNotProvideSurface()
                return@post
            }
            val st = SurfaceTexture(texId)
            inputW = request.resolution.width
            inputH = request.resolution.height
            st.setDefaultBufferSize(inputW, inputH)
            val surface = Surface(st)
            request.provideSurface(surface, executor) {
                st.setOnFrameAvailableListener(null)
                st.release()
                surface.release()
            }
            st.setOnFrameAvailableListener({ drawFrame(it) }, handler)
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        handler.post {
            if (released || !glReady) {
                surfaceOutput.close()
                return@post
            }
            val surface = surfaceOutput.getSurface(executor) { event ->
                val out = event.surfaceOutput
                outputs.remove(out)?.let { destroySurface(it) }
                out.close()
            }
            val egl = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            if (egl == null || egl == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "Gagal cipta EGL surface")
                surfaceOutput.close()
                return@post
            }
            outputs[surfaceOutput] = egl
        }
    }

    private fun destroySurface(s: EGLSurface) {
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        EGL14.eglDestroySurface(display, s)
    }

    private fun drawFrame(st: SurfaceTexture) {
        if (released) return
        try {
            EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
            st.updateTexImage()
        } catch (e: Exception) {
            return
        }
        st.getTransformMatrix(texMatrix)
        frameCount = (frameCount + 1) % 10000
        val p = params

        for ((out, egl) in outputs.entries.toList()) {
            if (!EGL14.eglMakeCurrent(display, egl, egl, context)) continue
            out.updateTransformMatrix(outMatrix, texMatrix)
            val w = out.size.width
            val h = out.size.height
            val aspect = w.toFloat() / h.toFloat()
            GLES20.glViewport(0, 0, w, h)
            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glUniform1i(u("sTex"), 0)
            GLES20.glUniformMatrix4fv(u("uTexMatrix"), 1, false, outMatrix, 0)
            GLES20.glUniform1i(u("uLook"), p.look.id)
            GLES20.glUniform1f(u("uLookAmt"), p.lookAmount)
            GLES20.glUniform1i(u("uLens"), p.lens.id)
            GLES20.glUniform1f(u("uLensAmt"), p.lensAmount)
            GLES20.glUniform1f(u("uHal"), if (p.halation) 1f else 0f)
            GLES20.glUniform1f(u("uVig"), if (p.vignette) 1f else 0f)
            GLES20.glUniform1f(u("uGrain"), if (p.grain) p.grainAmount else 0f)
            val lb = if (!p.letterbox) 0f else if (aspect >= 1f) 2.39f else 0.8f
            GLES20.glUniform1f(u("uLetterbox"), lb)
            GLES20.glUniform1f(u("uOutAspect"), aspect)
            GLES20.glUniform2f(u("uOutSize"), w.toFloat(), h.toFloat())
            GLES20.glUniform2f(u("uTexel"), 1f / inputW, 1f / inputH)
            GLES20.glUniform1f(u("uTime"), (frameCount % 997) * 1.37f)

            GLES20.glEnableVertexAttribArray(aPosition)
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, posBuf)
            GLES20.glEnableVertexAttribArray(aTexCoord)
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            EGLExt.eglPresentationTimeANDROID(display, egl, st.timestamp)
            EGL14.eglSwapBuffers(display, egl)
        }
    }

    private fun u(name: String): Int = uniforms[name] ?: -1

    fun release() {
        handler.post {
            released = true
            outputs.forEach { (out, egl) ->
                destroySurface(egl)
                out.close()
            }
            outputs.clear()
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, pbuffer)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
            thread.quitSafely()
        }
    }

    companion object {
        private const val TAG = "CinePro"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}

/** Sambungkan pemproses ke Preview + VideoCapture supaya filter masuk ke skrin DAN fail video. */
class CineEffect(processor: CineProcessor) : CameraEffect(
    CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
    processor.executor,
    processor,
    Consumer<Throwable> { Log.e("CinePro", "Ralat kesan kamera", it) }
)
