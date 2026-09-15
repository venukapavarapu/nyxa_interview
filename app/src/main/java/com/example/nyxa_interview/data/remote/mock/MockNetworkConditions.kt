package com.example.nyxa_interview.data.remote.mock

import kotlin.random.Random
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/** Seam for latency/failure simulation so tests can swap in a deterministic, zero-delay fake. */
interface NetworkConditions {
    suspend fun simulateLatency()
    fun shouldFail(): Boolean
    var forceDropNextSpin: Boolean
    fun consumeForcedSpinDrop(): Boolean
}

/**
 * Shared latency/failure simulation for the mock backend, matching the assignment spec:
 * 400-1500ms latency, 15% random failure rate. A debug-only flag forces the next spin to
 * "drop" (server settles, response never reaches the client) so the recovery path can be
 * demoed on demand instead of waiting for the random 15% to line up.
 */
@Singleton
class MockNetworkConditions @Inject constructor() : NetworkConditions {

    private val random: Random = Random.Default

    @Volatile
    override var forceDropNextSpin: Boolean = false

    override suspend fun simulateLatency() {
        delay(random.nextLong(MIN_LATENCY_MS, MAX_LATENCY_MS + 1))
    }

    override fun shouldFail(): Boolean = random.nextInt(100) < FAILURE_RATE_PERCENT

    override fun consumeForcedSpinDrop(): Boolean {
        if (!forceDropNextSpin) return false
        forceDropNextSpin = false
        return true
    }

    private companion object {
        const val MIN_LATENCY_MS = 400L
        const val MAX_LATENCY_MS = 1500L
        const val FAILURE_RATE_PERCENT = 15
    }
}
