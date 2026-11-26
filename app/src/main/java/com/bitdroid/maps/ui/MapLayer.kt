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
import org.maplibre.android.location.OnLocationCameraTransitionListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
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
class MapController(private val context: Context, private var pal: MapPalette, private var vehicleRes: Int = com.bitdroid.maps.R.drawable.ic_puck_gps) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var locationOn = false
    private var routeSrc: GeoJsonSource? = null
    private var altSrc: GeoJsonSource? = null
    private var pinSrc: GeoJsonSource? = null
    private var routeLine: LineLayer? = null
    private var routeGlow: LineLayer? = null
    private var routeCasing: LineLayer? = null
    private var altLine: LineLayer? = null
    private var pinDot: CircleLayer? = null
    private var destDot: CircleLayer? = null
    /** How much of the route you've already driven (0..1); that part is drawn dimmer. */
    private var traveled = 0f
    /** Width of the dim → bright edge as a fraction of the route (a few meters, whatever the route length). */
    private var edge = 0.0005f
    private var trailAnim: android.animation.ValueAnimator? = null
    private var destSrc: GeoJsonSource? = null
    private var trafficSrc: GeoJsonSource? = null
    private var labelSrc: GeoJsonSource? = null
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
        m.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) userMoved = true
        }
        m.uiSettings.isLogoEnabled = false
        m.uiSettings.isCompassEnabled = false
        m.uiSettings.attributionGravity = Gravity.TOP or Gravity.END
        m.uiSettings.setAttributionMargins(0, dp(150), dp(14), 0) // under the search bar, clear of the sheets
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
            is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(pal.bg))
            is FillLayer -> {
                val c = when {
                    has("water", "ocean", "river", "lake") -> pal.water
                    has("park", "wood", "forest", "grass", "landcover", "green", "pitch", "cemetery") -> pal.park
                    has("building") -> pal.block
                    else -> pal.bg
                }
                layer.setProperties(PropertyFactory.fillColor(c), PropertyFactory.fillOutlineColor(c))
            }
            is FillExtrusionLayer -> layer.setProperties(PropertyFactory.fillExtrusionColor(pal.block))
            is LineLayer -> {
                val c = when {
                    has("water", "river", "stream", "canal") -> pal.water
                    has("boundary", "admin") -> pal.road
                    has("rail", "transit") -> pal.block
                    has("motorway", "trunk") && has("casing") -> pal.bg
                    has("motorway", "trunk") -> pal.roadMajor
                    has("casing") -> pal.bg
                    has("path", "track", "pedestrian", "footway", "cycle") -> pal.block
                    has("highway", "road", "street", "transportation", "minor", "major", "primary", "secondary", "tertiary", "service") -> pal.road
                    else -> null
                }
                if (c != null) layer.setProperties(PropertyFactory.lineColor(c))
            }
            is SymbolLayer -> {
                val c = if (has("country", "city", "state", "continent")) pal.labelStrong else pal.label
                layer.setProperties(
                    PropertyFactory.textColor(c),
                    PropertyFactory.textHaloColor(pal.bg),
                    PropertyFactory.textHaloWidth(1.2f),
                )
            }
        }
    }

    /**
     * Route colors along the line. The part you've already driven is a dim, darker color so the road
     * ahead stands out; the glow is only drawn ahead of you.
     */
    private fun routeGradient(glow: Boolean): Expression {
        val dim = if (glow) android.graphics.Color.TRANSPARENT
        // Driven part: plain gray, clearly different from the bright road ahead.
        else 0xFF5C5F68.toInt()
        val p = traveled
        return when {
            p <= 0f -> Expression.interpolate(
                Expression.linear(), Expression.lineProgress(),
                Expression.stop(0f, Expression.color(pal.routeStart)),
                Expression.stop(1f, Expression.color(pal.routeEnd)),
            )
            p >= 0.995f -> Expression.interpolate(
                Expression.linear(), Expression.lineProgress(),
                Expression.stop(0f, Expression.color(dim)),
                Expression.stop(1f, Expression.color(dim)),
            )
            else -> {
                val e = edge.coerceAtMost((1f - p) / 2f).coerceAtLeast(1e-6f)
                Expression.interpolate(
                    Expression.linear(), Expression.lineProgress(),
                    Expression.stop(0f, Expression.color(dim)),
                    Expression.stop(p, Expression.color(dim)),
                    Expression.stop(p + e, Expression.color(pal.routeStart)),
                    Expression.stop(1f, Expression.color(pal.routeEnd)),
                )
            }
        }
    }

    private fun refreshRouteColors() {
        routeLine?.setProperties(PropertyFactory.lineGradient(routeGradient(glow = false)))
        routeGlow?.setProperties(PropertyFactory.lineGradient(routeGradient(glow = true)))
    }

    /**
     * Called on every GPS fix with how many meters of the route are behind you. The dim edge sits right
     * at your car and glides there over ~1 s, in step with the arrow's own animation, instead of jumping.
     */
    fun setTraveled(doneMeters: Double, totalMeters: Double) {
        if (totalMeters <= 0) return
        edge = (3.0 / totalMeters).toFloat()
        val target = ((doneMeters - 2.0) / totalMeters).toFloat().coerceIn(0f, 1f)
        trailAnim?.cancel()
        val from = traveled
        if (target <= from || target - from > 0.2f) {
            // New route or a jump: snap straight there.
            traveled = target
            refreshRouteColors()
            return
        }
        trailAnim = android.animation.ValueAnimator.ofFloat(from, target).apply {
            duration = 1000
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                traveled = it.animatedValue as Float
                refreshRouteColors()
            }
            start()
        }
    }

    /** Switches the map to another color set (app style changed). */
    fun applyPalette(p: MapPalette) {
        if (p == pal) return
        pal = p
        val s = style ?: return
        recolor(s)
        routeCasing?.setProperties(PropertyFactory.lineColor(0xFF0E0F12.toInt()))
        altLine?.setProperties(PropertyFactory.lineColor(pal.alt))
        pinDot?.setProperties(PropertyFactory.circleColor(pal.pin))
        destDot?.setProperties(PropertyFactory.circleColor(pal.routeEnd))
        refreshRouteColors()
        if (locationOn) runCatching { map?.locationComponent?.applyStyle(puckOptions()) }
    }

    /** Arrow or car icon for your position while navigating. */
    fun setVehicle(res: Int) {
        if (res == vehicleRes) return
        vehicleRes = res
        if (locationOn) runCatching { map?.locationComponent?.applyStyle(puckOptions()) }
    }

    private fun puckOptions(): LocationComponentOptions = LocationComponentOptions.builder(context)
        .foregroundDrawable(com.bitdroid.maps.R.drawable.ic_puck_dot)
        .gpsDrawable(vehicleRes)
        .accuracyColor(pal.pin)
        .accuracyAlpha(0.12f)
        .build()

    /** Line width that grows as you zoom in, so the route stays bold and smooth up close. */
    private fun byZoom(at12: Float, at19: Float): Expression = Expression.interpolate(
        Expression.exponential(1.4f), Expression.zoom(),
        Expression.stop(12f, at12), Expression.stop(19f, at19),
    )

    private fun addOverlays(s: Style) {
        // The route must sit ON TOP of every road (some road and bridge layers come after the first label
        // in the style), so it goes right above the last non-label layer. Only text labels stay above it.
        var anchor: String? = s.layers.lastOrNull { it !is SymbolLayer }?.id
        fun below(layer: Layer) {
            val a = anchor
            if (a != null) s.addLayerAbove(layer, a) else s.addLayer(layer)
            anchor = layer.id
        }

        val alt = GeoJsonSource("bm-alt", GeoJsonOptions())
        val route = GeoJsonSource("bm-route", GeoJsonOptions().withLineMetrics(true))
        val pin = GeoJsonSource("bm-pin", GeoJsonOptions())
        val dest = GeoJsonSource("bm-dest", GeoJsonOptions())
        s.addSource(alt); s.addSource(route); s.addSource(pin); s.addSource(dest)
        altSrc = alt; routeSrc = route; pinSrc = pin; destSrc = dest

        altLine = LineLayer("bm-alt-line", "bm-alt")
        below(
            altLine!!.withProperties(
                PropertyFactory.lineColor(pal.alt),
                PropertyFactory.lineWidth(byZoom(5f, 16f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineOpacity(0.85f),
            ),
        )
        val gradient = routeGradient(glow = false)
        routeCasing = LineLayer("bm-route-casing", "bm-route")
        below(
            routeCasing!!.withProperties(
                PropertyFactory.lineColor(0xFF0E0F12.toInt()),
                PropertyFactory.lineWidth(byZoom(11f, 34f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        routeGlow = LineLayer("bm-route-glow", "bm-route")
        below(
            routeGlow!!.withProperties(
                PropertyFactory.lineGradient(routeGradient(glow = true)),
                PropertyFactory.lineWidth(byZoom(14f, 40f)),
                PropertyFactory.lineBlur(byZoom(8f, 22f)),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        routeLine = LineLayer("bm-route-line", "bm-route")
        below(
            routeLine!!.withProperties(
                PropertyFactory.lineGradient(gradient),
                PropertyFactory.lineWidth(byZoom(7f, 24f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        // Live traffic painted on top of the route: orange = slow, red = jammed, dark red = stopped.
        val traffic = GeoJsonSource("bm-traffic", GeoJsonOptions())
        s.addSource(traffic)
        trafficSrc = traffic
        below(
            LineLayer("bm-traffic-line", "bm-traffic").withProperties(
                PropertyFactory.lineColor(
                    Expression.match(
                        Expression.get("lvl"), Expression.color(0xFFFFB454.toInt()),
                        Expression.stop(2, Expression.color(0xFFFF6B57.toInt())),
                        Expression.stop(3, Expression.color(0xFFC62828.toInt())),
                    ),
                ),
                PropertyFactory.lineWidth(byZoom(7f, 24f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        // "24 min" bubbles on each route (Route screen).
        val labels = GeoJsonSource("bm-labels", GeoJsonOptions())
        s.addSource(labels)
        labelSrc = labels
        s.addLayer(
            SymbolLayer("bm-label-pills", "bm-labels").withProperties(
                PropertyFactory.iconImage(Expression.get("img")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            ),
        )
        pinDot = CircleLayer("bm-pin-dot", "bm-pin")
        s.addLayer(
            pinDot!!.withProperties(
                PropertyFactory.circleRadius(11f),
                PropertyFactory.circleColor(pal.pin),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3.5f),
            ),
        )
        destDot = CircleLayer("bm-dest-dot", "bm-dest")
        s.addLayer(
            destDot!!.withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor(pal.routeEnd),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3.5f),
            ),
        )
    }

    // ---------- content ----------

    private val empty get() = FeatureCollection.fromFeatures(emptyArray<Feature>())

    private fun line(points: List<LatLon>) =
        Feature.fromGeometry(LineString.fromLngLats(smooth(points).map { Point.fromLngLat(it.lon, it.lat) }))

    /**
     * Rounds corners only within a few meters of each bend, so turns look smooth but the line never
     * cuts across the inside of a corner (the old smoothing pulled the line off the road at turns).
     */
    private fun smooth(points: List<LatLon>): List<LatLon> {
        if (points.size < 3) return points
        val out = ArrayList<LatLon>(points.size * 3)
        out.add(points.first())
        for (i in 1 until points.size - 1) {
            val a = points[i - 1]; val b = points[i]; val c = points[i + 1]
            val ab = com.bitdroid.maps.data.Geo.distance(a, b)
            val bc = com.bitdroid.maps.data.Geo.distance(b, c)
            if (ab < 0.5 || bc < 0.5) { out.add(b); continue }
            val t1 = (minOf(4.0, ab / 3) / ab)
            val t2 = (minOf(4.0, bc / 3) / bc)
            val p1 = LatLon(b.lat + (a.lat - b.lat) * t1, b.lon + (a.lon - b.lon) * t1)
            val p2 = LatLon(b.lat + (c.lat - b.lat) * t2, b.lon + (c.lon - b.lon) * t2)
            out.add(p1)
            // Quadratic curve through the corner, 4 steps.
            for (k in 1..3) {
                val t = k / 4.0
                val u = 1 - t
                out.add(LatLon(u * u * p1.lat + 2 * u * t * b.lat + t * t * p2.lat, u * u * p1.lon + 2 * u * t * b.lon + t * t * p2.lon))
            }
            out.add(p2)
        }
        out.add(points.last())
        return out
    }

    private fun GeoJsonSource.put(f: Feature?) {
        if (f != null) setGeoJson(f) else setGeoJson(empty)
    }

    private fun point(p: Place?): Feature? = p?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }

    fun showPin(place: Place?) {
        pinSrc?.put(point(place))
    }

    fun showRoutes(routes: List<Route>, selected: Int, dest: Place?, labels: Boolean = false) {
        trailAnim?.cancel()
        if (traveled != 0f) { traveled = 0f; refreshRouteColors() }
        val main = routes.getOrNull(selected)
        routeSrc?.put(main?.let { line(it.points) })
        val others = routes.filterIndexed { i, _ -> i != selected }.map { line(it.points) }
        altSrc?.setGeoJson(FeatureCollection.fromFeatures(others))
        destSrc?.put(if (main != null) point(dest) else null)
        pinSrc?.put(if (main != null) null else point(dest))
        // Traffic on the chosen route.
        val jams = main?.traffic.orEmpty().mapNotNull { sp ->
            val seg = main!!.points.subList(sp.from.coerceIn(0, main.points.lastIndex), (sp.to + 1).coerceIn(0, main.points.size))
            if (seg.size < 2) null
            else Feature.fromGeometry(LineString.fromLngLats(smooth(seg).map { Point.fromLngLat(it.lon, it.lat) })).also { it.addNumberProperty("lvl", sp.value) }
        }
        trafficSrc?.setGeoJson(FeatureCollection.fromFeatures(jams))
        showLabels(if (labels) routes else emptyList(), selected)
    }

    /** Pill bubbles ("24 min", "+3 min") placed where each route is easy to tell apart. */
    private fun showLabels(routes: List<Route>, selected: Int) {
        val s = style ?: return
        val feats = ArrayList<Feature>()
        val best = routes.getOrNull(selected)
        routes.forEachIndexed { i, r ->
            if (r.points.size < 2) return@forEachIndexed
            // Selected route: a point about 40% along. Others: the point farthest from the selected route.
            val at = if (i == selected || best == null) r.points[(r.points.size * 0.4).toInt()]
            else r.points.filterIndexed { k, _ -> k % 5 == 0 }.maxByOrNull { p -> best.points.filterIndexed { k, _ -> k % 8 == 0 }.minOf { com.bitdroid.maps.data.Geo.distance(it, p) } } ?: r.points[r.points.size / 2]
            val text = com.bitdroid.maps.data.Units.duration(r.durationS)
            val sub = when {
                i == selected && routes.size > 1 && i == 0 -> "Fastest"
                best != null && i != selected -> {
                    val d = Math.round((r.durationS - best.durationS) / 60.0).toInt()
                    if (d > 0) "+$d min" else if (d < 0) "$d min" else "Same time"
                }
                else -> null
            }
            val id = "bm-pill-$i-${if (i == selected) 1 else 0}"
            s.addImage(id, pillBitmap(text, sub, i == selected))
            feats.add(Feature.fromGeometry(Point.fromLngLat(at.lon, at.lat)).also { it.addStringProperty("img", id) })
        }
        labelSrc?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    private fun pillBitmap(text: String, sub: String?, selected: Boolean): android.graphics.Bitmap {
        val d = density
        val big = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15 * d; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 700, false)
            color = if (selected) pal.onPill else pal.labelStrong
        }
        val small = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11 * d; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 600, false)
            color = if (selected) pal.onPill else pal.label
        }
        val w = (maxOf(big.measureText(text), sub?.let { small.measureText(it) } ?: 0f) + 24 * d).toInt()
        val h = ((if (sub != null) 46 else 32) * d).toInt()
        val tail = (7 * d).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(w, h + tail, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = if (selected) pal.pill else pal.pillAlt }
        c.drawRoundRect(android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), 14 * d, 14 * d, bg)
        val path = android.graphics.Path().apply {
            moveTo(w / 2f - 7 * d, h.toFloat()); lineTo(w / 2f, (h + tail).toFloat()); lineTo(w / 2f + 7 * d, h.toFloat()); close()
        }
        c.drawPath(path, bg)
        c.drawText(text, 12 * d, if (sub != null) 21 * d else 21 * d, big)
        if (sub != null) c.drawText(sub, 12 * d, 37 * d, small)
        return bmp
    }

    // ---------- layers (satellite, live traffic everywhere, 3D buildings) ----------

    private var satOn = false
    private var flowOn = false

    fun setLayers(satellite: Boolean, traffic: Boolean, buildings3d: Boolean, tomtomKey: String?) = whenReady {
        val s = style ?: return@whenReady
        val key = tomtomKey?.let { java.net.URLEncoder.encode(it, "UTF-8") }
        // Satellite photos (TomTom, same free key) under the roads and labels.
        if (satellite && key != null && !satOn) runCatching {
            s.addSource(org.maplibre.android.style.sources.RasterSource(
                "bm-sat-src",
                org.maplibre.android.style.sources.TileSet("2.2.0", "https://api.tomtom.com/map/1/tile/sat/main/{z}/{x}/{y}.jpg?key=$key"),
                256,
            ))
            val firstLine = s.layers.firstOrNull { it is LineLayer }?.id
            val layer = org.maplibre.android.style.layers.RasterLayer("bm-sat", "bm-sat-src")
            if (firstLine != null) s.addLayerBelow(layer, firstLine) else s.addLayerBelow(layer, "bm-alt-line")
            satOn = true
        }
        if ((!satellite || key == null) && satOn) {
            runCatching { s.removeLayer("bm-sat"); s.removeSource("bm-sat-src") }
            satOn = false
        }
        // Live traffic on every road (TomTom flow tiles).
        if (traffic && key != null && !flowOn) runCatching {
            s.addSource(org.maplibre.android.style.sources.RasterSource(
                "bm-flow-src",
                org.maplibre.android.style.sources.TileSet("2.2.0", "https://api.tomtom.com/traffic/map/4/tile/flow/relative0-dark/{z}/{x}/{y}.png?key=$key&tileSize=256&thickness=6"),
                256,
            ))
            s.addLayerBelow(org.maplibre.android.style.layers.RasterLayer("bm-flow", "bm-flow-src").withProperties(PropertyFactory.rasterOpacity(0.85f)), "bm-alt-line")
            flowOn = true
        }
        if ((!traffic || key == null) && flowOn) {
            runCatching { s.removeLayer("bm-flow"); s.removeSource("bm-flow-src") }
            flowOn = false
        }
        // 3D buildings: the style's own extrusion layers.
        s.layers.filterIsInstance<FillExtrusionLayer>().forEach {
            it.setProperties(PropertyFactory.visibility(if (buildings3d) Property.VISIBLE else Property.NONE))
        }
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
            val options = puckOptions()
            lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, s)
                    .useDefaultLocationEngine(false)
                    .locationComponentOptions(options)
                    .build(),
            )
            lc.isLocationComponentEnabled = true
            lc.renderMode = RenderMode.NORMAL
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

    /** True once you pinch/drag the map yourself during navigation; then we stop forcing the zoom. */
    private var userMoved = false

    /** Street-level, like the mockup. */
    private val NAV_ZOOM = 19.0

    /** Called on every GPS fix while navigating: snaps back to close zoom unless you zoomed out yourself. */
    fun keepClose() {
        val m = map ?: return
        if (!isFollowing || userMoved || !locationOn) return
        if (m.cameraPosition.zoom < NAV_ZOOM - 0.25) m.locationComponent.zoomWhileTracking(NAV_ZOOM, 500)
    }

    /**
     * Navigation camera: close in on your arrow, rotated to your heading, tilted,
     * with your arrow in the lower part of the screen so you see the road ahead.
     */
    fun follow(tilt: Boolean) {
        val m = map ?: return
        if (!locationOn) return
        userMoved = false
        val top = context.resources.displayMetrics.heightPixels * 0.42
        m.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(m.cameraPosition).padding(0.0, top, 0.0, 0.0).build(),
            ),
        )
        val lc = m.locationComponent
        val zoom = NAV_ZOOM
        val tiltDeg = if (tilt) 58.0 else 0.0
        // Zoom + tilt are part of the switch to follow mode: MapLibre ignores separate zoom calls while
        // that switch animates, which left the map zoomed out until you started moving.
        lc.setCameraMode(
            CameraMode.TRACKING_GPS, 1200L, zoom, null, tiltDeg,
            object : OnLocationCameraTransitionListener {
                override fun onLocationCameraTransitionFinished(cameraMode: Int) {
                    lc.zoomWhileTracking(zoom, 600)
                    lc.tiltWhileTracking(tiltDeg, 600)
                }
                override fun onLocationCameraTransitionCanceled(cameraMode: Int) {
                    lc.zoomWhileTracking(zoom, 600)
                    lc.tiltWhileTracking(tiltDeg, 600)
                }
            },
        )
        lc.renderMode = RenderMode.GPS
        isFollowing = true
    }

    fun stopFollowing() {
        val m = map ?: return
        isFollowing = false
        if (!locationOn) return
        m.locationComponent.cameraMode = CameraMode.NONE
        m.locationComponent.renderMode = RenderMode.NORMAL
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
        // TextureView mode lets the map be clipped into a rounded Bloom tile.
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
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
