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
        "7gold", "antenna3", "canale21", "telecapri", "telecolor", "telecity",
        "telefriuli", "telelombardia", "teleuniverso", "televenezia", "telenorba",
        "teleromagna", "primocanale", "trm", "teleone",
        "emiliaromagna24"
    ).map(::normalize).toSet()

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
