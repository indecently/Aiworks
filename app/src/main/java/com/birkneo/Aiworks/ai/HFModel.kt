package com.birkneo.Aiworks.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class HFModel(
    @SerialName("id") val id: String,
    @SerialName("author") val author: String? = null,
    @SerialName("lastModified") val lastModified: String? = null,
    @SerialName("siblings") val siblings: List<HFSibling>? = emptyList(),
    @SerialName("downloads") val downloads: Int? = 0,
    @SerialName("likes") val likes: Int? = 0,
    @SerialName("tags") val tags: List<String>? = emptyList()
)

@Serializable
data class HFSibling(
    @SerialName("rfilename") val rfilename: String,
    @SerialName("size") val size: Long? = null,
    @SerialName("lfs") val lfs: HFLfs? = null
)

@Serializable
data class HFLfs(
    @SerialName("size") val size: Long? = null,
    @SerialName("sha256") val sha256: String? = null
)

data class HFModelUIState(
    val model: HFModel,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val downloadProgress: Float? = null,
    val isDownloading: Boolean = false,
    val formattedSize: String? = null,
    val hasRamWarning: Boolean = false,
    val quantizationTags: List<String> = emptyList()
)
