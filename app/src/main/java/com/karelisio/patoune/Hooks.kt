package com.karelisio.patoune

import com.karelisio.patoune.data.DoseLog
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.PushType

/** Réactions transverses aux événements métier (widget, sync, pushs). */
open class Hooks(private val c: AppContainer) {
    open suspend fun onDose(med: Medication, log: DoseLog) {
        onDataChanged()
    }
    open suspend fun onPush(type: PushType, title: String, body: String, refId: String?) {}
    open suspend fun onDataChanged() {}
    open suspend fun onDaily() {}
}
