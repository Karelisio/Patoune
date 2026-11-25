package com.karelisio.patoune.ui

import kotlinx.serialization.Serializable

object Routes {
    @Serializable object Home
    @Serializable object Treatments
    @Serializable object Breath
    @Serializable object Journal
    @Serializable object Health
    @Serializable object Settings
    @Serializable object Permissions
    @Serializable object Emergency
    @Serializable object Onboarding
    @Serializable data class AnimalEdit(val id: String? = null)
    @Serializable data class MedicationEdit(val id: String? = null)
    @Serializable data class MedicationDetail(val id: String)
    @Serializable data class BreathCounter(val night: Boolean = false)
    @Serializable object BreathAuto
    @Serializable data class CrisisEdit(val id: String? = null, val startRecording: Boolean = false)
    @Serializable data class Camera(val video: Boolean, val attachType: String, val attachId: String? = null, val autoStart: Boolean = false)
    @Serializable data class MediaViewer(val id: String)
    @Serializable object Gallery
    @Serializable object Report
    @Serializable object Air
    @Serializable object Animals
    @Serializable object Household
    @Serializable object Backup
}
