package app.picnic.player.data.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PicnicInfo(
    @SerialName("PluginVersion") val pluginVersion: String? = null,
    @SerialName("Seerr") val seerr: PicnicSeerrInfo? = null,
    @SerialName("Reviews") val reviews: PicnicFeatureInfo? = null,
    @SerialName("Streamers") val streamers: PicnicStreamersInfo? = null
)

@Serializable
data class PicnicSeerrInfo(
    @SerialName("Enabled") val enabled: Boolean = false,
    @SerialName("Url") val url: String? = null
)

@Serializable
data class PicnicFeatureInfo(
    @SerialName("Enabled") val enabled: Boolean = false
)

@Serializable
data class PicnicStreamersInfo(
    @SerialName("Enabled") val enabled: Boolean = false,
    @SerialName("DefaultRegion") val defaultRegion: String? = null
)
