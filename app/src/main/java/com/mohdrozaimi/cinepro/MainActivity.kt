package com.mohdrozaimi.cinepro

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mohdrozaimi.cinepro.gl.CineEffect
import com.mohdrozaimi.cinepro.gl.CineProcessor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalCamera2Interop::class)
class MainActivity : ComponentActivity() {

    private enum class Tab(val label: String) { WARNA("WARNA"), LENSA("LENSA"), FILEM("FILEM"), KAMERA("KAMERA"), PRESET("PRESET") }

    // ---------- Paparan ----------
    private lateinit var previewView: PreviewView
    private lateinit var focusRing: View
    private lateinit var recDot: View
    private lateinit var timerText: TextView
    private lateinit var fmtText: TextView
    private lateinit var micText: TextView
    private lateinit var audioMeter: ProgressBar
    private lateinit var tabRow: LinearLayout
    private lateinit var contentBox: LinearLayout
    private lateinit var chipRow: LinearLayout
    private lateinit var sliderRow: LinearLayout
    private lateinit var sliderLabel: TextView
    private lateinit var slider: SeekBar
    private lateinit var sliderValue: TextView
    private lateinit var recordBtn: ImageButton
    private lateinit var flipBtn: ImageButton
    private lateinit var thumbBtn: ImageView

    // ---------- Kamera ----------
    private val processor = CineProcessor()
    private val effect by lazy { CineEffect(processor) }
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var actualFps = "24fps"

    // ---------- Tetapan ----------
    private lateinit var store: PresetStore
    private var params = FilterParams()
    private var currentTab = Tab.WARNA
    private var panelOpen = true
    private var fps24 = true
    private var shutter48 = false
    private var aeLocked = false
    private var wbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
    private var evIndex = 0
    private var manualBaseIso = 400
    private var lastUri: Uri? = null

    @Volatile private var lastExposureNs = 0L
    @Volatile private var lastIso = 0

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.CAMERA] == true) startCamera()
            else Toast.makeText(this, "CinePro perlukan izin kamera untuk berfungsi.", Toast.LENGTH_LONG).show()
        }

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = updateMicLabel()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = updateMicLabel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PresetStore(this)
        params = store.loadLast() ?: Preset.BUILT_IN.first().params
        processor.params = params
        hideSystemBars()
        setupUi()

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.registerAudioDeviceCallback(audioCallback, null)

        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            permissionLauncher.launch(needed)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // =====================================================================
    //  UI
    // =====================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUi() {
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        focusRing = findViewById(R.id.focusRing)
        recDot = findViewById(R.id.recDot)
        timerText = findViewById(R.id.timerText)
        fmtText = findViewById(R.id.fmtText)
        micText = findViewById(R.id.micText)
        audioMeter = findViewById(R.id.audioMeter)
        tabRow = findViewById(R.id.tabRow)
        contentBox = findViewById(R.id.contentBox)
        chipRow = findViewById(R.id.chipRow)
        sliderRow = findViewById(R.id.sliderRow)
        sliderLabel = findViewById(R.id.sliderLabel)
        slider = findViewById(R.id.slider)
        sliderValue = findViewById(R.id.sliderValue)
        recordBtn = findViewById(R.id.recordBtn)
        flipBtn = findViewById(R.id.flipBtn)
        thumbBtn = findViewById(R.id.thumbBtn)
        thumbBtn.clipToOutline = true

        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        previewView.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_UP) tapToFocus(ev.x, ev.y)
            true
        }

        recordBtn.setOnClickListener { toggleRecording() }
        flipBtn.setOnClickListener {
            if (recording != null) return@setOnClickListener
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            shutter48 = false
            bindCamera()
        }
        thumbBtn.setOnClickListener { openLastClip() }

        buildTabs()
        showTab(currentTab)
        updateFmtLabel()
        updateMicLabel()
        loadThumbnail()
        preview?.setSurfaceProvider(previewView.surfaceProvider)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun chip(text: String, selected: Boolean, onLong: (() -> Unit)? = null, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(if (selected) 0xFF0E1A26.toInt() else 0xFFFFFFFF.toInt())
            setBackgroundResource(if (selected) R.drawable.glass_chip_on else R.drawable.glass_chip)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }

    private fun buildTabs() {
        tabRow.removeAllViews()
        Tab.values().forEach { t ->
            val tv = TextView(this).apply {
                text = t.label
                textSize = 12f
                letterSpacing = 0.12f
                setTextColor(if (t == currentTab) 0xFFFFFFFF.toInt() else 0x99FFFFFF.toInt())
                setPadding(dp(10), dp(6), dp(10), dp(6))
                if (t == currentTab && panelOpen) setBackgroundResource(R.drawable.glass_chip)
                setOnClickListener {
                    if (t == currentTab) panelOpen = !panelOpen else panelOpen = true
                    currentTab = t
                    buildTabs()
                    showTab(t)
                }
            }
            tabRow.addView(tv)
        }
    }

    private fun showTab(t: Tab) {
        contentBox.visibility = if (panelOpen) View.VISIBLE else View.GONE
        chipRow.removeAllViews()
        sliderRow.visibility = View.VISIBLE
        when (t) {
            Tab.WARNA -> {
                ColorLook.values().forEach { look ->
                    chipRow.addView(chip(look.label, params.look == look) {
                        update(params.copy(look = look)); showTab(t)
                    })
                }
                setupSlider("Kekuatan", 0, 100, (params.lookAmount * 100).roundToInt(), { "$it%" }) {
                    update(params.copy(lookAmount = it / 100f))
                }
            }
            Tab.LENSA -> {
                LensFx.values().forEach { fx ->
                    chipRow.addView(chip(fx.label, params.lens == fx) {
                        update(params.copy(lens = fx)); showTab(t)
                    })
                }
                setupSlider("Kekuatan", 0, 100, (params.lensAmount * 100).roundToInt(), { "$it%" }) {
                    update(params.copy(lensAmount = it / 100f))
                }
            }
            Tab.FILEM -> {
                chipRow.addView(chip("Letterbox", params.letterbox) { update(params.copy(letterbox = !params.letterbox)); showTab(t) })
                chipRow.addView(chip("Film Grain", params.grain) { update(params.copy(grain = !params.grain)); showTab(t) })
                chipRow.addView(chip("Halation", params.halation) { update(params.copy(halation = !params.halation)); showTab(t) })
                chipRow.addView(chip("Vignette", params.vignette) { update(params.copy(vignette = !params.vignette)); showTab(t) })
                setupSlider("Grain", 0, 100, (params.grainAmount * 100).roundToInt(), { "$it%" }) {
                    update(params.copy(grainAmount = it / 100f))
                }
            }
            Tab.KAMERA -> {
                chipRow.addView(chip(if (fps24) "24 fps" else "30 fps", true) {
                    if (recording != null) return@chip
                    fps24 = !fps24
                    bindCamera(); showTab(t)
                })
                chipRow.addView(chip("Shutter 1/48", shutter48) { toggleShutter(); showTab(t) })
                chipRow.addView(chip(if (aeLocked) "Kunci AE/AF: ON" else "Kunci AE/AF", aeLocked) {
                    aeLocked = !aeLocked
                    applyCameraOptions(); showTab(t)
                })
                listOf(
                    "WB Auto" to CaptureRequest.CONTROL_AWB_MODE_AUTO,
                    "Siang" to CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
                    "Mendung" to CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
                    "Lampu Kuning" to CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                    "Lampu Putih" to CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
                ).forEach { (label, mode) ->
                    chipRow.addView(chip(label, wbMode == mode) {
                        wbMode = mode; applyCameraOptions(); showTab(t)
                    })
                }
                val range = camera?.cameraInfo?.exposureState?.exposureCompensationRange ?: Range(-6, 6)
                val lo = range.lower
                val hi = range.upper
                setupSlider("Terang", 0, hi - lo, evIndex - lo, { v ->
                    val ev = v + lo
                    if (ev > 0) "+$ev" else "$ev"
                }) { v ->
                    evIndex = v + lo
                    applyExposure()
                }
            }
            Tab.PRESET -> {
                sliderRow.visibility = View.GONE
                chipRow.addView(chip("+ Simpan", false) { askSavePreset() })
                (Preset.BUILT_IN + store.userPresets()).forEach { p ->
                    val onLong: (() -> Unit)? = if (p.builtIn) null else ({ askDeletePreset(p) })
                    chipRow.addView(chip(p.name, p.params == params, onLong) {
                        update(p.params); showTab(t)
                    })
                }
            }
        }
    }

    private fun setupSlider(label: String, min: Int, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        sliderLabel.text = label
        slider.setOnSeekBarChangeListener(null)
        slider.max = max - min
        slider.progress = (value - min).coerceIn(0, max - min)
        sliderValue.text = fmt(value)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                sliderValue.text = fmt(progress + min)
                onChange(progress + min)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun update(newParams: FilterParams) {
        params = newParams
        processor.params = newParams
        store.saveLast(newParams)
    }

    private fun askSavePreset() {
        val input = EditText(this).apply { hint = "Nama preset (cth: Senja Kampung)" }
        AlertDialog.Builder(this)
            .setTitle("Simpan preset")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                store.save(Preset(name, params))
                Toast.makeText(this, "Preset \"$name\" disimpan", Toast.LENGTH_SHORT).show()
                showTab(Tab.PRESET)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun askDeletePreset(p: Preset) {
        AlertDialog.Builder(this)
            .setTitle("Padam preset?")
            .setMessage("Padam \"${p.name}\"?")
            .setPositiveButton("Padam") { _, _ -> store.delete(p.name); showTab(Tab.PRESET) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updateFmtLabel() {
        fmtText.text = "1080p · $actualFps"
    }

    private fun updateMicLabel() {
        if (!::micText.isInitialized) return
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val external = am.getDevices(AudioManager.GET_DEVICES_INPUTS).any {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        micText.text = if (external) "MIC LUAR" else "MIC TELEFON"
    }

    // =====================================================================
    //  Kamera
    // =====================================================================

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun pickFpsRange(provider: ProcessCameraProvider, selector: CameraSelector): Range<Int>? {
        val target = if (fps24) 24 else 30
        val info = provider.availableCameraInfos.firstOrNull { it.lensFacing == lensFacing } ?: return null
        val ranges = info.supportedFrameRateRanges
        ranges.firstOrNull { it.lower == target && it.upper == target }?.let { return it }
        return ranges.filter { it.lower <= target && it.upper >= target }.minByOrNull { it.upper }
    }

    private fun currentRotation(): Int = previewView.display?.rotation ?: Surface.ROTATION_0

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        provider.unbindAll()
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val fpsRange = pickFpsRange(provider, selector)
        actualFps = when {
            fpsRange == null -> if (fps24) "24fps*" else "30fps*"
            fpsRange.lower == fpsRange.upper -> "${fpsRange.upper}fps"
            else -> "${if (fps24) 24 else 30}fps~"
        }
        updateFmtLabel()

        val previewBuilder = Preview.Builder().setTargetRotation(currentRotation())
        fpsRange?.let { previewBuilder.setTargetFrameRate(it) }
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { lastExposureNs = it }
                result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { lastIso = it }
            }
        })
        val pv = previewBuilder.build()
        pv.setSurfaceProvider(previewView.surfaceProvider)
        preview = pv

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
            .setTargetVideoEncodingBitRate(24_000_000)
            .build()
        val vcBuilder = VideoCapture.Builder(recorder).setTargetRotation(currentRotation())
        fpsRange?.let { vcBuilder.setTargetFrameRate(it) }
        val vc = vcBuilder.build()
        videoCapture = vc

        val group = UseCaseGroup.Builder()
            .addUseCase(pv)
            .addUseCase(vc)
            .addEffect(effect)
            .build()
        try {
            camera = provider.bindToLifecycle(this, selector, group)
            applyCameraOptions()
            applyExposure()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal buka kamera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun manualSensorSupported(): Boolean {
        val cam = camera ?: return false
        val caps = Camera2CameraInfo.from(cam.cameraInfo)
            .getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return false
        return caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
    }

    private fun toggleShutter() {
        if (!shutter48) {
            if (!manualSensorSupported()) {
                Toast.makeText(this, "Kamera ini tak benarkan kawalan shutter manual.", Toast.LENGTH_LONG).show()
                return
            }
            val target = SHUTTER_NS
            manualBaseIso = if (lastExposureNs > 0 && lastIso > 0)
                (lastIso.toDouble() * lastExposureNs / target).roundToInt() else 400
            evIndex = 0
            shutter48 = true
            Toast.makeText(this, "Shutter 1/48 ON. Guna slider Terang untuk laras.", Toast.LENGTH_SHORT).show()
        } else {
            shutter48 = false
            evIndex = 0
        }
        applyCameraOptions()
        applyExposure()
    }

    private fun applyCameraOptions() {
        val cam = camera ?: return
        val b = CaptureRequestOptions.Builder()
        b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wbMode)
        if (aeLocked) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
        }
        if (shutter48) {
            val c2info = Camera2CameraInfo.from(cam.cameraInfo)
            val isoRange = c2info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val expRange = c2info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            var iso = (manualBaseIso * 2.0.pow(evIndex / 3.0)).roundToInt()
            if (isoRange != null) iso = iso.coerceIn(isoRange.lower, isoRange.upper)
            var exp = SHUTTER_NS
            if (expRange != null) exp = exp.coerceIn(expRange.lower, expRange.upper)
            val frameNs = 1_000_000_000L / (if (fps24) 24 else 30)
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, frameNs)
        }
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
    }

    private fun applyExposure() {
        val cam = camera ?: return
        if (shutter48) {
            cam.cameraControl.setExposureCompensationIndex(0)
            applyCameraOptions()
        } else {
            val r = cam.cameraInfo.exposureState.exposureCompensationRange
            cam.cameraControl.setExposureCompensationIndex(evIndex.coerceIn(r.lower, r.upper))
        }
    }

    private fun tapToFocus(x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val builder = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
        if (aeLocked) builder.disableAutoCancel() else builder.setAutoCancelDuration(4, TimeUnit.SECONDS)
        cam.cameraControl.startFocusAndMetering(builder.build())

        val size = focusRing.layoutParams.width
        (focusRing.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = (x - size / 2).roundToInt()
            topMargin = (y - size / 2).roundToInt()
        }
        focusRing.requestLayout()
        focusRing.animate().cancel()
        focusRing.alpha = 1f
        focusRing.scaleX = 1.3f
        focusRing.scaleY = 1.3f
        focusRing.animate().scaleX(1f).scaleY(1f).setDuration(200).withEndAction {
            focusRing.animate().alpha(0f).setStartDelay(900).setDuration(400).start()
        }.start()
    }

    // =====================================================================
    //  Rakaman
    // =====================================================================

    private fun toggleRecording() {
        val active = recording
        if (active != null) {
            active.stop()
            recording = null
            return
        }
        val vc = videoCapture ?: return
        vc.targetRotation = currentRotation()
        val name = "CinePro_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/CinePro")
        }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()
        var pending = vc.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordBtn.setBackgroundResource(R.drawable.rec_active)
                    recDot.alpha = 1f
                    recDot.animate().alpha(0.25f).setDuration(600).withEndAction(blink).start()
                }
                is VideoRecordEvent.Status -> {
                    val secs = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                    timerText.text = String.format(Locale.US, "%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60)
                    val amp = event.recordingStats.audioStats.audioAmplitude
                    audioMeter.progress = (amp * 100).roundToInt().coerceIn(0, 100)
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    recordBtn.setBackgroundResource(R.drawable.rec_idle)
                    recDot.animate().cancel()
                    recDot.alpha = 0.35f
                    audioMeter.progress = 0
                    timerText.text = "00:00:00"
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    if (!event.hasError()) {
                        lastUri = event.outputResults.outputUri
                        loadThumbnail()
                        Toast.makeText(this, "Disimpan dalam Galeri › CinePro", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Rakaman gagal (kod ${event.error})", Toast.LENGTH_LONG).show()
                    }
                }
                else -> Unit
            }
        }
    }

    private val blink: Runnable = object : Runnable {
        override fun run() {
            if (recording == null) return
            recDot.animate().alpha(if (recDot.alpha < 0.5f) 1f else 0.25f).setDuration(600).withEndAction(this).start()
        }
    }

    private fun findLatestClip(): Uri? {
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val sel = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        return try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, sel, arrayOf("DCIM/CinePro%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { c ->
                if (c.moveToFirst()) Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0).toString()) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadThumbnail() {
        val uri = lastUri ?: findLatestClip()?.also { lastUri = it } ?: return
        try {
            thumbBtn.setImageBitmap(contentResolver.loadThumbnail(uri, Size(160, 160), null))
        } catch (e: Exception) {
            thumbBtn.setImageDrawable(null)
        }
    }

    private fun openLastClip() {
        val uri = lastUri ?: run {
            Toast.makeText(this, "Belum ada klip. Tekan butang merah untuk rakam.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(intent) } catch (e: Exception) {
            Toast.makeText(this, "Tiada app untuk main video.", Toast.LENGTH_SHORT).show()
        }
    }

    // =====================================================================
    //  Putaran skrin
    // =====================================================================

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (recording != null) return
        setupUi()
        previewView.post {
            val rot = currentRotation()
            preview?.targetRotation = rot
            videoCapture?.targetRotation = rot
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        recording?.stop()
        (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(audioCallback)
        cameraProvider?.unbindAll()
        processor.release()
    }

    companion object {
        private const val SHUTTER_NS = 1_000_000_000L / 48
    }
}
