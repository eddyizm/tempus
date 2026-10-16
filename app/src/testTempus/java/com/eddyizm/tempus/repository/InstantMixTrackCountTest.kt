package com.eddyizm.tempus.repository

import com.eddyizm.tempus.util.ConstantsAA
import org.junit.Assert.assertEquals
import org.junit.Test

class InstantMixTrackCountTest {

    @Test fun singleAlbumArtistDoesNotQualify() {
        assertEquals(0, AutomotiveRepository.instantMixTrackCount(1, 500))
    }

    @Test fun tooFewTracksDoesNotQualify() {
        assertEquals(0, AutomotiveRepository.instantMixTrackCount(3, ConstantsAA.MIN_TRACKS_SMALL_MIX - 1))
    }

    @Test fun tiersFollowTotalTracks() {
        assertEquals(ConstantsAA.NUMBER_OF_TRACKS_IN_SMALL_MIX,
            AutomotiveRepository.instantMixTrackCount(2, ConstantsAA.MIN_TRACKS_SMALL_MIX))
        assertEquals(ConstantsAA.NUMBER_OF_TRACKS_IN_MEDIUM_MIX,
            AutomotiveRepository.instantMixTrackCount(2, ConstantsAA.MIN_TRACKS_MEDIUM_MIX))
        assertEquals(ConstantsAA.NUMBER_OF_TRACKS_IN_LARGE_MIX,
            AutomotiveRepository.instantMixTrackCount(2, ConstantsAA.MIN_TRACKS_LARGE_MIX))
    }
}
