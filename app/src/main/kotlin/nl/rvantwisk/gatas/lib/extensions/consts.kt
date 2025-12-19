package nl.rvantwisk.gatas.lib.extensions

import kotlin.math.PI

const val AIRCRAFT_POSITION_TYPE_V1 = 1
const val AIRCRAFT_POSITION_REQUEST_V1 = 2
const val AIRCRAFT_CONFIGURATIONS_V1 = 3
const val SET_ICAO_ADDRESS_V1 = 4


const val CRC16_BYTE_SIZE = 2
const val MAX_CALLSIGN_LENGTH = 12
const val SPEED = "speed"
const val TRACK = "track"
const val TURN_RATE = "turn"
const val VERTICAL_RATE = "vrate"
const val ALTITUDE = "alti"
const val DATA_SOURCE = "rsc" // Data Source
const val AIRCRAFT_CATEGORY = "aca"  // Aircraft Category
const val ADDRESS_TYPE = "aty"  // Adress Type
const val CALL_SIGN = "cas"  // Call Sign

const val TRANSITION_ALTITUDE_QNH = 1013.25;

const val MS_TO_FTPMIN = 196.850394         // meter/sec to feet/min
const val FTPMIN_TO_MS = 1.0 / MS_TO_FTPMIN // feet/min to meter/sec
const val KN_TO_MS = 0.514444444            // knots to meter/sec
const val RAD_TO_DEGREES = 180 / PI;
const val FT_TO_METERS = 0.3048;
const val METERS_TO_FT = 1 / FT_TO_METERS
