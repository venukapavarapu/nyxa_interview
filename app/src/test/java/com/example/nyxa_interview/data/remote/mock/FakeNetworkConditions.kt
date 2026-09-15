package com.example.nyxa_interview.data.remote.mock

/** Deterministic test double: zero latency, zero random failure rate, unless told otherwise. */
class FakeNetworkConditions(
    private val alwaysFail: Boolean = false,
) : NetworkConditions {
    override var forceDropNextSpin: Boolean = false

    override suspend fun simulateLatency() = Unit

    override fun shouldFail(): Boolean = alwaysFail

    override fun consumeForcedSpinDrop(): Boolean {
        if (!forceDropNextSpin) return false
        forceDropNextSpin = false
        return true
    }
}
