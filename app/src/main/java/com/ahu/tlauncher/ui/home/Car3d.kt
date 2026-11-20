package com.ahu.tlauncher.ui.home

import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ahu.tlauncher.data.CarPaint
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAG = "Car3d"

/** Loading and availability of the 3D car. The photos stay in use whenever this says no. */
internal object Car3d {
    private const val MODEL = "models/avanza.glb"

    /** Set once creating the renderer failed on this head unit: no new attempts until the app restarts. */
    @Volatile var failed = false
    @Volatile private var model: ByteBuffer? = null
    private var libsLoaded = false

    fun available(ctx: Context): Boolean = !failed && !crashedBefore(ctx) && supportsGles3(ctx)

    /*
     * Crash guard. A problem inside the native renderer can end the whole app, which can't be caught, and as
     * the home app it would then restart into the same crash. So a mark is written (synchronously) before
     * the 3D car starts and removed after its first frames; a mark left over from last time means that start
     * didn't survive, and the photos are used from then on. Settings → Display → Car → 3D tries again.
     */
    private const val GUARD_PREFS = "car3d_guard"
    private const val STARTING = "starting"
    private const val CRASHED = "crashed"

    private fun guard(ctx: Context) = ctx.applicationContext.getSharedPreferences(GUARD_PREFS, Context.MODE_PRIVATE)

    private fun crashedBefore(ctx: Context): Boolean {
        val g = guard(ctx)
        if (g.getBoolean(STARTING, false)) g.edit().putBoolean(CRASHED, true).putBoolean(STARTING, false).commit()
        return g.getBoolean(CRASHED, false)
    }

    fun markStarting(ctx: Context) { guard(ctx).edit().putBoolean(STARTING, true).commit() }
    fun markStarted(ctx: Context) { guard(ctx).edit().putBoolean(STARTING, false).apply() }

    /** Choosing 3D in Settings again gives it another try after a crash. */
    fun resetCrashGuard(ctx: Context) { guard(ctx).edit().clear().apply(); failed = false }

    fun crashed(ctx: Context): Boolean = guard(ctx).getBoolean(CRASHED, false)

    /** The glb in a direct buffer (read once, kept for the next time the panel is shown). */
    suspend fun model(ctx: Context): ByteBuffer? = model ?: withContext(Dispatchers.IO) {
        runCatching {
            val bytes = ctx.assets.open(MODEL).use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
        }.onFailure { Log.w(TAG, "Couldn't read $MODEL", it); failed = true }.getOrNull()
    }?.also { model = it }

    fun loadLibraries() {
        if (libsLoaded) return
        Gltfio.init()
        Utils.init()
        libsLoaded = true
    }
}

/** Whether the 3D car is on screen yet, and the user's orbit (radians, full screen only). */
@Stable
internal class Car3dState {
    var ready by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var yaw by mutableFloatStateOf(0f)
    var elevation by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    /** Bumped on every touch, restarting the wait before the car turns back. */
    var touches by mutableIntStateOf(0)
    /** The car still turning after a flick. */
    var fling: Job? = null

    fun stopFling() { fling?.cancel(); fling = null }

    /**
     * The car follows the finger: dragging right turns its near side to the right (the camera circles to its left,
     * which is +yaw in [CarFraming.eye]); dragging down tips its roof toward you.
     */
    fun drag(dx: Float, dy: Float, viewW: Float, viewH: Float) {
        yaw += dx / viewW * 2 * PI.toFloat()
        elevation = (elevation + dy / viewH * PI.toFloat() / 2)
            .coerceIn(CarFraming.ORBIT_EL_MIN.toFloat(), CarFraming.ORBIT_EL_MAX.toFloat())
    }
}

/**
 * The 3D car scene behind the car panel, like the prototype's vehicle canvas: as tall as the panel and as
 * wide as the screen (so it never resizes while the panel glides), car centred on the visible part.
 * In full screen, dragging on the car orbits it; double-tap or 5 s without touching turns it back.
 */
@Composable
internal fun Car3dLayer(
    car: Car3dState,
    paint: CarPaint,
    dark: Boolean,
    lowPerf: Boolean,
    /** The head unit says the headlights are on: lamps glow, and the beams light the night floor. */
    lightsOn: Boolean,
    progress: Float,
    visibleWidthPx: Float,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val windowW = ctx.resources.displayMetrics.widthPixels
    val glb by produceState<ByteBuffer?>(null) { value = Car3d.model(ctx) ?: run { car.failed = true; null } }
    val holder = remember { arrayOfNulls<Car3dRenderer>(1) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val full = progress >= 0.999f

    // Back to the default view: when the panel reaches split view, or 5 s after the last touch (700 ms ease-out).
    LaunchedEffect(progress <= 0f) { if (progress <= 0f) { car.stopFling(); car.yaw = 0f; car.elevation = 0f } }
    LaunchedEffect(car.touches, car.dragging, full) {
        if (car.dragging || !full || (car.yaw == 0f && car.elevation == 0f)) return@LaunchedEffect
        delay(CarFraming.RETURN_DELAY_MS)
        returnToView(car)
    }

    // Lamps fade on and off, and a new paint blends in (instant in low-performance mode).
    val lampLevel by animateFloatAsState(
        if (lightsOn) 1f else 0f, if (lowPerf) snap<Float>() else tween<Float>(CarFraming.LAMP_FADE_MS, easing = com.ahu.tlauncher.ui.theme.TMotion.EaseOutCubic), label = "lamps",
    )
    val paintLook = animatedPaint(paint, lowPerf)

    // Frames drawn while the view was still (almost) invisible may not stay on screen: draw again once it shows.
    LaunchedEffect(car.ready) { if (car.ready) { delay(600); holder[0]?.refresh() } }

    val buffer = glb ?: return
    var viewH by remember { mutableIntStateOf(0) }
    var redraws by remember { mutableIntStateOf(0) }
    Box(modifier.drawWithContent { redraws; drawContent() }) {
        AndroidView(
            factory = { c ->
                TextureView(c).also { tv ->
                    Car3d.markStarting(c)
                    holder[0] = runCatching { Car3dRenderer(tv, buffer, { Car3d.markStarted(c); car.ready = true }, { redraws++ }) }
                        .onFailure { Log.w(TAG, "3D car unavailable", it); Car3d.markStarted(c); Car3d.failed = true; car.failed = true }
                        .getOrNull()
                    tv.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> viewH = v.height }
                }
            },
            modifier = Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start, unbounded = true)
                .width(with(density) { windowW.toDp() }),
            onRelease = { _: TextureView -> holder[0]?.destroy(); holder[0] = null },
            update = {
                holder[0]?.update(
                    Car3dRenderer.Frame(paintLook, dark, lowPerf, lightsOn, lampLevel, progress, car.yaw.toDouble(), car.elevation.toDouble(), visibleWidthPx),
                )
            },
        )
        // Touch area over the car, full screen only (the panel swipe works everywhere else).
        if (full && car.ready && viewH > 0) {
            val b = CarFraming.screenBounds(1f, car.yaw.toDouble(), car.elevation.toDouble(), viewH, visibleWidthPx)
            val pad = with(density) { 24.dp.toPx() }
            val viewWf = windowW.toFloat()
            Box(
                Modifier
                    .offset { IntOffset((b[0] - pad).roundToInt(), (b[1] - pad).roundToInt()) }
                    .size(with(density) { (b[2] - b[0] + 2 * pad).toDp() }, with(density) { (b[3] - b[1] + 2 * pad).toDp() })
                    .pointerInput(Unit) {
                        // The touch area moves with the car, so speed is measured on the summed drag, not on positions.
                        val tracker = VelocityTracker()
                        var x = 0f
                        detectDragGestures(
                            onDragStart = { car.stopFling(); tracker.resetTracking(); x = 0f; car.dragging = true },
                            onDragEnd = {
                                car.dragging = false; car.touches++
                                val speed = CarFraming.flingRadPerSec(tracker.calculateVelocity().x, viewWf)
                                car.fling = scope.launch { flingYaw(car, speed) }
                            },
                            onDragCancel = { car.dragging = false; car.touches++ },
                        ) { change, d ->
                            change.consume()
                            x += d.x; tracker.addPosition(change.uptimeMillis, Offset(x, 0f))
                            car.drag(d.x, d.y, viewWf, viewH.toFloat())
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scope.launch { returnToView(car) } }) },
            )
        }
    }
}

/** Keeps the car turning after a flick, slowing down like a turntable. */
private suspend fun flingYaw(car: Car3dState, radPerSec: Float) {
    if (abs(radPerSec) < CarFraming.FLING_MIN_RAD_S) return
    Animatable(car.yaw).animateDecay(radPerSec, exponentialDecay(frictionMultiplier = CarFraming.FLING_FRICTION)) { car.yaw = value }
}

/** The paint shown: blends from the previous one when it changes (where it had got to, if still blending). */
@Composable
private fun animatedPaint(paint: CarPaint, lowPerf: Boolean): PaintLook {
    val target = remember(paint) { PaintLook.of(paint) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val t = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = from.lerp(to, t.value); to = target
        if (lowPerf) { t.snapTo(1f); return@LaunchedEffect }
        t.snapTo(0f)
        t.animateTo(1f, tween(CarFraming.PAINT_FADE_MS, easing = com.ahu.tlauncher.ui.theme.TMotion.EaseOutCubic))
    }
    return from.lerp(to, t.value)
}

/** Turns the car back to its view: yaw the short way round, 700 ms ease-out cubic (the prototype's return). */
private suspend fun returnToView(car: Car3dState) {
    car.stopFling()
    val startYaw = atan2(sin(car.yaw.toDouble()), cos(car.yaw.toDouble())).toFloat()
    val startEl = car.elevation
    val anim = Animatable(0f)
    anim.animateTo(1f, tween(CarFraming.RETURN_MS, easing = com.ahu.tlauncher.ui.theme.TMotion.EaseOutCubic)) {
        val k = 1f - value
        car.yaw = startYaw * k
        car.elevation = startEl * k
    }
}

/** Filament scene: the car, its reflection and floor, studio lighting; renders only when something changed. */
internal class Car3dRenderer(
    textureView: TextureView,
    glb: ByteBuffer,
    private val onFirstFrames: () -> Unit,
    private val onFrame: () -> Unit,
) : Choreographer.FrameCallback {
    /** Everything a frame depends on. */
    data class Frame(
        val paint: PaintLook,
        val dark: Boolean,
        val lowPerf: Boolean,
        /** Headlights on: the beams show on the night floor. */
        val lightsOn: Boolean,
        /** How lit the lamps are, 0–1 (fades with [lightsOn]). */
        val lampLevel: Float,
        val progress: Float,
        val yaw: Double,
        val elevation: Double,
        val visibleW: Float,
    )

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val cameraEntity: Int
    private val camera: Camera
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null
    private val materials: UbershaderProvider
    private val assetLoader: AssetLoader
    private val resourceLoader: ResourceLoader
    private val asset: FilamentAsset
    private val keyLight: Int
    private val fillLight: Int
    private val reflections: Texture
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val mirror: IntArray
    private val floorDark: IntArray
    private val floorLight: IntArray
    private val beams: IntArray

    private var width = 0
    private var height = 0
    private var frame: Frame? = null
    private var paintShown: PaintLook? = null
    private var mirrorShown = true
    private var darkShown: Boolean? = null
    private var lowPerfShown: Boolean? = null
    private var beamsShown = true // every part starts in the scene
    private var lampsShown: Float? = null
    private var framesLeft = 0
    private var scheduled = false
    private var shown = false
    private var destroyed = false

    init {
        Car3d.loadLibraries()
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        cameraEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)
        view.scene = scene
        view.camera = camera
        // Transparent: the panel's own background shows behind the floor, which fades out at its edge.
        view.blendMode = View.BlendMode.TRANSLUCENT
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0) }
        view.setShadowingEnabled(false)
        // The prototype's look: ACES with its brightness boost, exposure 0.9 (folded into the light levels).
        colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.ACESLegacy()).build(engine)
        view.colorGrading = colorGrading
        camera.setExposure(16f, 1f / 125f, 100f)

        reflections = studioReflections(engine)
        indirectLight = IndirectLight.Builder().reflections(reflections).intensity(LIGHT_SCALE * 0.85f).build(engine)
        scene.indirectLight = indirectLight
        // Key light high at the front-right (casts the floor shadow baked into the floor), cool fill from behind-left.
        keyLight = directional(engine, 0xFFF8F2, 1.25f, 3.5f, 6f, 4f)
        fillLight = directional(engine, 0xDBE5FA, 0.7f, -3f, 3f, -4f)
        scene.addEntity(keyLight)
        scene.addEntity(fillLight)

        materials = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materials, EntityManager.get())
        resourceLoader = ResourceLoader(engine)
        asset = assetLoader.createAsset(glb) ?: error("Couldn't parse the car model")
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        mirror = renderablesUnder("mirror")
        floorDark = renderablesUnder("floor_dark")
        floorLight = renderablesUnder("floor_light")
        beams = renderablesUnder("floor_beams")
        // The beams are drawn after (over) the night floor they lie on.
        engine.renderableManager.let { rm -> for (e in beams) rm.setPriority(rm.getInstance(e), 6) }

        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                requestFrames()
            }

            override fun onDetachedFromSurface() {
                swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }
                swapChain = null
            }

            override fun onResized(w: Int, h: Int) {
                width = w; height = h
                view.viewport = Viewport(0, 0, w, h)
                requestFrames()
            }
        }
        uiHelper.attachTo(textureView)
    }

    /** Renderable entities of the named node and everything under it. */
    private fun renderablesUnder(name: String): IntArray {
        val rm = engine.renderableManager
        val tm = engine.transformManager
        val root = asset.getFirstEntityByName(name)
        if (root == 0) return IntArray(0)
        return asset.entities.filter { e ->
            if (!rm.hasComponent(e)) return@filter false
            var t = e
            while (t != 0) {
                if (t == root) return@filter true
                val inst = tm.getInstance(t)
                if (inst == 0) break
                t = tm.getParent(inst)
            }
            false
        }.toIntArray()
    }

    private fun directional(engine: Engine, rgb: Int, threeIntensity: Float, x: Float, y: Float, z: Float): Int {
        val e = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(CarFraming.srgbToLinear(rgb shr 16 and 0xFF), CarFraming.srgbToLinear(rgb shr 8 and 0xFF), CarFraming.srgbToLinear(rgb and 0xFF))
            .intensity(LIGHT_SCALE * threeIntensity)
            .direction(-x, -y, -z) // shining from (x, y, z) toward the car
            .castShadows(false)
            .build(engine, e)
        return e
    }

    fun refresh() { if (!destroyed) requestFrames() }

    fun update(f: Frame) {
        if (destroyed || f == frame) return
        frame = f
        requestFrames()
    }

    private fun applyScene(f: Frame) {
        if (f.paint != paintShown) { applyPaint(f.paint); paintShown = f.paint }
        if (f.dark != darkShown) {
            if (f.dark) { scene.addEntities(floorDark); scene.removeEntities(floorLight) }
            else { scene.addEntities(floorLight); scene.removeEntities(floorDark) }
            darkShown = f.dark
        }
        // Headlights on: the beams on the night floor (the day floor has none), and the lamps glow.
        val wantBeams = f.dark && f.lightsOn
        if (wantBeams != beamsShown) {
            if (wantBeams) scene.addEntities(beams) else scene.removeEntities(beams)
            beamsShown = wantBeams
        }
        if (f.lampLevel != lampsShown) { applyLamps(f.lampLevel); lampsShown = f.lampLevel }
        // The reflection only shows in full screen (the prototype fades it in with the panel).
        val wantMirror = f.progress > 0.15f
        if (wantMirror != mirrorShown) {
            if (wantMirror) scene.addEntities(mirror) else scene.removeEntities(mirror)
            mirrorShown = wantMirror
        }
        if (f.lowPerf != lowPerfShown) {
            // Low-performance mode: no anti-aliasing, glow or contact shadows. Frames are only drawn when something
            // changes, so the extra work only counts while the car moves.
            val q = if (f.lowPerf) View.QualityLevel.LOW else View.QualityLevel.HIGH
            view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply { enabled = !f.lowPerf; sampleCount = 4 }
            view.bloomOptions = view.bloomOptions.apply {
                enabled = !f.lowPerf; strength = 0.12f; threshold = true; levels = 5; resolution = 512; quality = q
            }
            // Soft contact shadows (ambient occlusion): wheel arches, panel gaps, under the bumpers and mirrors.
            view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
                enabled = !f.lowPerf; radius = 0.3f; intensity = 1f; power = 1.2f; resolution = 0.5f
                quality = View.QualityLevel.MEDIUM; lowPassFilter = View.QualityLevel.MEDIUM; upsampling = View.QualityLevel.HIGH
            }
            // 16-bit colour while rendering: no banding in the paint's gradients and the floor's fade.
            view.renderQuality = view.renderQuality.apply { hdrColorBuffer = if (f.lowPerf) View.QualityLevel.MEDIUM else View.QualityLevel.HIGH }
            lowPerfShown = f.lowPerf
        }
    }

    private fun applyPaint(p: PaintLook) {
        val mi = asset.instance.materialInstances.firstOrNull { it.name == "paint" } ?: run { Log.w(TAG, "No paint material in the model"); return }
        val r = p.r; val g = p.g; val b = p.b
        val m = mi.material
        if (m.hasParameter("baseColorFactor")) mi.setParameter("baseColorFactor", r, g, b, 1f)
        if (m.hasParameter("metallicFactor")) mi.setParameter("metallicFactor", p.metallic)
        if (m.hasParameter("roughnessFactor")) mi.setParameter("roughnessFactor", p.roughness)
        if (m.hasParameter("clearCoatFactor")) mi.setParameter("clearCoatFactor", 1f)
        if (m.hasParameter("clearCoatRoughnessFactor")) mi.setParameter("clearCoatRoughnessFactor", 0.03f)
    }

    /**
     * How lit the lamps are (0–1): headlights warm white, fog lights yellow (#FFC400), tail lights red. Emissive
     * light (the glow on the brightest parts comes from the bloom).
     */
    private fun applyLamps(level: Float) {
        val all = asset.instance.materialInstances
        for ((name, rgb, strength) in LAMPS) {
            val mi = all.firstOrNull { it.name == name } ?: continue
            val k = strength * level
            if (mi.material.hasParameter("emissiveFactor")) mi.setParameter("emissiveFactor", rgb[0] * k, rgb[1] * k, rgb[2] * k)
            if (mi.material.hasParameter("emissiveStrength")) mi.setParameter("emissiveStrength", 1f)
        }
    }

    /** A few frames after each change: some drivers show a new TextureView frame one frame late. */
    private fun requestFrames() {
        framesLeft = 3
        if (!scheduled && !destroyed) { scheduled = true; Choreographer.getInstance().postFrameCallback(this) }
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (destroyed) return
        val sc = swapChain ?: return
        val f = frame ?: return
        if (!uiHelper.isReadyToRender || width == 0 || height == 0 || f.visibleW <= 0f) return
        applyScene(f)
        val e = CarFraming.eye(f.progress, f.yaw, f.elevation)
        val t = CarFraming.target()
        camera.lookAt(e[0], e[1], e[2], t[0], t[1], t[2], 0.0, 1.0, 0.0)
        // While orbited, the camera backs off and re-centres to keep the car clear of the panel's readouts and cards.
        val fit = CarFraming.fit(f.progress, f.yaw, f.elevation, height, f.visibleW)
        camera.setCustomProjection(CarFraming.projection(width, height, f.visibleW, fit), CarFraming.NEAR, CarFraming.FAR)
        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            framesLeft--
            if (!shown) { shown = true; onFirstFrames() }
        }
        // Inside Compose's layers the TextureView isn't always redrawn when a frame arrives, and frames left
        // waiting block the renderer's next ones (until something else redraws the panel). Asking the
        // composable around it to redraw makes sure each frame is taken.
        onFrame()
        if (framesLeft > 0) { scheduled = true; Choreographer.getInstance().postFrameCallback(this) }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        Choreographer.getInstance().removeFrameCallback(this)
        uiHelper.detach()
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null
        scene.removeEntities(asset.entities)
        assetLoader.destroyAsset(asset)
        materials.destroyMaterials()
        materials.destroy()
        assetLoader.destroy()
        resourceLoader.destroy()
        for (l in intArrayOf(keyLight, fillLight)) { engine.lightManager.destroy(l); EntityManager.get().destroy(l) }
        engine.destroyIndirectLight(indirectLight)
        engine.destroyTexture(reflections)
        engine.destroyColorGrading(colorGrading)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    private companion object {
        fun srgb(rgb: Int) = floatArrayOf(
            CarFraming.srgbToLinear(rgb shr 16 and 0xFF), CarFraming.srgbToLinear(rgb shr 8 and 0xFF), CarFraming.srgbToLinear(rgb and 0xFF),
        )

        /** Lamp materials in the model, their light colour (linear) and brightness when lit. */
        val LAMPS = listOf(
            Triple("headlight", floatArrayOf(1f, 0.95f, 0.85f), 60f),
            Triple("foglight", srgb(0xFFC400), 40f), // selective yellow
            Triple("taillight", floatArrayOf(1f, 0.04f, 0.02f), 30f),
        )

        /**
         * three.js light units to Filament's at this camera exposure (f/16, 1/125 s, ISO 100), times the
         * prototype's tone-mapping exposure of 0.9: a three.js intensity of 1 becomes this many lux.
         */
        const val LIGHT_SCALE = 0.9f * 1.2f * 32_000f
    }
}

/** Prefiltered reflections of the studio in [studioRadiance], for the car's paint, glass and chrome. */
private fun studioReflections(engine: Engine): Texture {
    val w = 256
    val h = 128
    val buf = ByteBuffer.allocateDirect(w * h * 4 * 4).order(ByteOrder.nativeOrder())
    val rgb = FloatArray(3)
    for (j in 0 until h) {
        val lat = PI / 2 - (j + 0.5) * PI / h
        for (i in 0 until w) {
            val lon = (i + 0.5) * 2 * PI / w - PI
            studioRadiance(cos(lat) * sin(lon), sin(lat), cos(lat) * cos(lon), rgb)
            buf.putFloat(rgb[0]); buf.putFloat(rgb[1]); buf.putFloat(rgb[2]); buf.putFloat(1f)
        }
    }
    buf.flip()
    // The cubemap conversion needs the full mip chain (256 px down to 1 px: 9 levels), generated on the GPU.
    val levels = 1 + (31 - Integer.numberOfLeadingZeros(maxOf(w, h)))
    val equirect = Texture.Builder().width(w).height(h).levels(levels).sampler(Texture.Sampler.SAMPLER_2D)
        .format(Texture.InternalFormat.RGBA16F)
        .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
        .build(engine)
    equirect.setImage(engine, 0, Texture.PixelBufferDescriptor(buf, Texture.Format.RGBA, Texture.Type.FLOAT))
    equirect.generateMipmaps(engine)
    val ctx = IBLPrefilterContext(engine)
    val toCube = IBLPrefilterContext.EquirectangularToCubemap(ctx)
    val specular = IBLPrefilterContext.SpecularFilter(ctx)
    val cube = toCube.run(equirect)
    val filtered = specular.run(cube)
    engine.destroyTexture(equirect)
    engine.destroyTexture(cube)
    toCube.destroy()
    specular.destroy()
    ctx.destroy()
    return filtered
}

/** A soft light panel of the studio: centre, size (w × h m), linear colour × strength, facing the car. */
private class StudioPanel(cx: Double, cy: Double, cz: Double, val w: Double, val h: Double, rgb: Int, strength: Float) {
    val c = doubleArrayOf(cx, cy, cz)
    val n: DoubleArray
    val right: DoubleArray
    val up: DoubleArray
    val color = floatArrayOf(
        CarFraming.srgbToLinear(rgb shr 16 and 0xFF) * strength,
        CarFraming.srgbToLinear(rgb shr 8 and 0xFF) * strength,
        CarFraming.srgbToLinear(rgb and 0xFF) * strength,
    )

    init {
        // Faces the point (0, 0.6, 0), like three.js lookAt: z toward it, x = up × z, y = z × x.
        n = norm(doubleArrayOf(-cx, 0.6 - cy, -cz))
        right = norm(cross(doubleArrayOf(0.0, 1.0, 0.0), n))
        up = cross(n, right)
    }

    /** Whether the ray from the car's centre in direction [d] hits this panel. */
    fun hit(dx: Double, dy: Double, dz: Double): Boolean {
        val denom = dx * n[0] + dy * n[1] + dz * n[2]
        if (denom >= 0) return false // the panel faces the car: rays toward it travel against its normal
        val t = (c[0] * n[0] + c[1] * n[1] + c[2] * n[2]) / denom
        if (t <= 0) return false
        val px = dx * t - c[0]; val py = dy * t - c[1]; val pz = dz * t - c[2]
        return abs(px * right[0] + py * right[1] + pz * right[2]) <= w / 2 && abs(px * up[0] + py * up[1] + pz * up[2]) <= h / 2
    }

    private companion object {
        fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
        fun norm(a: DoubleArray) = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).let { l -> doubleArrayOf(a[0] / l, a[1] / l, a[2] / l) }
    }
}

/** The prototype's studio (x and z mirrored: its car faces −Z, ours +Z): four soft panels in a dark room. */
private val STUDIO = listOf(
    StudioPanel(3.0, 6.0, 1.0, 8.0, 4.0, 0xFFF8EF, 4f),
    StudioPanel(-4.0, 3.5, -1.0, 2.2, 7.0, 0xDCE8FF, 2.8f),
    StudioPanel(6.0, 2.8, 4.0, 5.0, 2.5, 0xFFFFFF, 2f),
    StudioPanel(-1.0, 4.0, 5.0, 7.0, 0.8, 0xF2F5FF, 3f),
)

/**
 * Light arriving from direction (x, y, z) (unit vector, y up), linear RGB in three.js units, into [out]:
 * the room (#151A22), the prototype's hemisphere light folded in, and the four panels.
 */
internal fun studioRadiance(x: Double, y: Double, z: Double, out: FloatArray) {
    // Hemisphere light #DDE4F1 over #111318 at 0.35, as the matching ambient glow (÷ π, ÷ the 0.85 env strength).
    val sky = ((y * 0.5 + 0.5).coerceIn(0.0, 1.0)).toFloat()
    val hemi = 0.35f / PI.toFloat() / 0.85f
    out[0] = 0.0075f + hemi * (0.0056f + (0.723f - 0.0056f) * sky)
    out[1] = 0.0103f + hemi * (0.0065f + (0.776f - 0.0065f) * sky)
    out[2] = 0.0160f + hemi * (0.0091f + (0.880f - 0.0091f) * sky)
    for (p in STUDIO) if (p.hit(x, y, z)) { out[0] += p.color[0]; out[1] += p.color[1]; out[2] += p.color[2] }
}
