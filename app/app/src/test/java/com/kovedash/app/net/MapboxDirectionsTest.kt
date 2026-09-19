package com.kovedash.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapboxDirectionsTest {

    @Test
    fun exclude_param_is_null_when_nothing_avoided() {
        assertNull(MapboxDirections.Options().excludeParam())
    }

    @Test
    fun exclude_param_lists_avoided_road_types() {
        val all = MapboxDirections.Options(avoidMotorways = true, avoidTolls = true, avoidFerries = true)
        assertEquals("motorway,toll,ferry", all.excludeParam())
        assertEquals("toll", MapboxDirections.Options(avoidTolls = true).excludeParam())
    }

    @Test
    fun parses_every_route_with_summary_and_road_classes() {
        val body = """
            {"routes":[
              {"distance":1200.0,"duration":300.0,
               "geometry":{"coordinates":[[139.0,35.0],[139.01,35.01]]},
               "legs":[{"summary":"Route 1, Expressway","steps":[
                 {"distance":1200.0,"maneuver":{"location":[139.0,35.0],"type":"depart","instruction":"Head north"},
                  "intersections":[{"classes":["motorway","toll"]},{}]}
               ]}]},
              {"distance":1500.0,"duration":420.0,
               "geometry":{"coordinates":[[139.0,35.0],[139.02,35.0],[139.01,35.01]]},
               "legs":[{"summary":"Local Rd","steps":[
                 {"distance":1500.0,"maneuver":{"location":[139.0,35.0],"type":"depart"},
                  "intersections":[{"classes":["ferry"]}]}
               ]}]}
            ]}
        """.trimIndent()

        val routes = MapboxDirections.parseRoutes(body)

        assertEquals(2, routes.size)
        val best = routes[0]
        assertEquals(300.0, best.durationSeconds, 0.0)
        assertEquals("Route 1, Expressway", best.summary)
        assertTrue(best.usesMotorway)
        assertTrue(best.usesToll)
        assertFalse(best.usesFerry)
        assertEquals(1, best.steps.size)

        val alt = routes[1]
        assertEquals(3, alt.coords.size)
        assertEquals("Local Rd", alt.summary)
        assertFalse(alt.usesMotorway)
        assertTrue(alt.usesFerry)
    }

    @Test
    fun malformed_body_yields_no_routes() {
        assertTrue(MapboxDirections.parseRoutes("not json").isEmpty())
        assertTrue(MapboxDirections.parseRoutes("""{"code":"NoRoute","routes":[]}""").isEmpty())
    }
}
