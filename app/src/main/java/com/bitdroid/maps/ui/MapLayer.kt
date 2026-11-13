package com.bitdroid.maps.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.Location
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bitdroid.maps.data.LatLon
import com.bitdroid.maps.data.Place
import com.bitdroid.maps.data.Route
import com.bitdroid.maps.data.Servers
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Everything the app does to the map: dark styling, route lines, pins, camera and the location puck. */
class MapController(private val context: Context) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var locationOn = false
    private var routeSrc: GeoJsonSource? = null
    private var altSrc: GeoJsonSource? = null
    private var pinSrc: GeoJsonSource? = null
    private var destSrc: GeoJsonSource? = null
    private val pending = ArrayList<() -> Unit>()
    /** Called when the user drags the map while it was following them. */
    var onTrackingDismissed: (() -> Unit)? = null
    var ready = false
        private set

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    fun attach(m: MapLibreMap, s: Style) {
        map = m
        style = s
        recolor(s)
        addOverlays(s)
        m.uiSettings.isLogoEnabled = false
        m.uiSettings.isCompassEnabled = true
        m.uiSettings.compassGravity = Gravity.TOP or Gravity.END
        m.uiSettings.setCompassMargins(0, dp(150), dp(18), 0)
        m.uiSettings.attributionGravity = Gravity.TOP or Gravity.START
        m.uiSettings.setAttributionMargins(dp(14), dp(150), 0, 0)
        m.uiSettings.isAttributionEnabled = true
        ready = true
        pending.forEach { it() }
        pending.clear()
    }

    fun whenReady(block: () -> Unit) { if (ready) block() else pending.add(block) }

    // ---------- styling ----------

    /** Recolors OpenFreeMap's dark style to bitMaps' palette (works by layer type + name). */
    private fun recolor(s: Style) {
        for (layer in s.layers) runCatching { recolorLayer(layer) }
    }

    private fun recolorLayer(layer: Layer) {
        val id = layer.id.lowercase()
        fun has(vararg words: String) = words.any { id.contains(it) }
        when (layer) {
            is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(Color.parseColor("#0C0F15")))
            is FillLayer -> {
                val c = when {
                    has("water", "ocean", "river", "lake") -> "#0D1828"
                    has("park", "wood", "forest", "grass", "landcover", "green", "pitch", "cemetery") -> "#0F1D19"
                    has("building") -> "#161B24"
                    has("ice", "glacier") -> "#141922"
                    else -> "#0E1117"
                }
                layer.setProperties(PropertyFactory.fillColor(Color.parseColor(c)), PropertyFactory.fillOutlineColor(Color.parseColor(c)))
            }
            is FillExtrusionLayer -> layer.setProperties(PropertyFactory.fillExtrusionColor(Color.parseColor("#1A2030")))
            is LineLayer -> {
                val c = when {
                    has("water", "river", "stream", "canal") -> "#12233A"
                    has("boundary", "admin") -> "#2A3140"
                    has("rail", "transit") -> "#1A1F28"
                    has("motorway", "trunk") && has("casing") -> "#1E1B33"
                    has("motorway", "trunk") -> "#3B3470"
                    has("casing") -> "#12161E"
                    has("path", "track", "pedestrian", "footway", "cycle") -> "#1A1F28"
                    has("highway", "road", "street", "transportation", "minor", "major", "primary", "secondary", "tertiary", "service") -> "#222936"
                    else -> null
                }
                if (c != null) layer.setProperties(PropertyFactory.lineColor(Color.parseColor(c)))
            }
            is SymbolLayer -> {
                val c = when {
                    has("water", "ocean", "sea", "lake") -> "#4D6A93"
                    has("country", "city", "state", "continent") -> "#C5CBD6"
                    else -> "#8B93A3"
                }
                layer.setProperties(
                    PropertyFactory.textColor(Color.parseColor(c)),
                    PropertyFactory.textHaloColor(Color.parseColor("#0C0F15")),
                    PropertyFactory.textHaloWidth(1.2f),
                )
            }
        }
    }

    /** Line width that grows as you zoom in, so the route stays bold and smooth up close. */
    private fun byZoom(at12: Float, at19: Float): Expression = Expression.interpolate(
        Expression.exponential(1.4f), Expression.zoom(),
        Expression.stop(12f, at12), Expression.stop(19f, at19),
    )

    private fun addOverlays(s: Style) {
        val firstLabel = s.layers.firstOrNull { it is SymbolLayer }?.id
        fun below(layer: Layer) { if (firstLabel != null) s.addLayerBelow(layer, firstLabel) else s.addLayer(layer) }

        val alt = GeoJsonSource("bm-alt", GeoJsonOptions())
        val route = GeoJsonSource("bm-route", GeoJsonOptions().withLineMetrics(true))
        val pin = GeoJsonSource("bm-pin", GeoJsonOptions())
        val dest = GeoJsonSource("bm-dest", GeoJsonOptions())
        s.addSource(alt); s.addSource(route); s.addSource(pin); s.addSource(dest)
        altSrc = alt; routeSrc = route; pinSrc = pin; destSrc = dest

        below(
            LineLayer("bm-alt-line", "bm-alt").withProperties(
                PropertyFactory.lineColor(Color.parseColor("#4A5263")),
                PropertyFactory.lineWidth(byZoom(5f, 16f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineOpacity(0.85f),
            ),
        )
        val gradient = Expression.interpolate(
            Expression.linear(), Expression.lineProgress(),
            Expression.stop(0f, Expression.color(Color.parseColor("#7C4DFF"))),
            Expression.stop(0.55f, Expression.color(Color.parseColor("#2F6BFF"))),
            Expression.stop(1f, Expression.color(Color.parseColor("#22D3EE"))),
        )
        below(
            LineLayer("bm-route-glow", "bm-route").withProperties(
                PropertyFactory.lineGradient(gradient),
                PropertyFactory.lineWidth(byZoom(14f, 40f)),
                PropertyFactory.lineBlur(byZoom(8f, 22f)),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        below(
            LineLayer("bm-route-line", "bm-route").withProperties(
                PropertyFactory.lineGradient(gradient),
                PropertyFactory.lineWidth(byZoom(6f, 20f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        s.addLayer(
            CircleLayer("bm-pin-dot", "bm-pin").withProperties(
                PropertyFactory.circleRadius(11f),
                PropertyFactory.circleColor(Color.parseColor("#F472B6")),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3.5f),
            ),
        )
        s.addLayer(
            CircleLayer("bm-dest-dot", "bm-dest").withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor(Color.parseColor("#22D3EE")),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3.5f),
            ),
        )
    }

    // ---------- content ----------

    private val empty get() = FeatureCollection.fromFeatures(emptyArray<Feature>())

    private fun line(points: List<LatLon>) =
        Feature.fromGeometry(LineString.fromLngLats(smooth(points).map { Point.fromLngLat(it.lon, it.lat) }))

    /** Rounds sharp corners (two passes of Chaikin's corner cutting) so turns draw as smooth curves. */
    private fun smooth(points: List<LatLon>): List<LatLon> {
        var pts = points
        repeat(2) {
            if (pts.size < 3) return pts
            val out = ArrayList<LatLon>(pts.size * 2)
            out.add(pts.first())
            for (i in 0 until pts.size - 1) {
                val a = pts[i]; val b = pts[i + 1]
                out.add(LatLon(0.75 * a.lat + 0.25 * b.lat, 0.75 * a.lon + 0.25 * b.lon))
                out.add(LatLon(0.25 * a.lat + 0.75 * b.lat, 0.25 * a.lon + 0.75 * b.lon))
            }
            out.add(pts.last())
            pts = out
        }
        return pts
    }

    private fun GeoJsonSource.put(f: Feature?) {
        if (f != null) setGeoJson(f) else setGeoJson(empty)
    }

    private fun point(p: Place?): Feature? = p?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }

    fun showPin(place: Place?) {
        pinSrc?.put(point(place))
    }

    fun showRoutes(routes: List<Route>, selected: Int, dest: Place?) {
        val main = routes.getOrNull(selected)
        routeSrc?.put(main?.let { line(it.points) })
        val others = routes.filterIndexed { i, _ -> i != selected }.map { line(it.points) }
        altSrc?.setGeoJson(FeatureCollection.fromFeatures(others))
        destSrc?.put(if (main != null) point(dest) else null)
        pinSrc?.put(if (main != null) null else point(dest))
    }

    // ---------- camera ----------

    fun flyTo(lat: Double, lon: Double, zoom: Double, bottomPaddingDp: Int = 0) {
        val m = map ?: return
        stopFollowing()
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(lat, lon))
                    .zoom(zoom)
                    .tilt(0.0)
                    .padding(0.0, 0.0, 0.0, dp(bottomPaddingDp).toDouble())
                    .build(),
            ),
            900,
        )
    }

    fun fit(points: List<LatLon>, topDp: Int, bottomDp: Int) {
        val m = map ?: return
        if (points.size < 2) return
        stopFollowing()
        val b = LatLngBounds.Builder()
        points.forEach { b.include(LatLng(it.lat, it.lon)) }
        runCatching {
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(40), dp(topDp), dp(40), dp(bottomDp)), 900)
        }
    }

    // ---------- location puck ----------

    @SuppressLint("MissingPermission")
    fun showLocation(loc: Location) {
        val m = map ?: return
        val s = style ?: return
        val lc = m.locationComponent
        if (!locationOn) {
            val options = LocationComponentOptions.builder(context)
                .foregroundTintColor(Color.parseColor("#7C4DFF"))
                .bearingTintColor(Color.parseColor("#2F6BFF"))
                .accuracyColor(Color.parseColor("#7C4DFF"))
                .accuracyAlpha(0.12f)
                .build()
            lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, s)
                    .useDefaultLocationEngine(false)
                    .locationComponentOptions(options)
                    .build(),
            )
            lc.isLocationComponentEnabled = true
            lc.renderMode = RenderMode.COMPASS
            lc.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() { isFollowing = false; onTrackingDismissed?.invoke() }
                override fun onCameraTrackingChanged(currentMode: Int) {}
            })
            locationOn = true
        }
        lc.forceLocationUpdate(loc)
    }

    var isFollowing = false
        private set

    /**
     * Navigation camera: close in on your arrow, rotated to your heading, tilted,
     * with your arrow in the lower part of the screen so you see the road ahead.
     */
    fun follow(tilt: Boolean) {
        val m = map ?: return
        if (!locationOn) return
        val top = context.resources.displayMetrics.heightPixels * 0.42
        m.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(m.cameraPosition).padding(0.0, top, 0.0, 0.0).build(),
            ),
        )
        val lc = m.locationComponent
        lc.cameraMode = CameraMode.TRACKING_GPS
        lc.renderMode = RenderMode.GPS
        lc.zoomWhileTracking(18.2, 1200)
        lc.tiltWhileTracking(if (tilt) 58.0 else 0.0, 1200)
        isFollowing = true
    }

    fun stopFollowing() {
        val m = map ?: return
        isFollowing = false
        if (!locationOn) return
        m.locationComponent.cameraMode = CameraMode.NONE
        m.locationComponent.renderMode = RenderMode.COMPASS
        m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).padding(0.0, 0.0, 0.0, 0.0).build()))
    }
}

@Composable
fun MapSurface(controller: MapController, onLongPress: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val longPress by rememberUpdatedState(onLongPress)
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                m.setStyle(Style.Builder().fromUri(Servers.MAP_STYLE)) { s -> controller.attach(m, s) }
                m.addOnMapLongClickListener { p ->
                    longPress(p.latitude, p.longitude)
                    true
                }
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
