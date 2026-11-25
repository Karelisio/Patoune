package com.karelisio.patoune.domain

/**
 * Horloge logique hybride : (temps mur ms, compteur, appareil).
 * L'encodage texte est triable lexicographiquement, ce qui rend la fusion déterministe.
 */
data class Hlc(val wall: Long, val counter: Int, val node: String) : Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int =
        compareValuesBy(this, other, Hlc::wall, Hlc::counter, Hlc::node)

    fun encode(): String = "%015d-%06d-%s".format(wall, counter, node)

    companion object {
        fun decode(s: String): Hlc {
            val a = s.split("-", limit = 3)
            return Hlc(a[0].toLong(), a[1].toInt(), a[2])
        }
    }
}

/** Générateur d'horloge local, thread-safe. */
class HlcClock(private val node: String, private val now: () -> Long = System::currentTimeMillis) {
    private var last = Hlc(0, 0, node)

    @Synchronized
    fun tick(): Hlc {
        val wall = now()
        last = if (wall > last.wall) Hlc(wall, 0, node) else Hlc(last.wall, last.counter + 1, node)
        return last
    }

    /** Avance l'horloge à la réception d'un événement distant. */
    @Synchronized
    fun observe(remote: Hlc) {
        val wall = now()
        val maxWall = maxOf(wall, last.wall, remote.wall)
        last = when {
            maxWall == last.wall && maxWall == remote.wall -> Hlc(maxWall, maxOf(last.counter, remote.counter) + 1, node)
            maxWall == last.wall -> Hlc(maxWall, last.counter + 1, node)
            maxWall == remote.wall -> Hlc(maxWall, remote.counter + 1, node)
            else -> Hlc(maxWall, 0, node)
        }
    }
}
