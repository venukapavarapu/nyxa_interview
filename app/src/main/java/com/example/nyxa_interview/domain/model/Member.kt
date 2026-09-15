package com.example.nyxa_interview.domain.model

enum class MembershipTier { ANNUAL, MONTHLY, NONE }

data class Member(
    val id: String,
    val displayName: String,
    val membership: MembershipTier,
    val state: String,
)
