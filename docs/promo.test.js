const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const promo = require("./promo.js");

function zonedMinuteToDate(localMinute, timeZone) {
  const [datePart, timePart] = localMinute.split("T");
  const [year, month, day] = datePart.split("-").map(Number);
  const [hour, minute] = timePart.split(":").map(Number);
  let utc = Date.UTC(year, month - 1, day, hour, minute);
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const got = promo.pacificMinute(new Date(utc), timeZone);
    if (got === localMinute) return new Date(utc);
    utc += Date.parse(localMinute + ":00Z") - Date.parse(got + ":00Z");
  }
  throw new Error("could not resolve " + localMinute + " in " + timeZone);
}

test("the promo window is Black Friday through December 1, 2026, Pacific", () => {
  assert.equal(promo.PROMO.timeZone, "America/Los_Angeles");
  assert.equal(promo.PROMO.start, "2026-11-27T00:00");
  assert.equal(promo.PROMO.end, "2026-12-01T23:59");
  assert.equal(promo.PROMO.previewParam, "promo");
  assert.equal(promo.PROMO.previewValue, "bf2026");
});

test("minute keys roll hour 24 onto the next calendar day", () => {
  assert.equal(promo.minuteKey({ year: "2026", month: "12", day: "1", hour: "24", minute: "0" }), "2026-12-02T00:00");
  assert.equal(promo.minuteKey({ year: "2026", month: "11", day: "30", hour: "24", minute: "00" }), "2026-12-01T00:00");
  assert.equal(promo.minuteKey({ year: "2026", month: "12", day: "31", hour: "24", minute: "00" }), "2027-01-01T00:00");
  assert.equal(promo.minuteKey({ year: "2026", month: "11", day: "27", hour: "00", minute: "00" }), "2026-11-27T00:00");
});

test("the window is inclusive of the start and end minutes in Pacific time", () => {
  const start = zonedMinuteToDate(promo.PROMO.start, promo.PROMO.timeZone);
  const end = zonedMinuteToDate(promo.PROMO.end, promo.PROMO.timeZone);
  assert.equal(promo.pacificMinute(start), "2026-11-27T00:00");
  assert.equal(promo.pacificMinute(end), "2026-12-01T23:59");
  assert.equal(promo.inPromoWindow(new Date(start.getTime() - 1000)), false);
  assert.equal(promo.inPromoWindow(start), true);
  assert.equal(promo.inPromoWindow(new Date(end.getTime() + 59 * 1000)), true);
  assert.equal(promo.inPromoWindow(new Date(end.getTime() + 60 * 1000)), false);
  assert.equal(promo.inPromoWindow(new Date("2026-11-27T00:30:00Z")), false);
  assert.equal(promo.inPromoWindow(new Date("2026-11-23T12:00:00Z")), false);
});

test("the window follows the configured time zone rather than the machine zone", () => {
  const utc = { ...promo.PROMO, timeZone: "UTC" };
  const fridayMorningUtc = new Date("2026-11-27T00:30:00Z");
  assert.equal(promo.inPromoWindow(fridayMorningUtc, utc), true);
  assert.equal(promo.inPromoWindow(fridayMorningUtc), false);
  const lateMondayPacific = new Date("2026-12-02T07:30:00Z");
  assert.equal(promo.inPromoWindow(lateMondayPacific), true);
  assert.equal(promo.inPromoWindow(lateMondayPacific, utc), false);
});

test("preview, dismissal, and the real window combine without cookies", () => {
  const during = zonedMinuteToDate("2026-11-28T15:00", promo.PROMO.timeZone);
  const before = zonedMinuteToDate("2026-11-26T23:59", promo.PROMO.timeZone);
  assert.deepEqual(promo.promoVisibility(before, "", false), { banner: false, section: false });
  assert.deepEqual(promo.promoVisibility(during, "", false), { banner: true, section: true });
  assert.deepEqual(promo.promoVisibility(during, "", true), { banner: false, section: true });
  assert.deepEqual(promo.promoVisibility(before, "?promo=bf2026", false), { banner: true, section: true });
  assert.deepEqual(promo.promoVisibility(before, "?utm=1&promo=bf2026", true), { banner: true, section: true });
  assert.deepEqual(promo.promoVisibility(before, "?promo=BF2026", false), { banner: false, section: false });
  assert.deepEqual(promo.promoVisibility(before, "?sale=bf2026", false), { banner: false, section: false });
});

test("dismissal is remembered only as the localStorage flag", () => {
  const storage = new Map();
  const memory = {
    getItem(key) { return storage.has(key) ? storage.get(key) : null; },
    setItem(key, value) { storage.set(key, value); }
  };
  assert.equal(promo.readDismissed(memory, promo.PROMO.storageKey), false);
  promo.rememberDismissal(memory, promo.PROMO.storageKey);
  assert.equal(storage.get(promo.PROMO.storageKey), "1");
  assert.equal(promo.readDismissed(memory, promo.PROMO.storageKey), true);
  assert.equal(promo.readDismissed({ getItem() { throw new Error("blocked"); } }, promo.PROMO.storageKey), false);
  assert.doesNotThrow(() => promo.rememberDismissal({ setItem() { throw new Error("blocked"); } }, promo.PROMO.storageKey));
});

test("the homepage keeps the promo out of the layout until promo.js reveals it", () => {
  const html = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");
  const css = fs.readFileSync(path.join(__dirname, "style.css"), "utf8");
  const source = fs.readFileSync(path.join(__dirname, "promo.js"), "utf8");

  for (const id of ["bf-banner", "bf-promo"]) {
    const tag = html.match(new RegExp(`<[^>]*id="${id}"[^>]*>`));
    assert.ok(tag, "missing #" + id);
    assert.match(tag[0], /\shidden(?:[\s>]|$)/);
  }
  assert.match(html, /<script src="promo\.js"><\/script>/);
  assert.match(html, /data-promo-dismiss/);
  assert.match(html, /type="button"/);
  assert.match(html, /aria-label="Black Friday and Cyber Monday"/);
  assert.match(html, /aria-label="Dismiss Black Friday promotion"/);

  const downloadTags = [...html.matchAll(/<a\b[^>]*\bdata-nightly-download\b[^>]*>/g)].map((match) => match[0]);
  const hrefs = downloadTags.map((tag) => {
    const href = tag.match(/href="([^"]+)"/);
    assert.ok(href, tag);
    return href[1];
  });
  assert.ok(hrefs.length >= 6);
  assert.equal(new Set(hrefs).size, 1, "promo downloads must reuse the existing nightly link");

  assert.match(css, /\.bf-banner\[hidden\],#bf-promo\[hidden\]\{display:none\}/);
  assert.match(css, /prefers-reduced-motion:reduce/);
  assert.doesNotMatch(source, /document\.cookie|sessionStorage|gtag|googletagmanager|fetch\(/i);
  assert.match(source, /localStorage/);
  assert.doesNotMatch(html, /<script[^>]+src="https?:\/\//);
});
