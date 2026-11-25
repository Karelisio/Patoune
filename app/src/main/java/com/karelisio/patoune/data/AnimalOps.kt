package com.karelisio.patoune.data

/** Opérations sur les animaux (passent par le Store pour être synchronisées). */
object AnimalOps {
    /** Supprime l'animal et ses traitements (tombstones). Retourne les traitements supprimés. */
    suspend fun delete(store: Store, db: PatouneDb, animal: Animal): List<Medication> {
        val meds = db.medications().allRows().filter { it.animalId == animal.id && !it.deleted }
        meds.forEach { store.delete(it) }
        db.animals().byId(animal.id)?.let { store.delete(it) }
        return meds
    }

    /** Nouvel ordre après déplacement de [from] vers [to] ; seuls les animaux dont l'ordre change sont réécrits. */
    fun reorder(list: List<Animal>, from: Int, to: Int): List<Animal> {
        if (from !in list.indices || to !in list.indices || from == to) return emptyList()
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        return m.mapIndexedNotNull { i, a -> if (a.sortOrder != i) a.copy(sortOrder = i) else null }
    }

    suspend fun move(store: Store, list: List<Animal>, from: Int, to: Int) {
        reorder(list, from, to).forEach { store.save(it) }
    }
}
