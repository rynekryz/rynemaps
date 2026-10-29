package com.rynekryz.rynemaps.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH = 6371000.0

    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH * asin(sqrt(h))
    }

    data class Projection(val segment: Int, val offset: Double, val along: Double)

    fun project(route: RouteInfo, p: GeoPoint): Projection {
        val pts = route.points
        if (pts.size < 2) return Projection(0, distance(pts.first(), p), 0.0)
        val cum = route.cumulative
        val k = cos(Math.toRadians(p.lat))
        var best = Double.MAX_VALUE
        var bi = 0
        var bt = 0.0
        for (i in 0 until pts.size - 1) {
            val ax = (pts[i].lon - p.lon) * 111320.0 * k
            val ay = (pts[i].lat - p.lat) * 110540.0
            val bx = (pts[i + 1].lon - p.lon) * 111320.0 * k
            val by = (pts[i + 1].lat - p.lat) * 110540.0
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx
            val cy = ay + t * dy
            val d = sqrt(cx * cx + cy * cy)
            if (d < best) {
                best = d
                bi = i
                bt = t
            }
        }
        return Projection(bi, best, cum[bi] + bt * (cum[bi + 1] - cum[bi]))
    }
}
