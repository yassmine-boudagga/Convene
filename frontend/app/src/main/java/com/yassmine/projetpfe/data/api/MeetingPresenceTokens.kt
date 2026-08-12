package com.yassmine.projetpfe.data.api

fun JoinedParticipantDto.identityPresenceTokens(): Set<String> = buildSet {
    fun addNorm(s: String?) {
        val t = s?.trim()?.takeIf { it.isNotBlank() } ?: return
        add(t.lowercase())
    }
    addNorm(id)
    addNorm(email)
}
fun MeetingDto.joinedPresenceTokenSet(): Set<String> =
    joinedParticipants.flatMapTo(mutableSetOf()) { it.identityPresenceTokens() }

fun participantPresenceCandidates(
    userId: String?,
    email: String?,
): Set<String> = buildSet {
    fun addNorm(s: String?) {
        val t = s?.trim()?.takeIf { it.isNotBlank() } ?: return
        add(t.lowercase())
    }
    addNorm(userId)
    addNorm(email)
}
