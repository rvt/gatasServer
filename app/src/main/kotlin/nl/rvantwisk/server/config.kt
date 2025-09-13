package nl.rvantwisk.server

// Filter out all aircraft XXXmeter below ownship
const val STORE_FILTER_BELOW_OWNSHIP = 1000
// Filter out all aircraft XXXmeter above ownship
const val STORE_FILTER_ABOVE_OWNSHIP = 1000
// Maximum number of aircraft queried from Tile38
const val STORE_MAX_AIRCRAFT = 30L
// Radius around ownship to fetch aircraft for
const val STORE_MAX_RADIUS = 150000L

const val STORE_METAR_MAX_RADIUS = 200000L

// Expire time for fleet
const val STORE_FLEET_EXPIRE_SECONDS = 60L

// Expire time for aircraft
const val STORE_AIRCRAFT_EXPIRE_SECONDS = 10L

// Expire time for METAR
const val STORE_METAR_EXPIRE_SECONDS = 60 * 60L

const val UBER_H3 = "h3"

// For a visual que https://clupasq.github.io/h3-viewer/
const val H3_RESOLUTION = 4

const val STD_QNH = 1013.25
