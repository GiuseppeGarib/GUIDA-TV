package it.guidatvitalia.data

import java.util.Locale

/**
 * Mantiene nella guida soltanto i canali televisivi italiani in chiaro
 * presenti nella selezione editoriale dell'app. Il feed EPG originale può
 * contenere anche servizi a pagamento: non vengono salvati né mostrati.
 */
object FreeToAirFilter {
    private val allowed = setOf(
        "20", "20mediaset", "27", "twentyseven", "27twentyseven", "boing", "canale5", "cartoonito", "cielo", "cine34", "canaleitalia", "canaleitalia83", "canaleitalia84",
        "dmax", "focus", "foodnetwork", "frisbee", "giallo", "hgtv", "hse24",
        "iris", "italia1", "k2", "la5", "la7", "la7cinema", "la7d",
        "mediasetextra", "motortrend", "nove", "qvc", "qvc2", "tgcom24", "tv2000", "telepace", "radioitalia", "rtl1025", "byoblu", "il61",
        "rai1", "rai2", "rai3", "rai4", "rai5", "raigulp", "raimovie",
        "rainews24", "raipremium", "raisc uola", "raiscuola", "raisport",
        "raistoria", "raiyoyo", "realtime", "rete4", "sanmarinortv",
        "sportitalia", "super", "supertennis", "topcrime", "tv8", "warnertv",
        "almatv", "radio105", "r101", "deejaytv", "radioitaliatv", "bomchannel",
        "solocalcio", "donnasporttv", "gm24", "france24", "rtvunicasanmarino",
        "7gold", "antenna3", "canale21", "telecapri", "telecolor", "telecity",
        "telefriuli", "telelombardia", "teleuniverso", "televenezia", "telenorba",
        "teleromagna", "primocanale", "trm", "teleone",
        "emiliaromagna24"
    ).map(::normalize).toSet()

    /** LCN nazionale e principali alias; le locali senza posizione certa restano in coda. */
    fun lcnFor(channel: EpgChannel): Int {
        val keys = variants(channel.name) + variants(channel.id)
        val lcn = mapOf(
            "rai1" to 1, "rai2" to 2, "rai3" to 3, "rete4" to 4, "canale5" to 5,
            "italia1" to 6, "la7" to 7, "tv8" to 8, "nove" to 9,
            "20" to 20, "20mediaset" to 20, "rai4" to 21, "iris" to 22, "rai5" to 23,
            "raimovie" to 24, "raipremium" to 25, "cielo" to 26, "27" to 27,
            "twentyseven" to 27, "tv2000" to 28, "la7d" to 29, "la5" to 30,
            "realtime" to 31, "qvc" to 32, "foodnetwork" to 33, "cine34" to 34,
            "focus" to 35, "warnertv" to 37, "giallo" to 38, "topcrime" to 39,
            "boing" to 40, "k2" to 41, "raigulp" to 42, "raiyoyo" to 43,
            "frisbee" to 44, "cartoonito" to 46, "super" to 47, "rainews24" to 48,
            "dmax" to 52, "mediasetextra" to 55, "hgtv" to 56, "motortrend" to 59,
            "sportitalia" to 60, "solocalcio" to 61, "donnasporttv" to 62, "gm24" to 63,
            "supertennis" to 64, "almatv" to 65, "radio105" to 66, "r101" to 67,
            "bomchannel" to 68, "deejaytv" to 69, "radioitaliatv" to 70
        )
        return keys.firstNotNullOfOrNull { lcn[it] } ?: 1000
    }

    fun apply(data: EpgData): EpgData {
        val channels = data.channels.filter(::isAllowed)
        val ids = channels.mapTo(hashSetOf()) { it.id }
        return EpgData(channels, data.programs.filter { it.channelId in ids })
    }

    private fun isAllowed(channel: EpgChannel): Boolean =
        (variants(channel.name) + variants(channel.id)).any { it in allowed }

    private fun variants(value: String): Set<String> {
        val base = normalize(value).removeSuffix("it")
        val noHd = base.replace(Regex("hd\\d*$"), "")
        val noLcn = noHd.replace(Regex("\\d{3}$"), "")
        return setOf(base, noHd, noLcn)
    }

    private fun normalize(value: String) =
        value.lowercase(Locale.ITALY).replace(Regex("[^a-z0-9]"), "")
}
