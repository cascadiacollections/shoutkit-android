# Launch post drafts

Drafts only. Nothing here has been posted. Before posting, check every link opens and that the
v1.3.1 release carries the signed APK; fill the bracketed parts and delete this note.

Facts the posts rely on, so they stay true:

- "No tracking" describes the iPhone app and the Android FOSS build (GitHub Releases, F-Droid).
  The Google Play build includes Firebase Analytics and Crashlytics. Say so if Play comes up.
- The app does contact three things: the Radio Browser directory (told by default when you start
  a station, to rank stations; can be turned off), the stations you play, and Apple's iTunes
  Search for album art (can be turned off).
- iPhone is a public TestFlight beta, not an App Store release.
- Mention F-Droid only as "submitted" or "listed", whichever is true on the day.

Links:

- Android site: https://cascadiacollections.github.io/shoutkit-android/
- Android source: https://github.com/cascadiacollections/shoutkit-android
- iPhone site: https://cascadiacollections.github.io/shoutkit/
- iPhone source: https://github.com/cascadiacollections/shoutkit

---

## Show HN

**Title** (80 characters max):

> Show HN: ShoutKit – open-source internet radio for iPhone and Android, no account

**URL:** https://cascadiacollections.github.io/shoutkit-android/

**First comment:**

> ShoutKit is an internet radio player with no account, no tracking and no ads. It is open
> source, and it runs on iPhone and Android.
>
> I wanted a radio app that plays a station and otherwise leaves me alone. You search the
> community-run Radio Browser directory, or paste a stream URL, tap play, and it keeps playing in
> the background. There are favorites, a sleep timer, an equalizer, a widget, Android Auto and
> Android TV. Your stations stay on the device and you can export them.
>
> Details people here usually ask about:
>
> - The Android APK is a FOSS build with no Google Play services, Firebase, analytics or crash
>   reporting. The build is reproducible, so you can check the APK against the source.
> - The app talks to Radio Browser, to the stations you play, and to Apple's iTunes Search for
>   album art. Radio Browser is told when you start a station so popular ones rank higher. Both
>   that and the artwork lookup can be turned off in Settings.
> - The app is GPL-3.0. The reusable parts (stream handling, time-shift, audio levels) are MIT
>   libraries.
> - iPhone is a public TestFlight beta: https://cascadiacollections.github.io/shoutkit/
>
> Source: https://github.com/cascadiacollections/shoutkit-android and
> https://github.com/cascadiacollections/shoutkit
>
> I'd like to hear which stations fail to play and what is missing compared with the radio app
> you use now.

---

## r/fdroid

**Title:**

> ShoutKit: internet radio with no account and no tracking, open source, reproducible build [F-Droid status]

Replace `[F-Droid status]` with "(F-Droid inclusion requested)" or "(now on F-Droid)".

**Body:**

> ShoutKit is an internet radio player: no account, no tracking, no ads, open source (GPL-3.0),
> for Android and iPhone.
>
> What it does: search the Radio Browser directory or add a stream by URL, background playback,
> favorites and recents, import and export, sleep timer, equalizer, widget, Android Auto and
> Android TV. Android 7.0 and later.
>
> What F-Droid users will care about:
>
> - The FOSS flavor has no Google Play services, Firebase, analytics or crash reporting.
> - The build is reproducible. The F-Droid recipe asks F-Droid to verify its build against the
>   developer-signed APK and ship that one, so the GitHub and F-Droid APKs are the same file and
>   update each other.
> - Network use: Radio Browser, the stations themselves, and Apple's iTunes Search for album
>   art, which is why the recipe declares NonFreeNet. Artwork lookup and Radio Browser play
>   counts can both be turned off in Settings.
> - Chromecast and the Wear OS companion need Google libraries, so they are only in the Google
>   Play build.
>
> Signed APK: https://github.com/cascadiacollections/shoutkit-android/releases/latest
> Source: https://github.com/cascadiacollections/shoutkit-android
> [F-Droid merge request or listing link]
>
> Bug reports and missing-feature requests are welcome in the issue tracker.

---

## r/opensource

**Title:**

> ShoutKit: an open-source internet radio app for iPhone and Android with no account and no tracking

**Body:**

> I built ShoutKit because most radio apps want an account, show ads or collect analytics. This
> one has no account, no tracking and no ads. It is open source and runs on iPhone and Android.
>
> - Stations come from Radio Browser, a community-run directory anyone can add to or correct.
>   You can also add any stream by its URL.
> - The apps are GPL-3.0. The parts that could be useful to other projects are split out as MIT
>   libraries.
> - The Android FOSS build has no Google services and builds reproducibly. iPhone is a public
>   TestFlight beta.
> - It is free. Nothing is locked behind payment.
>
> Android: https://github.com/cascadiacollections/shoutkit-android
> iPhone: https://github.com/cascadiacollections/shoutkit
> Site: https://cascadiacollections.github.io/shoutkit-android/
>
> Contributions are welcome: code, translations (English and Simplified Chinese so far), and
> reports of stations that do not play.
