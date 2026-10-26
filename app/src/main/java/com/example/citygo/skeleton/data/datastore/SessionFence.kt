package com.example.citygo.skeleton.data.datastore

/** generation 区分 A→B→A 和退出后重新登录，防止旧请求“碰巧”遇到相同账号而生效。 */
data class SessionSnapshot(val generation: Long, val phone: String?, val token: String?) {
    val storageKey: String get() = AccountStorage.keyFor(phone)
    override fun toString(): String = "SessionSnapshot(generation=$generation)"
}

class SessionFence {
    private var current = SessionSnapshot(0, null, null)

    @Synchronized fun snapshot(): SessionSnapshot = current

    @Synchronized fun update(phone: String?, token: String?): SessionSnapshot {
        val validToken = token?.takeIf { it.isNotBlank() }
        val validPhone = phone?.trim()?.takeIf { it.isNotEmpty() && validToken != null }
        val normalizedToken = validToken.takeIf { validPhone != null }
        if (current.phone != validPhone || current.token != normalizedToken) {
            current = SessionSnapshot(current.generation + 1, validPhone, normalizedToken)
        }
        return current
    }

    @Synchronized fun isCurrent(snapshot: SessionSnapshot): Boolean = current == snapshot
}
