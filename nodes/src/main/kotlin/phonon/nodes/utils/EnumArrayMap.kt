package phonon.nodes.utils

/**
 * Wrapper to create EnumArrayMap from an initializer function.
 * Note this internally uses `enumValues` and `map` so that
 * allocated lists during initialization, so avoid using this 
 * repeatedly in hot paths.
 */
inline fun <reified K: Enum<K>, reified T> createEnumArrayMap(init: (K) -> T): EnumArrayMap<K, T> =
    EnumArrayMap(enumValues<K>().map(init).toTypedArray())

/**
 * Alternative to EnumMap where all enum values must be mapped
 * to some value. This avoids doing null key checks in EnumMap.
 * This is useful for situations where every enum requires a
 * mapping. This is backed by an array accessed directly
 * by enum.ordinal without doing any bounds or null key checks.
 * 
 * For implementing iterator, issue with reified generic type.
 * See:
 * https://stackoverflow.com/questions/49855273/kotlin-generic-iterator-over-enum-how-to-declare-type-variables
 */
public class EnumArrayMap<K: Enum<K>, T>(
    val array: Array<T>,
) {
    /**
     * Getter and setter operators to allow `map[key]` style access.
     */
    operator fun get(key: K) = this.array[key.ordinal]
    
    operator fun set(key: K, value: T) {
        this.array[key.ordinal] = value
    }

    /**
     * Return array size.
     */
    public val size: Int get() = this.array.size

    /**
     * Return a copy with backing array cloned.
     * Note actual internal elements are just shallow-copied references.
     */
    public fun copyOf(): EnumArrayMap<K, T> {
        return EnumArrayMap(this.array.copyOf())
    }
}
