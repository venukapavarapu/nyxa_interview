package com.example.nyxa_interview.core.idempotency

import java.util.UUID
import javax.inject.Inject

/**
 * Generates client-side idempotency keys for mutating requests (spin, box open, checkout).
 * A fresh key is generated once per logical user action, then reused across retries of
 * that same action so the server can de-duplicate charges.
 */
interface IdempotencyKeyGenerator {
    fun generate(): String
}

class UuidIdempotencyKeyGenerator @Inject constructor() : IdempotencyKeyGenerator {
    override fun generate(): String = UUID.randomUUID().toString()
}
