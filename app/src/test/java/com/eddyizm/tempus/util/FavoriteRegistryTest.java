package com.eddyizm.tempus.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.eddyizm.tempus.util.FavoriteRegistry.Kind;

import org.junit.Before;
import org.junit.Test;

public class FavoriteRegistryTest {

    @Before
    public void reset() {
        FavoriteRegistry.clear();
    }

    @Test
    public void untouchedItemFollowsTheServer() {
        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", true));
        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void starringWinsOverAServerThatDoesNotSayStarred() {
        FavoriteRegistry.set(Kind.ALBUM, "1", true);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void unstarringWinsOverAServerStillSayingStarred() {
        FavoriteRegistry.set(Kind.ALBUM, "1", false);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", true));
    }

    @Test
    public void onlyTheItemThatWasTouchedIsAffected() {
        FavoriteRegistry.set(Kind.ALBUM, "1", true);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "2", false));
    }

    @Test
    public void theSameIdUnderADifferentKindIsADifferentItem() {
        FavoriteRegistry.set(Kind.SONG, "1234", true);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1234", false));
        assertFalse(FavoriteRegistry.resolve(Kind.ARTIST, "1234", false));
        assertTrue(FavoriteRegistry.resolve(Kind.SONG, "1234", false));
    }

    @Test
    public void aNullIdFallsBackToTheServer() {
        FavoriteRegistry.set(Kind.SONG, null, true);

        assertFalse(FavoriteRegistry.resolve(Kind.SONG, null, false));
        assertTrue(FavoriteRegistry.resolve(Kind.SONG, null, true));
    }

    @Test
    public void aRefusedChangeLeavesTheEarlierDecisionStanding() {
        FavoriteRegistry.set(Kind.ALBUM, "1", false);
        FavoriteRegistry.Record record = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(record);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", true));
    }

    @Test
    public void aRefusedFirstChangeFallsBackToTheServer() {
        FavoriteRegistry.Record record = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(record);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", true));
    }

    @Test
    public void aLateRefusalLeavesALaterDecisionAlone() {
        FavoriteRegistry.Record record = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.set(Kind.ALBUM, "1", false);
        FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(record);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void twoRefusalsFallBackToTheServerInEitherOrder() {
        FavoriteRegistry.Record first = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.Record second = FavoriteRegistry.set(Kind.ALBUM, "1", false);
        FavoriteRegistry.strike(first);
        FavoriteRegistry.strike(second);
        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));

        FavoriteRegistry.clear();
        first = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        second = FavoriteRegistry.set(Kind.ALBUM, "1", false);
        FavoriteRegistry.strike(second);
        FavoriteRegistry.strike(first);
        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aRefusedRetryVoidsTheQueuedDecisionToo() {
        FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.Record retry = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(retry);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aRefusedRetryStopsAtAnAcceptedDecisionBeneathIt() {
        FavoriteRegistry.accept(FavoriteRegistry.set(Kind.ALBUM, "1", true));
        FavoriteRegistry.Record retry = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(retry);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aRefusedRetryStopsAtTheOtherDecisionBeneathIt() {
        FavoriteRegistry.set(Kind.ALBUM, "1", false);
        FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.Record retry = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(retry);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", true));
    }

    @Test
    public void aRefusalDoesNotReachAnAcceptedDecisionAboveIt() {
        FavoriteRegistry.Record first = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.accept(FavoriteRegistry.set(Kind.ALBUM, "1", true));
        FavoriteRegistry.strike(first);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void anAcceptedDecisionCannotBeStruck() {
        FavoriteRegistry.Record record = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.accept(record);
        FavoriteRegistry.strike(record);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aLateAcceptanceRevivesAStruckRecord() {
        FavoriteRegistry.Record record = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(record);
        FavoriteRegistry.accept(record);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aRecordBuriedByACascadeRevivesWhenItsOwnRequestIsAccepted() {
        FavoriteRegistry.Record first = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.Record second = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(second);
        FavoriteRegistry.accept(first);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aRefusalSkipsAStruckRecordOfTheOtherDecision() {
        FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.Record unstar = FavoriteRegistry.set(Kind.ALBUM, "1", false);
        FavoriteRegistry.strike(unstar);
        FavoriteRegistry.Record star = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(star);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aRecordIsCurrentUntilANewerDecisionReplacesIt() {
        FavoriteRegistry.Record star = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        assertTrue(FavoriteRegistry.isCurrent(star));

        FavoriteRegistry.set(Kind.ALBUM, "1", false);
        assertFalse(FavoriteRegistry.isCurrent(star));
    }

    @Test
    public void aRecordIsStillCurrentBeneathAStruckNewerOne() {
        FavoriteRegistry.Record star = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(FavoriteRegistry.set(Kind.ALBUM, "1", false));

        assertTrue(FavoriteRegistry.isCurrent(star));
    }

    @Test
    public void nothingIsCurrentAfterClearing() {
        FavoriteRegistry.Record star = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.clear();

        assertFalse(FavoriteRegistry.isCurrent(star));
    }

    @Test
    public void aWithdrawnRecordIsNotCurrent() {
        FavoriteRegistry.Record star = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.withdraw(star);

        assertFalse(FavoriteRegistry.isCurrent(star));
    }

    @Test
    public void aRefusalPassesThroughAStruckRecordOfTheSameDecision() {
        FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.withdraw(FavoriteRegistry.set(Kind.ALBUM, "1", true));
        FavoriteRegistry.strike(FavoriteRegistry.set(Kind.ALBUM, "1", true));

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aQueuedDecisionReplacesAPendingOppositeOne() {
        FavoriteRegistry.set(Kind.ALBUM, "1", false);
        FavoriteRegistry.supersede(Kind.ALBUM, "1", true);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", true));
    }

    @Test
    public void aQueuedDecisionLeavesAnAcceptedOppositeOneAlone() {
        FavoriteRegistry.accept(FavoriteRegistry.set(Kind.ALBUM, "1", false));
        FavoriteRegistry.supersede(Kind.ALBUM, "1", true);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", true));
    }

    @Test
    public void aSupersededDecisionRevivesWhenTheServerAcceptsIt() {
        FavoriteRegistry.Record unstar = FavoriteRegistry.set(Kind.ALBUM, "1", false);
        FavoriteRegistry.supersede(Kind.ALBUM, "1", true);
        FavoriteRegistry.accept(unstar);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", true));
    }

    @Test
    public void anAcceptedDecisionCannotBeWithdrawn() {
        FavoriteRegistry.Record star = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.accept(star);
        FavoriteRegistry.withdraw(star);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void withdrawingTouchesOnlyItsOwnRecord() {
        FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.Record retry = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.withdraw(retry);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aMissingRecordIsIgnored() {
        FavoriteRegistry.withdraw(null);
        assertFalse(FavoriteRegistry.isCurrent(null));
        FavoriteRegistry.strike(null);
        FavoriteRegistry.accept(null);

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void aStrikeAfterClearingLeavesTheNewSessionAlone() {
        FavoriteRegistry.Record stale = FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.clear();
        FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.strike(stale);

        assertTrue(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }

    @Test
    public void clearingForgetsEverything() {
        FavoriteRegistry.set(Kind.ALBUM, "1", true);
        FavoriteRegistry.clear();

        assertFalse(FavoriteRegistry.resolve(Kind.ALBUM, "1", false));
    }
}
