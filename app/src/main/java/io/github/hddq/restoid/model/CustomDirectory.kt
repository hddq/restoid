package io.github.hddq.restoid.model

data class CustomDirectory(
    val uri: String,
    val isSelected: Boolean = true,
    val isExcluded: Boolean = false
)
