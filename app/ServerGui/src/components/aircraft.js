import { El } from "@frameable/el";
import store from "./store";

class AircraftModule extends El {
  created() {
    this.state = this.$observable({ aircraft: {} });
  }

  mounted() {
    this._fetchData();
  }

  unmounted() {
  }

  // _select() {
  //   let mode_s = this.state.aircraft?.icaoAddress
  //   if (!this._isCurrent()) {
  //     store.changeAircraft(mode_s);
  //     if (this.onselect) {
  //       this.onselect(mode_s)
  //     }
  //   }
  // }

  async _fetchData() {
    if (this.modes) {
      this._loading = true;
      const modeS = parseInt(this.modes).toString(16).toUpperCase();

      store.icaoAircraftInfo(this.modes)
        .then((data) => {

          data.icaoAddressHex = parseInt(data.icaoAddress).toString(16).toUpperCase().padStart(6, '0');
          Object.assign(this.state.aircraft, data);
        })
    }
  }

    _isCurrent() {
        // Weird equal issue, using == for the moment
        return store.state.icaoAddress === this.state.aircraft?.icaoAddress;
    }

  render(html) {
    const isCurrent = this._isCurrent();
    const isPending = this.state.aircraft?.icaoAddress === store.state.newIcaoAddress && !this._isCurrent();
    return html`
      <article class="${isCurrent && 'selected'} ${isPending && 'pending'}" >
        ${isPending ? html`<progress></progress>` : html`<progress value=0" max="100"></progress>`}
        <div>${this.state.aircraft?.registration}</div>
        <div>${this.state.aircraft?.icaoType}</div>
        <div style="justify-content: space-between;display: flex;">
          <div>${this.state.aircraft?.icaoAddressHex}</div>
          <div>${isCurrent && '✔'}${isPending && '&nbsp;'}</div>
        </div>
      </article>
    `;
  }
}

customElements.define("aircraft-config", AircraftModule);
