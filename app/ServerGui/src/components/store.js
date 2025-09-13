import { El } from "@frameable/el";

let instance;

export class GaTasStore {
  constructor() {
    if (instance) {
      return 
    }

    instance = this;

    this.state = El.observable({
      reConfiguring: false,
      gatasId: 0,
      connected: false,
      newIcaoAddress: null,
      icaoAddress: 0,
      gatasIp: 0,
      _pollingChanges: false,
      icaoAddressList: []
    });
  }

  /**
   * Current gata's ID
   * @param {number} gatasId
   */
  gatasId(gatasId) {
    this.state.gatasId = gatasId;
  }


  /**
   *
   * @param {Number} gatasId
   */
  init(gatasId) {
    this.state.gatasId = gatasId;
    return this.getOwnshipConfiguration();
  }


  /**
   * Get from the Mode-S code the aircrafts information using an external service
   * @param {number} icaoCode
   * @returns {}
   */
  icaoAircraftInfo(icaoCode) {
    const icaoCodeHex = parseInt(icaoCode).toString(16).toUpperCase().padStart(6, '0');
    return store.fetch(`https://api.adsbdb.com/v0/aircraft/${icaoCodeHex}`, {
      }).then((data) => {
        return {
          registration: data.response.aircraft.registration,
          icaoType: data.response.aircraft.icao_type,
          icaoAddress: Number("0x" + data.response.aircraft.mode_s),
        };
      }).catch(() => {
        return {
          registration: "-",
          icaoType: "-",
          localIp: "-.-.-.-",
          icaoAddress: icaoCode,
        }
      })
  }

  /**
   * CHange the current GA/TAS confiuration to a new aircraft configuration
   *
   * @param {number} icaoCode
   * @returns
   */
  changeAircraft(icaoCode) {
    this.state.newIcaoAddress = icaoCode
    this.state.reConfiguring = true;

    return store.fetch(`/api/config/changeAircraft`, {
      method: "POST",
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        gatasId: this.state.gatasId,
        newIcaoAdddress: icaoCode
      }),
    }).then((data) => {
      this.state.reConfiguring = true;
      this.pollStatus();
        return true
    });
  }

  getOwnshipConfiguration() {

    return store.fetch(`/api/config/aircraftConfiguration/${this.state.gatasId}`)
      .then((data) => {
        this.state.icaoAddressList.length = 0;
        data.icaoAddressList.forEach( (e) => {
          this.state.icaoAddressList.push(parseInt(e));
        })
        this.state.icaoAddress = parseInt(data.icaoAddress);
        this.state.newIcaoAddress = parseInt(data.newIcaoAddress);
        this.state.gatasIp = data.gatasIp;
        return true
      });

    // this.state.icaoAddress = 4738995
    // this.state.newIcaoAddress = null
    // this.state.icaoAddressList = [4738995, 4739478, 4744401]

    // if (this.state.newIcaoAddress !== null && this.state.icaoAddress !== this.state.newIcaoAddress) {
    //   this.state.reConfiguring = true;
    // }

    // return Promise.resolve(true)
  }

  pollStatus() {
    this.state._pollingChanges = true;
    this.__pollStatus();
  }

  __pollStatus() {
    this.fetch(`/api/config/aircraftConfiguration/${this.state.gatasId}`)
      .then((data) => {
        if (data.newIcaoAddress === data.icaoAddress || data.newIcaoAddress === undefined) {
          this.state._pollingChanges = false;
          this.state.reConfiguring = false;
          this.getOwnshipConfiguration();
        }
      })
      .finally(() => {
        if (this.state._pollingChanges) {
          setTimeout(() => {
            this.__pollStatus();
          }, 750);
        }
      });
  }

  /**
   * Fetch operation with default handler
   *
   * @param {} path
   * @param {*} requestOptions
   * @returns
   */
  fetch(path, requestOptions) {
    return fetch(path, {
      ...requestOptions,
      signal: AbortSignal.timeout(5500),
    })
    .then((response) => {
      if (!response.ok) {
        throw new Error("Network response was not ok");
      }
      this.state.connected = true;
      return response.json();
    })
    .catch((e) => {
      this.state.connected = false;
      throw new Error(e);
    });
  }

  // /**
  //  * Update store aircraft array
  //  *
  //  */
  // _updateAircraftArray() {
  //   this.state.aircrafts.length = 0;
  //   for (var prop in this.state.aircraftsObj) {
  //     this.state.aircrafts.push({
  //       name: prop,
  //       ...this.state.aircraftsObj[prop],
  //     });
  //   }
  //   this.state.numberOfAircrafts = this.state.aircrafts.length;
  // }

  // /**
  //  * Initialize the store
  //  *
  //  * @returns
  //  */
  // async init() {
  //   // // Fetch config.json
  //   // const configData = await this.fetch("/api/_Configuration/config.json");
  //   // this.state.aircraftId = configData.aircraftId;
  //   // this.state.configModified = configData._dirty;

  //   // // Fetch aircraft.json
  //   // const aircraftData = await this.fetch("/api/_Configuration/aircraft.json");
  //   // Object.assign(this.state.aircraftsObj, aircraftData);
  //   // this._updateAircraftArray();

  //   // // Validate if the configured aircraft exists
  //   // if (aircraftData[this.state.aircraftId] === undefined) {
  //   //   if (this.state.aircrafts.length > 0) {
  //   //     await this.setDefaultAirCraftId(this.state.aircrafts[0].callSign);
  //   //   } else {
  //   //     this.state.aircraftId = "";
  //   //   }
  //   // }

  //   // // Fetch hardware.json
  //   // const hardwareData = await this.fetch("/api/_Configuration/hardware.json");
  //   // Object.assign(this.state.hardware, hardwareData);
  //   // this.state.hardwareName = this.availableHardware.find((d) => d.hardware === hardwareData.type)?.name;
  // }

  // /**
  //  * Delete an aircraft by ID from teh aircrafts path
  //  *
  //  * @param {*} aircraftId
  //  * @returns
  //  */
  // deleteAircraft(aircraftId) {
  //   return this.fetch(`/api/_Configuration/aircraft/${aircraftId}.json`, {
  //     method: "POST",
  //     headers: {
  //       "X-Method": "DELETE",
  //     },
  //     body: "{}",
  //   }).then((data) => {
  //     delete this.state.aircraftsObj[aircraftId];
  //     this._updateAircraftArray();
  //     return this.init();
  //   });
  // }

  // /**
  //  * Update aircraft in the aircrafts path
  //  *
  //  * @param {*} aircraft
  //  * @returns
  //  */
  // updateAircraft(aircraft) {
  //   return this.fetch(`/api/_Configuration/aircraft/${aircraft.callSign}.json`, {
  //     method: "POST",
  //     headers: {
  //       "X-Method": "POST",
  //     },
  //     body: JSON.stringify(aircraft),
  //   }).then((data) => {
  //     this.state.aircraftsObj[aircraft.callSign] = {};
  //     Object.assign(this.state.aircraftsObj[aircraft.callSign], aircraft);
  //     this._updateAircraftArray();
  //     return data;
  //   });
  // }

  // /**
  //  * Update the type of board this is running on
  //  * @param {*} type
  //  * @returns
  //  */
  // updateHardware(typeIdx) {
  //   const type = this.availableHardware[typeIdx].hardware;
  //   this.state.hardwareName = this.availableHardware[typeIdx].name;
  //   return this.fetch(`/api/_Configuration/hardware.json`, {
  //     method: "POST",
  //     headers: {
  //       "X-Method": "POST",
  //     },
  //     body: JSON.stringify({ type }),
  //   }).then((data) => {
  //     this.state.hardware.type = type;
  //     return data;
  //   });
  // }

  // /**
  //  * Set the default aircraft ID in the config path
  //  *
  //  * @param {aircraftId of the aircraft that needs to be set as default} aircraftId
  //  * @returns
  //  */
  // setDefaultAirCraftId(aircraftId) {
  //   const url = "/api/_Configuration/config.json";
  //   return store
  //     .fetch(url)
  //     .then((data) => {
  //       data.aircraftId = aircraftId;
  //       return this.fetch(url, {
  //         method: "POST",
  //         body: JSON.stringify(data),
  //       });
  //     })
  //     .then((data) => {
  //       this.state.aircraftId = aircraftId;
  //       return data;
  //     });
  // }

  // getModuleData(moduleName) {
  //   return store.fetch(`/api/_Configuration/${moduleName}.json`).then((data) => {
  //     // null means configuration did not exists.
  //     if (data == null) {
  //       return {};
  //     } else {
  //       return data;
  //     }
  //   });
  // }

}

let store = Object.freeze(new GaTasStore());
export default store;
