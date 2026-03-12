import { i18n } from "../../i18n";

class TimeEntrySlotForm extends HTMLFormElement {
  #hasBeenTriedToSubmitAtLeastOnce = false;
  #autoFillEnabled = true;

  connectedCallback() {
    // prevent html validation messages. we're doing it ourself here with JavaScript
    this.setAttribute("novalidate", "");

    this.addEventListener("turbo:submit-start", () => {
      this.querySelector(".ajax-loader")?.classList.add("ajax-loader--loading");
    });

    this.addEventListener("submit", (event) => {
      this.#hasBeenTriedToSubmitAtLeastOnce = true;
      if (!this.#validate()) {
        event.preventDefault();
      }
    });

    const errorContainer = this.querySelector(
      "[data-error-container]",
    ) as HTMLElement;
    const startElement = this.querySelector(
      "input[name='start']",
    ) as HTMLInputElement;
    const endElement = this.querySelector(
      "input[name='end']",
    ) as HTMLInputElement;
    const durationElement = this.querySelector(
      "input[name='duration']",
    ) as HTMLInputElement;

    startElement.addEventListener("blur", () => {
      console.debug("Blur event triggered on start input");
      if (this.#autoFillEnabled) {
        this.#autoFillDuration(startElement, durationElement);
      }

      if (!this.#hasBeenTriedToSubmitAtLeastOnce) {
        return;
      }
      if (startElement.value) {
        startElement.setCustomValidity("");
        if (!endElement.value && !durationElement.value) {
          errorContainer.innerHTML = `<ul><li>${i18n(
            "time-entry.validation.endOrDuration.required",
          )}</li></ul>`;
        } else {
          errorContainer.innerHTML = ``;
          durationElement.setCustomValidity("");
        }
      } else {
        if (endElement.value && durationElement.value) {
          startElement.setCustomValidity("");
          errorContainer.innerHTML = ``;
        } else if (!endElement.value && !durationElement.value) {
          startElement.setCustomValidity("required");
          durationElement.setCustomValidity("required");
          errorContainer.innerHTML = ``;
        } else if (!endElement.value && durationElement.value) {
          startElement.setCustomValidity("required");
          endElement.setCustomValidity("required");
          errorContainer.innerHTML = `<ul><li>${i18n(
            "time-entry.validation.startOrEnd.required",
          )}</li></ul>`;
        } else {
          startElement.setCustomValidity("required");
          durationElement.setCustomValidity("required");
          errorContainer.innerHTML = `<ul><li>${i18n(
            "time-entry.validation.startOrDuration.required",
          )}</li></ul>`;
        }
      }
    });

    endElement.addEventListener("blur", () => {
      if (!this.#hasBeenTriedToSubmitAtLeastOnce) {
        return;
      }
      if (endElement.value) {
        endElement.setCustomValidity("");
        if (!startElement.value && !durationElement.value) {
          errorContainer.innerHTML = `<ul><li>${i18n(
            "time-entry.validation.startOrDuration.required",
          )}</li></ul>`;
        } else {
          errorContainer.innerHTML = ``;
          durationElement.setCustomValidity("");
        }
      } else {
        if (startElement.value && durationElement.value) {
          endElement.setCustomValidity("");
          errorContainer.innerHTML = ``;
        } else if (!startElement.value && !durationElement.value) {
          endElement.setCustomValidity("required");
          durationElement.setCustomValidity("required");
          errorContainer.innerHTML = ``;
        } else if (!startElement.value && durationElement.value) {
          endElement.setCustomValidity("required");
          startElement.setCustomValidity("required");
          errorContainer.innerHTML = `<ul><li>${i18n(
            "time-entry.validation.startOrEnd.required",
          )}</li></ul>`;
        } else {
          endElement.setCustomValidity("required");
          durationElement.setCustomValidity("required");
          errorContainer.innerHTML = `<ul><li>${i18n(
            "time-entry.validation.endOrDuration.required",
          )}</li></ul>`;
        }
      }
    });

    durationElement.addEventListener("blur", () => {
      if (durationElement.value && !/\d\d:\d\d/.test(durationElement.value)) {
        durationElement.setCustomValidity("pattern");
        errorContainer.innerHTML = `<ul><li>${i18n(
          "time-entry.validation.duration.pattern",
        )}</li></ul>`;
        return;
      } else if (!this.#hasBeenTriedToSubmitAtLeastOnce) {
        durationElement.setCustomValidity("");
        errorContainer.innerHTML = ``;
        return;
      }

      if (!this.#hasBeenTriedToSubmitAtLeastOnce) {
        return;
      }

      if (durationElement.value) {
        durationElement.setCustomValidity("");
        if (!startElement.value && !endElement.value) {
          errorContainer.innerHTML = `<ul><li>${i18n(
            "time-entry.validation.startOrEnd.required",
          )}</li></ul>`;
        } else {
          errorContainer.innerHTML = ``;
          startElement.setCustomValidity("");
          endElement.setCustomValidity("");
        }
      } else {
        if (startElement.value && endElement.value) {
          durationElement.setCustomValidity("");
          errorContainer.innerHTML = ``;
        } else if (!startElement.value && !endElement.value) {
          durationElement.setCustomValidity("required");
          errorContainer.innerHTML = ``;
        } else if (!startElement.value && endElement.value) {
          durationElement.setCustomValidity("required");
          startElement.setCustomValidity("required");
          errorContainer.innerHTML = `<ul><li>${i18n(
            "time-entry.validation.startOrDuration.required",
          )}</li></ul>`;
        } else {
          durationElement.setCustomValidity("required");
          endElement.setCustomValidity("required");
          errorContainer.innerHTML = `<ul><li>${i18n(
            "time-entry.validation.endOrDuration.required",
          )}</li></ul>`;
        }
      }
    });
  }

  #validate(): boolean {
    const errorContainer = this.querySelector(
      "[data-error-container]",
    ) as HTMLElement;
    const startElement = this.querySelector(
      "input[name='start']",
    ) as HTMLInputElement;
    const endElement = this.querySelector(
      "input[name='end']",
    ) as HTMLInputElement;
    const durationElement = this.querySelector(
      "input[name='duration']",
    ) as HTMLInputElement;

    let valid = true;
    let errorMessage = "";

    if (!startElement.value && !endElement.value && !durationElement.value) {
      startElement.setCustomValidity("required");
      endElement.setCustomValidity("required");
      durationElement.setCustomValidity("required");
      valid = false;
    } else if (
      startElement.value &&
      !endElement.value &&
      !durationElement.value
    ) {
      endElement.setCustomValidity("required");
      durationElement.setCustomValidity("required");
      errorMessage = i18n("time-entry.validation.endOrDuration.required");
      valid = false;
    } else if (
      !startElement.value &&
      endElement.value &&
      !durationElement.value
    ) {
      startElement.setCustomValidity("required");
      durationElement.setCustomValidity("required");
      errorMessage = i18n("time-entry.validation.startOrDuration.required");
      valid = false;
    } else if (
      !startElement.value &&
      !endElement.value &&
      durationElement.value
    ) {
      startElement.setCustomValidity("required");
      endElement.setCustomValidity("required");
      errorMessage = i18n("time-entry.validation.startOrEnd.required");
      valid = false;
    }

    if (errorMessage) {
      errorContainer.innerHTML = `<ul><li>${errorMessage}</li></ul>`;
    }

    return valid;
  }

  async #autoFillDuration(
    startElement: HTMLInputElement,
    durationElement: HTMLInputElement,
  ) {
    try {
      const dateElement = this.querySelector(
        "input[name='date']",
      ) as HTMLInputElement;
      const userLocalIdElement = this.querySelector(
        "input[name='userLocalId']",
      ) as HTMLInputElement;

      if (!dateElement?.value || !startElement.value) {
        console.debug("Auto-fill: Missing date or start time", {
          date: dateElement?.value,
          startTime: startElement.value,
        });
        return;
      }

      // Build API URL
      const parameters = new URLSearchParams({
        date: dateElement.value,
        startTime: startElement.value,
      });

      if (userLocalIdElement?.value) {
        parameters.append("userLocalId", userLocalIdElement.value);
      }

      const apiUrl = `/api/timeentries/remaining-hours?${parameters}`;
      console.debug("Auto-fill: Calling API", apiUrl);

      const response = await fetch(apiUrl);

      if (!response.ok) {
        console.error(
          "Auto-fill: API call failed",
          response.status,
          response.statusText,
        );
        return;
      }

      const data = await response.json();

      if (data.remainingHours && !durationElement.value) {
        durationElement.value = data.remainingHours;
        console.debug("Auto-fill: Set duration to", data.remainingHours);
        // Dispatch input event to trigger any listeners
        durationElement.dispatchEvent(new Event("input", { bubbles: true }));
      } else {
        console.debug("Auto-fill: No remaining hours or duration already set", {
          hasRemainingHours: !!data.remainingHours,
          durationValue: durationElement.value,
        });
      }
    } catch (error) {
      // Silently fail to not break the user experience
      console.error("Failed to auto-fill duration:", error);
    }
  }
}

customElements.define("z-time-entry-slot-form", TimeEntrySlotForm, {
  extends: "form",
});

export default TimeEntrySlotForm;
