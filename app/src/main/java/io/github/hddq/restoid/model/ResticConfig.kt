package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable

@Serializable
data class ResticConfig(
    val id: String,
    val version: Int,
    val chunker_polynomial: String
)
