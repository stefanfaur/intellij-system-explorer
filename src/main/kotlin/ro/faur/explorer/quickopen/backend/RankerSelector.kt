package ro.faur.explorer.quickopen.backend

/**
 * Selects the best available ranker at startup.
 * Priority: NucleoRanker > MinusculeMatcherRanker > FallbackRanker
 */
object RankerSelector {
    private val nucleo = NucleoRanker()
    private val minuscule = MinusculeMatcherRanker()
    private val fallback = FallbackRanker()

    init {
        NucleoNative.tryLoad()
    }

    val active: RankerBackend get() = when {
        nucleo.isAvailable() -> nucleo
        minuscule.isAvailable() -> minuscule
        else -> fallback
    }
}
