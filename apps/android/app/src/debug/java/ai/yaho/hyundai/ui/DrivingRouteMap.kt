package ai.yaho.hyundai.ui

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.yaho.hyundai.R
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale
import kotlin.math.roundToInt

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_ASSET = "seoul-road-route.geojson"
private const val ROUTE_ORANGE = "#B84608"

@Composable
internal fun DrivingRouteMap(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val routeJson = remember(context) { context.assets.open(ROUTE_ASSET).bufferedReader().use { it.readText() } }
    val route = remember(routeJson) { JSONObject(routeJson) }
    val properties = remember(route) { route.getJSONObject("properties") }
    var rendered by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val mapView = remember(context, routeJson) {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .camera(CameraPosition.Builder().target(LatLng(37.568, 126.984)).zoom(14.8).build())
            .textureMode(true)
            .compassEnabled(false)
            .scrollGesturesEnabled(false)
            .zoomGesturesEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .doubleTapGesturesEnabled(false)
            .quickZoomGesturesEnabled(false)
        MapView(context, options).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener {
                failed = true
                Log.w("YahoDrivingMap", "map_load_failed: $it")
            }
            addOnDidFinishRenderingMapListener { fully ->
                if (fully && !rendered) {
                    rendered = true
                    failed = false
                    Log.i("YahoDrivingMap", "basemap_fully_rendered")
                }
            }
            getMapAsync { map ->
                map.setStyle(STYLE_URL) { style ->
                    style.addSource(GeoJsonSource("yaho-route", routeJson))
                    style.addLayer(LineLayer("yaho-route-edge", "yaho-route").withProperties(
                        lineColor("#FFFFFF"), lineWidth(12f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                    ))
                    style.addLayer(LineLayer("yaho-route-line", "yaho-route").withProperties(
                        lineColor(ROUTE_ORANGE), lineWidth(8f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                    ))
                    val points = route.getJSONObject("geometry").getJSONArray("coordinates")
                    val bounds = LatLngBounds.Builder()
                    for (index in 0 until points.length()) {
                        val point = points.getJSONArray(index)
                        bounds.include(LatLng(point.getDouble(1), point.getDouble(0)))
                    }
                    val markers = """{"type":"FeatureCollection","features":[${listOf(0, points.length() - 1).joinToString(",") { index ->
                        """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":${points.getJSONArray(index)}}}"""
                    }}]}"""
                    style.addSource(GeoJsonSource("yaho-route-points", markers))
                    style.addLayer(CircleLayer("yaho-route-markers", "yaho-route-points").withProperties(
                        circleRadius(7f), circleColor(ROUTE_ORANGE), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
                    ))
                    post {
                        if (isAttachedToWindow && width > 0 && height > 0) {
                            val padding = minOf(width, height) / 8
                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), padding))
                        }
                    }
                    Log.i("YahoDrivingMap", "road_route_layer_added vertices=${points.length()}")
                }
            }
        }
    }

    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView) {
        delay(15_000)
        if (!rendered) failed = true
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Surface(
            Modifier.align(Alignment.TopStart).padding(24.dp),
            color = Color(0xFF25221F), contentColor = Color.White, shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("실제 도로 경로 · 미리보기", fontSize = 22.sp, color = Color(0xFFFFC490))
                Text("${properties.getString("from")} → ${properties.getString("to")}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                val distanceKm = String.format(Locale.KOREA, "%.1f", properties.getDouble("distanceMeters") / 1_000)
                Text("경로 약 $distanceKm km · OSRM", fontSize = 23.sp)
                if (properties.optString("firstManeuverModifier") == "slight right") {
                    val meters = properties.getDouble("firstManeuverDistanceMeters").roundToInt()
                    Text("출발점 기준 ${meters} m 후 오른쪽 방향", fontSize = 22.sp)
                }
            }
        }
        if (!rendered || failed) {
            Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(14.dp)) {
                Text(if (failed) "지도 데이터를 불러오지 못했어요" else "지도 불러오는 중", Modifier.padding(18.dp), fontSize = 24.sp)
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 62.dp).size(150.dp),
            shape = CircleShape, color = Color(0xFFFAF7F2), shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.yaho_companion_current), "야호 얼굴", Modifier.fillMaxSize(.9f))
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).padding(10.dp), color = Color.White.copy(alpha = .93f)) {
            Text("OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                color = Color(0xFF35332F), fontSize = 15.sp)
        }
    }
}
