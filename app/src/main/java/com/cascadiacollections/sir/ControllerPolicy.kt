package com.cascadiacollections.sir

/**
 * Which apps may connect to [RadioPlaybackService].
 *
 * The service is exported so Android Auto, Android Automotive and other system media surfaces can
 * bind to it and browse stations. Exporting it means any installed app could also bind, so
 * `onConnect` only accepts:
 * - this app (UI, tile, widget, Cast coordinator, headless activities);
 * - controllers the platform trusts for media control (MEDIA_CONTENT_CONTROL, the status bar,
 *   the system, or an enabled notification listener), plus Media3's own notification controller;
 * - Android Auto, its companion and Automotive OS hosts, as Media3 identifies them;
 * - the Google apps in [KNOWN_CONTROLLERS] that drive media sessions without one of the above.
 *
 * Package names come from the binder caller's uid, so another app cannot claim one of these.
 */
internal object ControllerPolicy {

    val KNOWN_CONTROLLERS = setOf(
        "com.google.android.projection.gearhead", // Android Auto
        "com.google.android.carassistant", // Assistant on Automotive OS
        "com.google.android.googlequicksearchbox", // Google Assistant
        "com.google.android.wearable.app" // Wear OS phone app (media controls on the watch)
    )

    fun isAllowed(
        controllerPackage: String,
        ownPackage: String,
        isTrusted: Boolean,
        isSystemSurface: Boolean
    ): Boolean = controllerPackage == ownPackage ||
        isTrusted ||
        isSystemSurface ||
        controllerPackage in KNOWN_CONTROLLERS
}
