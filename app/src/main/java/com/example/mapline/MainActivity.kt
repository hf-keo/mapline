package com.example.mapline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            MaplineApp()
        }
    }
}

@Composable
fun MaplineApp() {
    MaterialTheme {
        MapScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = remember {
        ActivityResultContracts.RequestPermission()
    }
    val launcher = rememberLauncherForActivityResultCompat(permissionLauncher) { granted ->
        hasLocationPermission = granted
    }

    DisposableEffect(Unit) {
        launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        onDispose { }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
        }
    }

    var lineColor by remember { mutableStateOf(Color(0xFF00BCD4)) }
    var heading by remember { mutableStateOf(0f) }
    var lineLength by remember { mutableStateOf(500f) }
    var followLocation by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
    }
    val compassOverlay = remember { CompassOverlay(context, mapView) }
    val guideLine = remember { Polyline().apply { outlinePaint.strokeWidth = 6f } }

    DisposableEffect(mapView) {
        mapView.overlays.add(locationOverlay)
        mapView.overlays.add(compassOverlay)
        mapView.overlays.add(guideLine)
        mapView.controller.setCenter(GeoPoint(0.0, 0.0))

        onDispose {
            locationOverlay.disableMyLocation()
            locationOverlay.disableFollowLocation()
            compassOverlay.disableCompass()
            mapView.overlays.remove(locationOverlay)
            mapView.overlays.remove(compassOverlay)
            mapView.overlays.remove(guideLine)
            mapView.onPause()
        }
    }

    DisposableEffect(followLocation) {
        if (followLocation) locationOverlay.enableFollowLocation() else locationOverlay.disableFollowLocation()
        onDispose { }
    }

    fun refreshGuideLine() {
        val startPoint = locationOverlay.myLocation ?: mapView.mapCenter as GeoPoint
        val end = destinationPoint(startPoint, heading.toDouble(), lineLength.toDouble())
        guideLine.setPoints(listOf(startPoint, end))
        guideLine.outlinePaint.color = android.graphics.Color.argb(
            255,
            (lineColor.red * 255).toInt(),
            (lineColor.green * 255).toInt(),
            (lineColor.blue * 255).toInt()
        )
        mapView.invalidate()
    }

    DisposableEffect(heading, lineLength, lineColor, hasLocationPermission) {
        refreshGuideLine()
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Heading guide",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            AndroidMapView(mapView = mapView)
            ControlPanel(
                heading = heading,
                onHeadingChange = { heading = it },
                lineLength = lineLength,
                onLineLengthChange = { lineLength = it },
                lineColor = lineColor,
                onLineColorChange = { lineColor = it },
                followLocation = followLocation,
                onFollowLocationChange = { followLocation = it },
                onRefresh = {
                    if (hasLocationPermission) {
                        refreshGuideLine()
                    }
                }
            )
        }
        Text(
            text = "Uses OpenStreetMap tiles via osmdroid for free usage.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            scope.launch {
                compassOverlay.enableCompass()
                // Wait for the first location fix then center the map.
                repeat(10) {
                    val loc = locationOverlay.myLocation
                    if (loc != null) {
                        mapView.controller.animateTo(loc)
                        refreshGuideLine()
                        return@launch
                    }
                    delay(500)
                }
            }
        }
        onDispose { }
    }
}

@Composable
private fun AndroidMapView(mapView: MapView) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .height(360.dp),
        factory = { mapView },
        update = { it.onResume() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlPanel(
    heading: Float,
    onHeadingChange: (Float) -> Unit,
    lineLength: Float,
    onLineLengthChange: (Float) -> Unit,
    lineColor: Color,
    onLineColorChange: (Color) -> Unit,
    followLocation: Boolean,
    onFollowLocationChange: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Heading direction", style = MaterialTheme.typography.bodyLarge)
        SliderRow(
            label = "Heading (°)",
            value = heading,
            valueRange = 0f..360f,
            steps = 12,
            onValueChange = onHeadingChange
        )
        SliderRow(
            label = "Line length (m)",
            value = lineLength,
            valueRange = 50f..5000f,
            steps = 20,
            onValueChange = onLineLengthChange
        )
        ColorDropdown(lineColor, onLineColorChange)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = followLocation, onCheckedChange = onFollowLocationChange)
            Text(text = "Follow my location", modifier = Modifier.padding(start = 8.dp))
        }
        Button(onClick = onRefresh) {
            Text(text = "Refresh line")
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorDropdown(selected: Color, onColorChange: (Color) -> Unit) {
    val options = listOf(
        Color(0xFF00BCD4), // teal
        Color(0xFFFF5722), // deep orange
        Color(0xFF4CAF50), // green
        Color(0xFF9C27B0)  // purple
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "",
            onValueChange = { },
            readOnly = true,
            label = { Text("Line color") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.menuAnchor()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { color ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(color = color, shape = CircleShape)
                            )
                            Text(text = " ")
                        }
                    },
                    onClick = {
                        onColorChange(color)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean {
    val fine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun <I, O> rememberLauncherForActivityResultCompat(
    contract: ActivityResultContracts.RequestPermission,
    onResult: (Boolean) -> Unit
) = androidx.activity.compose.rememberLauncherForActivityResult(contract = contract, onResult = onResult)

private fun destinationPoint(start: GeoPoint, headingDegrees: Double, distanceMeters: Double): GeoPoint {
    val radiusEarth = 6371000.0
    val angularDistance = distanceMeters / radiusEarth
    val bearingRad = Math.toRadians(headingDegrees)

    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)

    val lat2 = Math.asin(
        sin(lat1) * Math.cos(angularDistance) +
            cos(lat1) * Math.sin(angularDistance) * Math.cos(bearingRad)
    )

    val lon2 = lon1 + Math.atan2(
        sin(bearingRad) * Math.sin(angularDistance) * cos(lat1),
        Math.cos(angularDistance) - sin(lat1) * Math.sin(lat2)
    )

    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}
