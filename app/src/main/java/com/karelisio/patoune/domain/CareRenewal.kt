package com.karelisio.patoune.domain

import com.karelisio.patoune.data.CareItem
import java.time.LocalDate

/** Soins récurrents du carnet de santé (vaccins, vermifuges, antiparasitaires). Logique pure. */
object CareRenewal {
    /** Durées de renouvellement proposées (mois). */
    val choices = listOf(1, 3, 6, 12)

    /** Échéance calculée : [date] + [months] (31 janvier + 1 mois → 28/29 février). */
    fun nextDue(date: String, months: Int?): String? {
        if (months == null || months <= 0) return null
        return Dates.parse(date)?.plusMonths(months.toLong())?.toString()
    }

    /** Applique le renouvellement : si [CareItem.repeatMonths] est renseigné, l'échéance suit la date. */
    fun withComputedDue(item: CareItem): CareItem =
        if (item.repeatMonths == null) item else item.copy(nextDue = nextDue(item.date, item.repeatMonths) ?: item.nextDue)

    /**
     * « Fait aujourd'hui » : nouvelle entrée (même animal, type, produit et renouvellement) datée de [today],
     * avec la prochaine échéance. L'ancienne entrée reste dans l'historique.
     */
    fun doneToday(item: CareItem, today: LocalDate): CareItem = withComputedDue(
        CareItem(
            animalId = item.animalId, kind = item.kind, name = item.name,
            date = today.toString(), repeatMonths = item.repeatMonths,
        ),
    )

    private fun productKey(ci: CareItem) = Triple(ci.animalId, ci.kind, ci.name.trim().lowercase())

    /**
     * Entrée la plus récente par produit (animal + type + nom) : seules ses échéances comptent pour les rappels
     * et l'affichage « en retard », les anciennes restent pour l'historique.
     */
    fun latestPerProduct(items: List<CareItem>): List<CareItem> =
        items.filter { !it.deleted }.groupBy(::productKey).values
            .map { group -> group.maxWith(compareBy<CareItem>({ it.date }, { it.updatedAt })) }

    /** Identifiants des entrées les plus récentes par produit. */
    fun latestIds(items: List<CareItem>): Set<String> = latestPerProduct(items).mapTo(HashSet()) { it.id }
}
