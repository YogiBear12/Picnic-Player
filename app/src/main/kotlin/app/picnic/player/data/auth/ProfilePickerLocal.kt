package app.picnic.player.data.auth

/**
 * On-disk profile-picker inputs for one server, readable synchronously from
 * [AuthRepository]'s in-memory snapshot after [AuthRepository.warmLocalCache].
 */
data class ProfilePickerLocal(
    val server: ServerConnection,
    val stored: List<StoredSession>,
    val cachedPublic: List<PublicUserInfo>,
    val profileOrder: List<String>,
    val authErrors: Map<String, String> = emptyMap()
)
