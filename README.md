# GA/TAS Server

**GA/TAS Server** is a proof-of-concept service for the [GA/TAS project](https://github.com/rvt/openace).  
It provides ADS-B ingress to GA/TAS, enabling pilots to receive live ADS-B traffic data directly in their electronic flight bag (EFB).

---

## ✈️ Data Sources

### ADS-B Feeds
- [airplanes.live](http://airplanes.live)  
- [adsb.fi](http://adsb.fi)  
- [adsb.lol](https://adsb.lol)  

### METAR Weather
- [aviationweather.gov](https://aviationweather.gov)  

---

## 🛠 Technologies

- [Tile38](https://tile38.com) — spatial database and geofencing  
- [Redis](https://redis.io) — in-memory datastore and caching  

---

## 💻 Implementation

- **Backend**: Kotlin with [Ktor](https://ktor.io)  
- **Frontend**: JavaScript  

---

## 🚧 Status

This project is experimental and under active development. Expect breaking changes as the architecture evolves.
