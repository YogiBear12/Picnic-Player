package app.picnic.player.data.auth

data class UserScope(val serverId: String, val userId: String) {
    fun key(base: String): String = prefix(serverId, userId) + base

    companion object {
        const val ACTIVE_SESSION = "active_session"

        fun encode(serverId: String, userId: String): String = "$serverId|$userId"

        fun decode(value: String?): UserScope? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 2) return null
            return UserScope(parts[0], parts[1])
        }

        fun prefix(serverId: String, userId: String): String = "user.$serverId.$userId."

        fun serverPrefix(serverId: String): String = "user.$serverId."
    }
}
