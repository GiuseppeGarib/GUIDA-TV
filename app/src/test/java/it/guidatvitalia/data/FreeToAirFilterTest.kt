package it.guidatvitalia.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeToAirFilterTest {
    @Test
    fun keepsFreeChannelsAndDropsPayChannelsAndTheirPrograms() {
        val input = EpgData(
            channels = listOf(
                EpgChannel("rai1", "Rai 1 HD"),
                EpgChannel("canale5", "Canale 5"),
                EpgChannel("skycinema1", "Sky Cinema Uno"),
                EpgChannel("tv8", "TV8 HD")
            ),
            programs = listOf(
                EpgProgram("rai1", "TG", null, null, 100L, 200L),
                EpgProgram("canale5", "Film", null, null, 200L, 300L),
                EpgProgram("skycinema1", "Film premium", null, null, 300L, 400L),
                EpgProgram("tv8", "Show", null, null, 400L, 500L)
            )
        )

        val filtered = FreeToAirFilter.apply(input)

        assertEquals(setOf("rai1", "canale5", "tv8"), filtered.channels.map { it.id }.toSet())
        assertEquals(3, filtered.programs.size)
        assertEquals(false, filtered.programs.any { it.channelId == "skycinema1" })
    }

    @Test
    fun keepsOnlyOneBroadcasterForEachNationalLcn() {
        val input = EpgData(
            channels = listOf(
                EpgChannel("20", "20 Mediaset"),
                EpgChannel("20mediaset.it", "20 HD"),
                EpgChannel("27", "27"),
                EpgChannel("twentyseven", "Twentyseven")
            ),
            programs = listOf(
                EpgProgram("20", "Programma A", null, null, 1L, 2L),
                EpgProgram("20mediaset.it", "Programma B", null, null, 2L, 3L),
                EpgProgram("27", "Programma C", null, null, 3L, 4L),
                EpgProgram("twentyseven", "Programma D", null, null, 4L, 5L)
            )
        )
        val filtered = FreeToAirFilter.apply(input)
        assertEquals(1, filtered.channels.count { FreeToAirFilter.lcnFor(it) == 20 })
        assertEquals(1, filtered.channels.count { FreeToAirFilter.lcnFor(it) == 27 })
        assertEquals(filtered.channels.map { it.id }.toSet(), filtered.programs.map { it.channelId }.toSet())
    }

    @Test
    fun keepsDistinctUnnumberedLocalBroadcasters() {
        val filtered = FreeToAirFilter.apply(EpgData(
            channels = listOf(EpgChannel("telecolor", "Telecolor"), EpgChannel("telecity", "Telecity")),
            programs = emptyList()
        ))
        assertEquals(2, filtered.channels.size)
    }

    @Test
    fun recognizesChannelByIdWhenDisplayNameIsDifferent() {
        val input = EpgData(
            channels = listOf(EpgChannel("rai2.it", "Nome non riconosciuto")),
            programs = listOf(EpgProgram("rai2.it", "Programma", null, null, 1L, 2L))
        )

        val filtered = FreeToAirFilter.apply(input)

        assertEquals(listOf("rai2.it"), filtered.channels.map { it.id })
        assertEquals(1, filtered.programs.size)
    }
    @Test
    fun assignsLcnPositionsAfterSixtyFour() {
        assertEquals(65, FreeToAirFilter.lcnFor(EpgChannel("almatv", "Alma TV")))
        assertEquals(66, FreeToAirFilter.lcnFor(EpgChannel("radio105", "Radio 105")))
        assertEquals(68, FreeToAirFilter.lcnFor(EpgChannel("bomchannel", "BOM Channel")))
        assertEquals(122, FreeToAirFilter.lcnFor(EpgChannel("canale122", "Canale 122")))
    }

    @Test
    fun keepsRecognizedFreeChannelsBeyondSixtyFour() {
        val input = EpgData(
            channels = listOf(
                EpgChannel("almatv", "Alma TV"),
                EpgChannel("radio105", "Radio 105"),
                EpgChannel("skycinema", "Sky Cinema")
            ),
            programs = listOf(
                EpgProgram("almatv", "Programma", null, null, 1L, 2L),
                EpgProgram("radio105", "Musica", null, null, 2L, 3L),
                EpgProgram("skycinema", "Film", null, null, 3L, 4L)
            )
        )
        val filtered = FreeToAirFilter.apply(input)
        assertEquals(setOf("almatv", "radio105"), filtered.channels.map { it.id }.toSet())
        assertEquals(2, filtered.programs.size)
    }

}
