const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");

const root = __dirname;

function read(relative) {
  return fs.readFileSync(path.join(root, relative), "utf8");
}

test("the Play privacy page describes Firebase and the Install Referrer, and the FOSS build excludes them", () => {
  const page = read("privacy/play/index.html");
  assert.match(page, /Firebase Analytics/);
  assert.match(page, /Crashlytics/);
  assert.match(page, /Play Install Referrer/);
  assert.match(page, /advertising id/);
  assert.match(page, /utm_content/);
  assert.match(page, /firebase_campaign/);
  assert.match(page, /com\.cascadiacollections\.sir\.foss/);
  assert.match(page, /does not include Firebase Analytics, Firebase Crashlytics, or the Play Install Referrer/);
  assert.doesNotMatch(page, /no analytics/);
});

test("the FOSS privacy page still excludes Firebase and links to the Play page", () => {
  const page = read("privacy/index.html");
  assert.match(page, /excludes Firebase Analytics, Crashlytics, the Play Install Referrer/);
  assert.match(page, /href="play\/"/);
  assert.doesNotMatch(page, /privacy\/play\/index/);
});

test("Settings links the Play flavor at the Play page and leaves the FOSS URL in main", () => {
  const main = read("../app/src/main/res/values/strings.xml");
  const play = read("../app/src/play/res/values/strings.xml");
  assert.match(
    main,
    /name="privacy_policy_url"[^>]*>https:\/\/cascadiacollections\.github\.io\/shoutkit-android\/privacy\/</
  );
  assert.match(
    play,
    /name="privacy_policy_url"[^>]*>https:\/\/cascadiacollections\.github\.io\/shoutkit-android\/privacy\/play\/</
  );
});

test("the Play application id and the FOSS suffix are unchanged", () => {
  const gradle = read("../app/build.gradle.kts");
  assert.match(gradle, /applicationId = "com\.cascadiacollections\.sir"/);
  assert.match(gradle, /applicationIdSuffix = "\.foss"/);
  assert.match(gradle, /"playImplementation"\(libs\.firebase\.analytics\)/);
  assert.match(gradle, /"playImplementation"\(libs\.firebase\.crashlytics\)/);
  assert.match(gradle, /"playImplementation"\(libs\.installreferrer\)/);
});

test("the Data safety checklist is an internal record and names what Kevin submits", () => {
  const doc = read("play-data-safety.md");
  assert.match(doc, /Kevin enters and submits the form/);
  assert.match(doc, /Play Console → App content → Data safety/);
  assert.match(doc, /Advertising ID/);
  assert.match(doc, /not a copy of a submitted Play Console form/);
  assert.match(doc, /privacy\/play\//);
});
