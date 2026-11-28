package com.cascadiacollections.sir.core.artwork

/**
 * A small thread-safe least-recently-used map.
 *
 * Not `android.util.LruCache`, so the lookup it backs is testable on the plain JVM.
 */
class LruCache<K : Any, V>(private val maxSize: Int) {

    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }

    private val map = object : LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, ACCESS_ORDER) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    /** Whether [key] is cached at all — distinguishes a cached null value from a missing entry. */
    @Synchronized
    fun contains(key: K): Boolean = map.containsKey(key)

    /** The cached value for [key], marking it most recently used. */
    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun size(): Int = map.size

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
        const val ACCESS_ORDER = true
    }
}
