import { El } from "@frameable/el";

class ButtonLoader extends El {
    render(html) {
        return html`
          <button  ${this.loading?'disabled':''} style="width:100%">
            <slot></slot><span aria-busy="true" style="opacity: ${this.loading?100:0};" ></span>
          </button>
        `;
    }
}

customElements.define("button-loader", ButtonLoader);
