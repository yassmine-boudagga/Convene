package com.yassmine.projetpfe.data.model

enum class MeetingType { ONLINE, PHYSICAL }

enum class MeetingStatus { UPCOMING, ONGOING, FINISHED, ARCHIVED }

data class Meeting(
    val id: String,
    val title: String,
    val date: String,           
    val time: String,         
    val participants: Int,
    val duration: String,       
    val type: MeetingType,
    val status: MeetingStatus,
    val location: String? = null
)
