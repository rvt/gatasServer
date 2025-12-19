package nl.rvantwisk.gatas.server

// Filter out all aircraft XXXmeter below ownship
const val STORE_FILTER_BELOW_OWNSHIP = 1000
// Filter out all aircraft XXXmeter above ownship
const val STORE_FILTER_ABOVE_OWNSHIP = 1000

// Maximum altitude difference between ownship and aircraft
const val FETCH_ALT_DIFFERENCE = 1000
// Maximum number of aircraft queried from Tile38,
// TODO optimise this, for example don't return ground vehicles
// WE return plenty of aircraft, because due to filtering we migth have a lot less
const val STORE_MAX_AIRCRAFT = 64L

// Radius around ownship to fetch aircraft for
const val STORE_MAX_RADIUS = 100000L

// When aircraft on the ground within this range they are returned
const val REQUEST_GROUND_DIST = 5000

// Aircraft within this range that are airborn will be returned
const val REQUEST_MAX_DIST = 100000

// Maximum number of aircraft returned within a single request
const val REQUEST_MAX_AIRCRAFT = 15;

const val STORE_METAR_MAX_RADIUS = 200000L

// Expire time for fleet
const val STORE_FLEET_EXPIRE_SECONDS = 60L

// Expire time for aircraft
const val STORE_AIRCRAFT_EXPIRE_SECONDS = 10L

// Expire time for METAR
const val STORE_METAR_EXPIRE_SECONDS = 60 * 60L

const val UBER_H3 = "h3"

const val GROUND = "gnd"

// For a visual que https://clupasq.github.io/h3-viewer/
// Cell size to use when an aircrafts position is stored in the DB, a h3 cell is added to each position
const val H3_AIRCRAFT_CELL_SIZE = 4

const val STD_QNH = 1013.25

/////////////////////// Tile38 keys
// Store a metar index by uberh3 key
const val METAR_BY_H3_KEY = "metarbyh3"

// Store a metar index by station id
const val METAR_BY_STATION_KEY = "metarbystation"

// Key to store aircraft under that was received
const val AIRCRAFT_KEY = "aircraft"

// Key to store a fleet aircraft configuration
const val FLEET_CONFIG_KEY = "fleetConfig"

// Key to store the fleet position under
const val FLEET_KEY = "fleet"
