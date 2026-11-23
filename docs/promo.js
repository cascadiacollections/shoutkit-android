// Black Friday through Cyber Monday 2026.
//
// The marketing site is hand-written static HTML, not a site generator, so nothing
// rebuilds this page on a schedule. The window below is the one place to change
// the dates, and promo.js applies it in the browser.
//
// Active from start through end, inclusive, as wall-clock minutes in timeZone.
// Outside that window the banner and section stay hidden (they ship with the
// hidden attribute), so there is no layout change and no cleanup deploy.
// With JavaScript disabled they stay hidden too.
//
// Preview outside the window: ?promo=bf2026
// Dismissing the banner stores PROMO.storageKey in localStorage. No cookies.
(function () {
  "use strict";

  var PROMO = {
    id: "bf2026",
    timeZone: "America/Los_Angeles",
    start: "2026-11-27T00:00",
    end: "2026-12-01T23:59",
    previewParam: "promo",
    previewValue: "bf2026",
    storageKey: "shoutkit-bf2026-dismissed"
  };

  // Some engines label midnight as 24:00 on the previous calendar day.
  function minuteKey(parts) {
    var year = Number(parts.year);
    var month = Number(parts.month);
    var day = Number(parts.day);
    var hour = Number(parts.hour);
    var minute = Number(parts.minute);
    if (hour === 24) {
      hour = 0;
      var rolled = new Date(Date.UTC(year, month - 1, day));
      rolled.setUTCDate(rolled.getUTCDate() + 1);
      year = rolled.getUTCFullYear();
      month = rolled.getUTCMonth() + 1;
      day = rolled.getUTCDate();
    }
    function pad(value) {
      return String(value).padStart(2, "0");
    }
    return year + "-" + pad(month) + "-" + pad(day) + "T" + pad(hour) + ":" + pad(minute);
  }

  function pacificMinute(date, timeZone) {
    var fmt = new Intl.DateTimeFormat("en-US", {
      timeZone: timeZone || PROMO.timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
      calendar: "gregory",
      numberingSystem: "latn"
    });
    var parts = {};
    fmt.formatToParts(date).forEach(function (part) {
      if (part.type !== "literal") parts[part.type] = part.value;
    });
    return minuteKey(parts);
  }

  function inPromoWindow(date, promo) {
    var config = promo || PROMO;
    var minute = pacificMinute(date, config.timeZone);
    return minute >= config.start && minute <= config.end;
  }

  function previewOn(search, promo) {
    var config = promo || PROMO;
    var query = search.charAt(0) === "?" ? search.slice(1) : search;
    return new URLSearchParams(query).get(config.previewParam) === config.previewValue;
  }

  function readDismissed(storage, key) {
    if (!storage) return false;
    try {
      return storage.getItem(key) === "1";
    } catch (err) {
      return false;
    }
  }

  function rememberDismissal(storage, key) {
    if (!storage) return;
    try {
      storage.setItem(key, "1");
    } catch (err) {
      // Storage can be blocked. The banner still closes for this page view.
    }
  }

  // Preview always shows the promo, including after a dismissal, so the
  // switch stays useful. A dismissal during the real window hides the banner
  // only; the short section remains part of the page.
  function promoVisibility(date, search, dismissed, promo) {
    var preview = previewOn(search, promo);
    var active = preview || inPromoWindow(date, promo);
    return {
      banner: active && (preview || !dismissed),
      section: active
    };
  }

  var api = {
    PROMO: PROMO,
    minuteKey: minuteKey,
    pacificMinute: pacificMinute,
    inPromoWindow: inPromoWindow,
    previewOn: previewOn,
    readDismissed: readDismissed,
    rememberDismissal: rememberDismissal,
    promoVisibility: promoVisibility
  };

  if (typeof module === "object" && module.exports) module.exports = api;
  if (typeof document === "undefined") return;

  function boot() {
    var banner = document.getElementById("bf-banner");
    var section = document.getElementById("bf-promo");
    if (!banner && !section) return;

    var storage = null;
    try {
      storage = window.localStorage;
    } catch (err) {
      storage = null;
    }

    var search = window.location.search;
    var visibility = promoVisibility(new Date(), search, readDismissed(storage, PROMO.storageKey));
    if (section && visibility.section) section.hidden = false;
    if (!banner || !visibility.banner) return;

    banner.hidden = false;
    var preview = previewOn(search);

    function dismiss() {
      if (!preview) rememberDismissal(storage, PROMO.storageKey);
      banner.hidden = true;
      if (banner.contains(document.activeElement)) {
        var next = document.querySelector("nav a.brand");
        if (next) next.focus();
      }
    }

    var button = banner.querySelector("[data-promo-dismiss]");
    if (button) button.addEventListener("click", dismiss);
    document.addEventListener("keydown", function (event) {
      if (event.key !== "Escape" || banner.hidden) return;
      var target = event.target;
      if (target && typeof target.closest === "function" && target.closest("input, textarea, select, [contenteditable]")) return;
      event.preventDefault();
      dismiss();
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
