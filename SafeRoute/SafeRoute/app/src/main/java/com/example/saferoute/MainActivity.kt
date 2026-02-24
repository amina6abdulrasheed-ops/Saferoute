package com.example.saferoute

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap

    private lateinit var sourceInput: EditText
    private lateinit var destinationInput: EditText
    private lateinit var findRouteBtn: Button

    private val crimeList = ArrayList<CrimePoint>()

    // REPLACE WITH YOUR REAL API KEY
    private val apiKey = "AIzaSyDLlpIpLwFoHDImM_jOBxUfXuQ3y1AVBi0"

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sourceInput = findViewById(R.id.sourceInput)
        destinationInput = findViewById(R.id.destinationInput)
        findRouteBtn = findViewById(R.id.findRouteBtn)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        loadDataset()

        findRouteBtn.setOnClickListener {

            Toast.makeText(this, "Button clicked", Toast.LENGTH_SHORT).show()

            val source = sourceInput.text.toString()
            val destination = destinationInput.text.toString()

            if (source.isEmpty() || destination.isEmpty()) {

                Toast.makeText(
                    this,
                    "Enter source and destination",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            fetchRoute(source, destination)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {

        map = googleMap

        val poojappura = LatLng(8.4821, 76.9676)

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(poojappura, 13f))
    }

    // LOAD DATASET
    private fun loadDataset() {

        try {

            val reader = BufferedReader(
                InputStreamReader(
                    assets.open("crime_data_poojappura_120_entries.csv")
                )
            )

            reader.readLine()

            var line: String?

            while (reader.readLine().also { line = it } != null) {

                val data = line!!.split(",")

                crimeList.add(
                    CrimePoint(
                        data[1].toDouble(),
                        data[2].toDouble(),
                        data[9].toDouble()
                    )
                )
            }

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }

    // FETCH ROUTES FROM GOOGLE DIRECTIONS API
    private fun fetchRoute(source: String, destination: String) {

        Toast.makeText(this, "Fetching safest route...", Toast.LENGTH_SHORT).show()

        val url =
            "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${source.replace(" ", "+")}" +
                    "&destination=${destination.replace(" ", "+")}" +
                    "&alternatives=true" +
                    "&key=$apiKey"

        val queue = Volley.newRequestQueue(this)

        val request = StringRequest(
            Request.Method.GET,
            url,
            { response ->

                val json = JSONObject(response)

                val routes = json.getJSONArray("routes")

                val allRoutes = ArrayList<List<LatLng>>()

                var safestRoute: List<LatLng>? = null
                var lowestRisk = Double.MAX_VALUE

                for (i in 0 until routes.length()) {

                    val polyline =
                        routes.getJSONObject(i)
                            .getJSONObject("overview_polyline")
                            .getString("points")

                    val decoded = decodePolyline(polyline)

                    allRoutes.add(decoded)

                    val risk = calculateRisk(decoded)

                    if (risk < lowestRisk) {

                        lowestRisk = risk
                        safestRoute = decoded
                    }
                }

                drawAllRoutes(allRoutes, safestRoute!!)

                animateMovement(safestRoute)

            },
            { error ->

                Toast.makeText(
                    this,
                    "Error loading route",
                    Toast.LENGTH_LONG
                ).show()

                error.printStackTrace()
            }
        )

        queue.add(request)
    }

    // CALCULATE RISK
    private fun calculateRisk(route: List<LatLng>): Double {

        var totalRisk = 0.0

        for (point in route) {

            for (crime in crimeList) {

                val dist =
                    Math.sqrt(
                        Math.pow(point.latitude - crime.lat, 2.0) +
                                Math.pow(point.longitude - crime.lon, 2.0)
                    )

                if (dist < 0.002)
                    totalRisk += crime.risk
            }
        }

        return totalRisk
    }

    // DRAW ROUTES
    private fun drawAllRoutes(
        allRoutes: List<List<LatLng>>,
        safestRoute: List<LatLng>
    ) {

        map.clear()

        // GRAY routes
        for (route in allRoutes) {

            val polyline = PolylineOptions()
                .addAll(route)
                .color(android.graphics.Color.GRAY)
                .width(8f)

            map.addPolyline(polyline)
        }

        // GREEN safest route
        val safestPolyline = PolylineOptions()
            .addAll(safestRoute)
            .color(android.graphics.Color.GREEN)
            .width(15f)

        map.addPolyline(safestPolyline)

        val source = safestRoute.first()

        map.addMarker(
            MarkerOptions()
                .position(source)
                .title("Source")
                .icon(BitmapDescriptorFactory.defaultMarker(
                    BitmapDescriptorFactory.HUE_GREEN))
        )

        val destination = safestRoute.last()

        map.addMarker(
            MarkerOptions()
                .position(destination)
                .title("Destination")
                .icon(BitmapDescriptorFactory.defaultMarker(
                    BitmapDescriptorFactory.HUE_RED))
        )

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(source, 13f))
    }

    // LIVE MOVEMENT
    private fun animateMovement(route: List<LatLng>) {

        val marker =
            map.addMarker(
                MarkerOptions()
                    .position(route[0])
                    .title("Live Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_AZURE))
            )

        Thread {

            for (point in route) {

                runOnUiThread {

                    marker?.position = point

                    map.animateCamera(
                        CameraUpdateFactory.newLatLng(point)
                    )
                }

                Thread.sleep(800)
            }

        }.start()
    }

    // DECODE POLYLINE
    private fun decodePolyline(encoded: String): List<LatLng> {

        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {

            var b: Int
            var shift = 0
            var result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlat =
                if ((result and 1) != 0)
                    (result shr 1).inv()
                else
                    result shr 1

            lat += dlat

            shift = 0
            result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlng =
                if ((result and 1) != 0)
                    (result shr 1).inv()
                else
                    result shr 1

            lng += dlng

            poly.add(
                LatLng(
                    lat / 1E5,
                    lng / 1E5
                )
            )
        }

        return poly
    }
}
