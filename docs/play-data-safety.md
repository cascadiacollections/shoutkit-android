# Play Data safety answers

This file records the Data safety answers supported by the Play build's code and by the
Firebase SDKs it actually depends on. It is not a copy of a submitted Play Console form.
The repository has no record of answers already entered in Play Console.

Kevin enters and submits the form himself, in Play Console → App content → Data safety.
He also sets the store listing's privacy policy URL and the separate Advertising ID
declaration. This repository does not change Play Console or store settings.

The privacy policy URL for the Play listing is the Play page, not the FOSS page:

https://cascadiacollections.github.io/shoutkit-android/privacy/play/

That page is `docs/privacy/play/index.html`. GitHub Pages serves the `gh-pages` branch, and
the nightly job only rewrites the download link there. Copy `docs/privacy/` (including
`privacy/play/`) to `gh-pages` before the listing or the Play build sends anyone to that
URL. Do not publish this file. It is an internal checklist, not the policy.

The in-app Privacy Policy row in the Play flavor opens that URL. The FOSS flavor keeps
`https://cascadiacollections.github.io/shoutkit-android/privacy/`. F-Droid listing text in
`fastlane/metadata/android/en-US/` stays on the FOSS page, because that listing describes
the FOSS build.

## SDK versions this record was checked against

Re-check this file when `firebaseBom` in `gradle/libs.versions.toml` changes. Google's
[Play data disclosure](https://firebase.google.com/docs/android/play-data-disclosure)
describes only the latest SDK, and the answers below were checked against the versions
this project resolves, not against a newer SDK.

| Dependency | Version | Where |
| --- | --- | --- |
| `firebase-bom` | 35.2.0 | `gradle/libs.versions.toml` |
| `firebase-analytics` | 24.0.0 | BOM; `playImplementation` in `app/build.gradle.kts` |
| `play-services-measurement` | 24.0.0 | dependency of `firebase-analytics` |
| `firebase-crashlytics` | 21.1.0 | BOM; `playImplementation` |
| `firebase-installations` | 20.0.0 | dependency of Crashlytics |
| `firebase-sessions` | 4.0.2 | dependency of Crashlytics |
| `installreferrer` | 2.2 | `playImplementation`; the app's own reader |

`foss` does not get these dependencies. `applicationIdSuffix = ".foss"` is unchanged.
`src/foss` `CampaignGenreSourceImpl` returns null and does not link the Install Referrer
client. Plugins for Google services and Crashlytics run only when
`app/src/play/google-services.json` exists.

The app never calls `FirebaseAnalytics` or `FirebaseCrashlytics`. It does not log custom
events, set user properties or a user id, or attach custom keys, logs, or non-fatal
exceptions. It does not set `firebase_analytics_collection_deactivated` or
`google_analytics_adid_collection_enabled`.

In `play-services-measurement-impl` 24.0.0, `zzal.zzt()` treats collection as deactivated
only when `firebase_analytics_collection_deactivated` is true. Absent, collection stays on.
`zzal.zzu()` returns true for advertising-id collection when
`google_analytics_adid_collection_enabled` is absent, and false only when that flag is
false. `zzal.zzv()` does the same for automatic screen reporting.

The measurement SDK manifests that merge into the Play APK declare, and this project does
not remove:

- `com.google.android.gms.permission.AD_ID`
- `android.permission.ACCESS_ADSERVICES_AD_ID`
- `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`
- `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`

## Do not describe the Play build as the FOSS build

The Play build collects analytics and crash data. Do not answer "no data collected", and
do not use the FOSS wording ("no analytics", "no crash reporting").

## Answers to enter

Play's form is Kevin's to submit. The rows below are the answers the code and Google's
disclosures support. Purposes use Play's labels. Google's own purpose language is noted
where it does not use those labels.

### App activity → App interactions

- Collected: yes.
- Shared: yes, with Google (Firebase / Google Analytics).
- Processed ephemerally: no.
- Optional: no. Settings cannot turn Analytics off.
- Purposes: Analytics and App functionality.

The app logs none of these itself. They are the automatic events that apply to a build
with Analytics and Crashlytics and without AdMob, Cloud Messaging, Dynamic Links, In-App
Messaging, or Play Billing. Google's
[automatically collected events](https://support.google.com/analytics/answer/9234069)
list: `first_open`, `session_start`, `user_engagement`, `screen_view`, `app_update`,
`os_update`, `app_remove`, `app_clear_data`, `firebase_campaign` when the install has
campaign parameters, and `app_exception` because Crashlytics is integrated. Default app
parameters on those events include the app version and the current screen.

`firebase_campaign` is the campaign attribution the issue asks about. Google's parameter
list for that event is source, medium, campaign, term, content, `gclid`, `aclid`, `cp1`,
`anid`, click timestamp, and campaign info source. The Analytics SDK reads the Play
Install Referrer itself. That is a second reader, separate from the app's own client.

Do not declare ad impressions, notification events, or purchase events. Those products
are not dependencies.

### Device or other IDs

- Collected: yes.
- Shared: yes, with Google.
- Processed ephemerally: no.
- Optional: no.
- Purposes: Analytics and App functionality.

Declare:

- The Firebase installation id (the app instance id). Google's installations disclosure
  says it does not uniquely identify a user or physical device. Crashlytics depends on
  that SDK and rotates its own id from it.
- The Crashlytics installation UUID, which Google says is used to count installations
  affected by a crash.
- The advertising id. Collection is on by default in this SDK, the opt-out flag is unset,
  and the advertising-id permission is merged.

Play Console has a separate Advertising ID question under App content. Declare that the
Play app uses the advertising id. The FOSS build does not. Also review the two Ad
Services permissions above. They come from the Analytics SDK manifest. This file does not
invent a separate Attribution API payload beyond those permissions being merged.

### Crash logs

- Collected: yes. Stack traces and relevant application state when the app crashes.
  Google's Crashlytics disclosure.
- Shared: yes, with Google.
- Processed ephemerally: no.
- Optional: no.
- Purpose: App functionality. Google's stated purpose is crash reporting and crash
  management. The app adds no custom keys, logs, user ids, or non-fatal reports.

### Diagnostics

- Collected: yes.
- Shared: yes, with Google.
- Processed ephemerally: no.
- Optional: no.
- Purpose: Analytics.

This is the Crashlytics and sessions data that is not the stack trace itself:

- Point-in-time device metadata on a crash (Google's Crashlytics disclosure; it does not
  list the fields).
- Sessions SDK, which Crashlytics 21.1.0 depends on: package name, operating-system
  information, SDK version, network connection type, device manufacturer and model, and
  foreground time. Google's disclosure says this provides crash and performance metrics.
- The installations user agent: operating-system version, device name, model, brand, and
  form factor, the installer app, and which Firebase SDKs are present, with versions.
  Google says this user agent is not linked to a user or device identifier.
- Analytics breadcrumb logs of actions immediately before a crash, which Google's
  disclosure says Crashlytics uses when Analytics is also included. Those are the
  automatic Analytics events. The app does not write its own breadcrumbs.

## Security questions

- Encrypted in transit: yes for the Firebase data above. Google's disclosure says HTTPS.
  The Radio Browser directory API and iTunes Search also use HTTPS. Some station audio
  and images are plain HTTP, because the app allows cleartext so those stations can play
  (`app/src/main/res/xml/network_security_config.xml`). Answer encryption for the data
  types declared. Do not claim every stream is HTTPS.
- Can users request deletion: not answered here. There is no account. The app does not
  show the Firebase installation id or the Crashlytics installation id. Google's
  disclosure says Firebase lets the developer delete end-user data in line with each
  product. The privacy page tells people to open a GitHub issue. Choose Yes on the form
  only if Cascadia will delete that Firebase data on request, and say how. The repository
  has no deletion URL and no support email.

Collection of Analytics and Crashlytics is not optional. The Play app has no consent
prompt and no settings switch for either SDK.

Google's disclosure says Firebase does not transfer the listed data to other third
parties except subprocessors that assist Firebase, or when the developer instructs a
transfer. The app does not link Firebase to another product in code. If the Firebase
project is later linked to an ads account, ad-personalization disclosures change and
this file has to be updated. The app includes no ads SDK.

## What the app's own Install Referrer client does

`app/src/play/.../CampaignGenreSourceImpl.kt` reads `installReferrer` once, on first
launch (`InstallReferrerGenre.kt`). It keeps `utm_content` only when that value is an
allow-listed welcome genre (`christmas`, `talk`, `rock`, `news`, `jazz`, `country` in
`FirstRunGenre.kt`), and only when the welcome has not already set a genre. A welcome
link on that launch is applied first. The raw string, source, medium, campaign, term,
click id, and timestamps are not stored. The click and install timestamps and the
instant-experience flag are not read. The genre stays in on-device preferences and is
not sent to Firebase.

That on-device genre is not "collected" in Play's sense by the app's own code, because
the app does not transmit it. Firebase's separate referrer read can still send campaign
parameters, including `utm_content`, to Google. Declare that under App interactions.
Do not declare the app as storing `gclid`.

## Other network use, shared with the FOSS build

These are not Firebase. The form must not describe the Play build as sending nothing,
and it must not invent extra categories for them. Whether Play's data types cover them
is Kevin's decision. The privacy page discloses them.

- Radio Browser, over HTTPS. By default, starting a directory station reports that
  station's id (`json/url/{stationuuid}`). The Settings switch "Report plays to Radio
  Browser" defaults to on.
- Album artwork, on by default. The current artist and title are sent to
  `https://itunes.apple.com/search`. Settings can turn it off.
- Audio streams and station images go to the hosts in the station record. Some of those
  hosts are HTTP. The connection carries an IP address and ordinary request headers.
  The app does not add an account or an analytics id to those requests.

## Not collected

Do not declare these for the Play build. Nothing in the app or in the SDKs it includes
collects them:

- Name, email address, phone number, or physical address. There is no account.
- A developer-set Analytics user id, custom events, or custom user properties. The
  first-run genre is not a user property.
- Location. The app does not request a location permission.
- Photos, contacts, calendar, audio files the user owns, or financial information.
- Purchases or subscriptions. There is no Play Billing dependency.

## FOSS and F-Droid

F-Droid and GitHub Releases ship the `foss` flavor. Its privacy page is the one that
says analytics and crash reporting are excluded, and that statement is true for that
build only. Do not paste these Play answers into the F-Droid listing.
