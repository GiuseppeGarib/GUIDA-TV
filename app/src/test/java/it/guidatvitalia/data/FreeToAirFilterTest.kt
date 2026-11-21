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
    fun recognizesChannelByIdWhenDisplayNameIsDifferent() {
        val input = EpgData(
            channels = listOf(EpgChannel("rai2.it", "Nome non riconosciuto")),
            programs = listOf(EpgProgram("rai2.it", "Programma", null, null, 1L, 2L))
        )

        val filtered = FreeToAirFilter.apply(input)

        assertEquals(listOf("rai2.it"), filtered.channels.map { it.id })
        assertEquals(1, filtered.programs.size)
    }
}
