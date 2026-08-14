package com.example.smartambulance.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenStreetMapWebView(
    modifier: Modifier = Modifier,
    patientLat: Double? = null,
    patientLng: Double? = null,
    patientName: String = "Patient",
    driverLat: Double? = null,
    driverLng: Double? = null,
    driverName: String = "Ambulance",
    driverSpeed: Double = 0.0,
    hospitalLat: Double? = null,
    hospitalLng: Double? = null,
    hospitalName: String = "Hospital",
    fleetMarkers: List<FleetMarkerData> = emptyList(),
    zoomLevel: Int = 13
) {
    val context = LocalContext.current
    val webView = remember { WebView(context) }

    val defaultLat = driverLat ?: patientLat ?: fleetMarkers.firstOrNull()?.latitude ?: 12.9716
    val defaultLng = driverLng ?: patientLng ?: fleetMarkers.firstOrNull()?.longitude ?: 77.5946

    val htmlContent = remember(patientLat, patientLng, driverLat, driverLng, hospitalLat, hospitalLng, fleetMarkers) {
        buildLeafletHtml(
            defaultLat = defaultLat,
            defaultLng = defaultLng,
            patientLat = patientLat,
            patientLng = patientLng,
            patientName = patientName,
            driverLat = driverLat,
            driverLng = driverLng,
            driverName = driverName,
            driverSpeed = driverSpeed,
            hospitalLat = hospitalLat,
            hospitalLng = hospitalLng,
            hospitalName = hospitalName,
            fleetMarkers = fleetMarkers,
            zoomLevel = zoomLevel
        )
    }

    AndroidView(
        factory = {
            webView.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { wv ->
            wv.loadDataWithBaseURL("https://openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
        },
        modifier = modifier.fillMaxSize()
    )
}

data class FleetMarkerData(
    val id: String,
    val driverName: String,
    val latitude: Double,
    val longitude: Double,
    val isAvailable: Boolean,
    val phone: String
)

private fun buildLeafletHtml(
    defaultLat: Double,
    defaultLng: Double,
    patientLat: Double?,
    patientLng: Double?,
    patientName: String,
    driverLat: Double?,
    driverLng: Double?,
    driverName: String,
    driverSpeed: Double,
    hospitalLat: Double?,
    hospitalLng: Double?,
    hospitalName: String,
    fleetMarkers: List<FleetMarkerData>,
    zoomLevel: Int
): String {
    val patientMarkerJs = if (patientLat != null && patientLng != null && patientLat != 0.0 && patientLng != 0.0) {
        """
        var patientIcon = L.divIcon({
            html: '<div style="background:#E53935;color:white;width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:18px;border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.4);">👤</div>',
            className: '',
            iconSize: [32, 32],
            iconAnchor: [16, 16]
        });
        var pMarker = L.marker([$patientLat, $patientLng], {icon: patientIcon}).addTo(map);
        pMarker.bindPopup("<b>👤 Patient: ${patientName.replace("'", "\\'")}</b><br>Emergency Location");
        bounds.push([$patientLat, $patientLng]);
        """.trimIndent()
    } else ""

    val driverMarkerJs = if (driverLat != null && driverLng != null && driverLat != 0.0 && driverLng != 0.0) {
        """
        var driverIcon = L.divIcon({
            html: '<div style="background:#1565C0;color:white;width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:20px;border:3px solid white;box-shadow:0 2px 8px rgba(0,0,0,0.5);">🚑</div>',
            className: '',
            iconSize: [36, 36],
            iconAnchor: [18, 18]
        });
        var dMarker = L.marker([$driverLat, $driverLng], {icon: driverIcon}).addTo(map);
        dMarker.bindPopup("<b>🚑 ${driverName.replace("'", "\\'")}</b><br>Speed: ${driverSpeed.toInt()} km/h");
        bounds.push([$driverLat, $driverLng]);
        """.trimIndent()
    } else ""

    val hospitalMarkerJs = if (hospitalLat != null && hospitalLng != null && hospitalLat != 0.0 && hospitalLng != 0.0) {
        """
        var hospitalIcon = L.divIcon({
            html: '<div style="background:#2E7D32;color:white;width:34px;height:34px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:18px;border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.4);">🏥</div>',
            className: '',
            iconSize: [34, 34],
            iconAnchor: [17, 17]
        });
        var hMarker = L.marker([$hospitalLat, $hospitalLng], {icon: hospitalIcon}).addTo(map);
        hMarker.bindPopup("<b>🏥 ${hospitalName.replace("'", "\\'")}</b><br>Destination Hospital");
        bounds.push([$hospitalLat, $hospitalLng]);
        """.trimIndent()
    } else ""

    val routeJs = if (driverLat != null && driverLng != null && patientLat != null && patientLng != null) {
        val polyCoords = mutableListOf<String>()
        polyCoords.add("[$driverLat, $driverLng]")
        polyCoords.add("[$patientLat, $patientLng]")
        if (hospitalLat != null && hospitalLng != null) {
            polyCoords.add("[$hospitalLat, $hospitalLng]")
        }
        """
        var polyline = L.polyline([${polyCoords.joinToString(",")}], {
            color: '#1E88E5',
            weight: 5,
            opacity: 0.8,
            dashArray: '10, 10'
        }).addTo(map);
        """.trimIndent()
    } else ""

    val fleetJs = fleetMarkers.joinToString("\n") { f ->
        val bgColor = if (f.isAvailable) "#2E7D32" else "#C62828"
        val statusText = if (f.isAvailable) "AVAILABLE" else "BUSY"
        """
        (function(){
            var icon = L.divIcon({
                html: '<div style="background:$bgColor;color:white;width:30px;height:30px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:16px;border:2px solid white;box-shadow:0 2px 5px rgba(0,0,0,0.4);">🚑</div>',
                className: '',
                iconSize: [30, 30],
                iconAnchor: [15, 15]
            });
            var m = L.marker([${f.latitude}, ${f.longitude}], {icon: icon}).addTo(map);
            m.bindPopup("<b>🚑 ${f.driverName.replace("'", "\\'")}</b><br>Status: $statusText<br>Tel: ${f.phone}");
            bounds.push([${f.latitude}, ${f.longitude}]);
        })();
        """.trimIndent()
    }

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
            html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background: #0F172A; }
            .leaflet-control-attribution { display: none !important; }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script>
            var map = L.map('map', { zoomControl: true }).setView([$defaultLat, $defaultLng], $zoomLevel);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19
            }).addTo(map);

            var bounds = [];
            $patientMarkerJs
            $driverMarkerJs
            $hospitalMarkerJs
            $routeJs
            $fleetJs

            if (bounds.length > 1) {
                map.fitBounds(bounds, { padding: [40, 40] });
            }
        </script>
    </body>
    </html>
    """.trimIndent()
}
