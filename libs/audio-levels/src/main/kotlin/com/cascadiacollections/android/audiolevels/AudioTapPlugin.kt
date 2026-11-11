package com.cascadiacollections.android.audiolevels

import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Something a playback engine hands rendered PCM to: a level meter, a loudness or silence
 * detector, a recorder. The engine owns the tap; plug-ins only see buffers.
 *
 * [process] runs on the engine's audio thread (Media3's playback thread for a
 * `TeeAudioProcessor`), never the main thread, and must not block. Read what you need from
 * [pcm] between its position and limit without moving them; it is not yours after the call.
 */
fun interface AudioTapPlugin {
    fun process(pcm: ByteBuffer, encoding: PcmEncoding, channelCount: Int, sampleRate: Int)
}

/**
 * The engine side of [AudioTapPlugin]: a thread-safe set plug-ins are added to and removed from
 * on any thread, and that the engine dispatches to from its audio thread. One engine tap, any
 * number of plug-ins. Dispatching to an empty set costs one read of an empty array.
 */
class AudioTapPluginSet {
    private val plugins = CopyOnWriteArrayList<AudioTapPlugin>()

    /** Adds [plugin]. Adding one already present (by identity) does nothing. */
    fun add(plugin: AudioTapPlugin) {
        plugins.addIfAbsent(plugin)
    }

    fun remove(plugin: AudioTapPlugin) {
        plugins.remove(plugin)
    }

    val isEmpty: Boolean get() = plugins.isEmpty()

    val size: Int get() = plugins.size

    /**
     * Hands [pcm] to every plug-in, in the order they were added. Iterates a snapshot, so a
     * plug-in that adds or removes taps from inside [AudioTapPlugin.process] can't deadlock.
     */
    fun dispatch(pcm: ByteBuffer, encoding: PcmEncoding, channelCount: Int, sampleRate: Int) {
        for (plugin in plugins) {
            plugin.process(pcm, encoding, channelCount, sampleRate)
        }
    }
}
