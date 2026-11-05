package me.ryanhamshire.GriefPrevention;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpamDetectorContentTest
{
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    static Stream<Arguments> contentBoundaries()
    {
        return Stream.of(
                Arguments.of("abc!!", false),
                Arguments.of("abc!!!", false),
                Arguments.of("ab!!!!", true),
                Arguments.of("abcd!!!", false),
                Arguments.of("abc!!!!", true),
                Arguments.of("abcdefghijklmno", false),
                Arguments.of("abcdefghijklmnop", true),
                Arguments.of("abcdefghi jklmnopqr", false),
                Arguments.of("abcdefghi jklmnopqrs", true),
                Arguments.of("abcdefghi  jklmnopqr", false),
                Arguments.of("ab  !!", true),
                Arguments.of("ab\t\t!!", true),
                Arguments.of("abcdefg\u2003hijklmno", false),
                Arguments.of("abcdefg\u00a0hijklmno", true),
                Arguments.of("abc\ud83d\ude00", false),
                Arguments.of("ab\ud83d\ude00cd", false),
                Arguments.of("a\ud83d\ude00\ud83d\ude00b", true),
                Arguments.of("\u03b1\u03b2\u03b3\u03b4\u03b5\u03b6", false));
    }

    @ParameterizedTest
    @MethodSource("contentBoundaries")
    void preservesContentClassificationAndState(String message, boolean gibberish)
    {
        SpamDetector detector = new SpamDetector();
        ChatterData state = new ChatterData();
        state.spamLevel = 1;
        detector.dataStore.put(PLAYER, state);

        SpamAnalysisResult result = detector.AnalyzeMessage(PLAYER, message, 100000);

        assertEquals(message, result.finalMessage);
        assertEquals(gibberish ? "gibberish" : null, result.muteReason);
        assertFalse(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
        assertEquals(gibberish ? 2 : 0, state.spamLevel);
        assertFalse(state.spamWarned);
        assertEquals(message, state.lastMessage);
        assertEquals(100000, state.lastMessageTimestamp);
        assertEquals(message.length(), state.getTotalRecentLength(100000));
    }

    @Test
    void firstGibberishIncrementsWithoutMuting()
    {
        SpamDetector detector = new SpamDetector();
        SpamAnalysisResult result = detector.AnalyzeMessage(PLAYER, "ab!!!!", 100000);
        assertNull(result.muteReason);
        assertFalse(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
        assertEquals(1, detector.dataStore.get(PLAYER).spamLevel);
    }

    @Test
    void existingRepeatReasonSkipsContentIncrement()
    {
        SpamDetector detector = new SpamDetector();
        detector.AnalyzeMessage(PLAYER, "ab!!!!", 100000);
        SpamAnalysisResult result = detector.AnalyzeMessage(PLAYER, "ab!!!!", 101000);
        assertEquals("repeat message", result.muteReason);
        assertEquals(3, detector.dataStore.get(PLAYER).spamLevel);
        assertFalse(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
        assertEquals(12, detector.dataStore.get(PLAYER).getTotalRecentLength(101000));
    }

    @Test
    void contentUsesNormalizedMessageAndWarnsAtExistingThreshold()
    {
        SpamDetector detector = new SpamDetector();
        ChatterData state = new ChatterData();
        state.spamLevel = 3;
        detector.dataStore.put(PLAYER, state);
        SpamAnalysisResult result = detector.AnalyzeMessage(PLAYER, "AB!!!!", 100000);
        assertEquals("ab!!!!", result.finalMessage);
        assertEquals("ab!!!!", state.lastMessage);
        assertEquals("gibberish", result.muteReason);
        assertEquals(4, state.spamLevel);
        assertTrue(state.spamWarned);
        assertTrue(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
    }

    @Test
    void warnedChatterReachesExistingBanThreshold()
    {
        SpamDetector detector = new SpamDetector();
        ChatterData state = new ChatterData();
        state.spamLevel = 8;
        state.spamWarned = true;
        detector.dataStore.put(PLAYER, state);
        SpamAnalysisResult result = detector.AnalyzeMessage(PLAYER, "ab!!!!", 100000);
        assertEquals("gibberish", result.muteReason);
        assertEquals(9, state.spamLevel);
        assertTrue(state.spamWarned);
        assertFalse(result.shouldWarnChatter);
        assertTrue(result.shouldBanChatter);
    }
}
