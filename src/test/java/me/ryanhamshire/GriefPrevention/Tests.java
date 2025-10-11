package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class Tests
{

    @Test
    public void testTrivial()
    {
        assertTrue(true);
    }

    @Test
    public void testWordFinderBeginningMiddleEnd()
    {
        WordFinder finder = new WordFinder(Arrays.asList("alpha", "beta", "gamma"));
        assertTrue(finder.hasMatch("alpha"));
        assertTrue(finder.hasMatch("alpha etc"));
        assertTrue(finder.hasMatch("etc alpha etc"));
        assertTrue(finder.hasMatch("etc alpha"));

        assertTrue(finder.hasMatch("beta"));
        assertTrue(finder.hasMatch("beta etc"));
        assertTrue(finder.hasMatch("etc beta etc"));
        assertTrue(finder.hasMatch("etc beta"));

        assertTrue(finder.hasMatch("gamma"));
        assertTrue(finder.hasMatch("gamma etc"));
        assertTrue(finder.hasMatch("etc gamma etc"));
        assertTrue(finder.hasMatch("etc gamma"));
    }

    @Test
    public void testWordFinderCasing()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("aLPhA"));
        assertTrue(finder.hasMatch("alpha"));
        assertTrue(finder.hasMatch("aLPhA"));
        assertTrue(finder.hasMatch("AlpHa"));
        assertTrue(finder.hasMatch("ALPHA"));
    }

    @Test
    public void testWordFinderPunctuation()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("alpha"));
        assertTrue(finder.hasMatch("What do you think,alpha?"));
    }

    @Test
    public void testWordFinderNoMatch()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("alpha"));
        assertFalse(finder.hasMatch("Unit testing is smart."));
    }

    @Test
    public void testWordFinderEmptyList()
    {
        WordFinder finder = new WordFinder(Collections.emptyList());
        assertFalse(finder.hasMatch("alpha"));
        finder = new WordFinder(Collections.singletonList(""));
        assertFalse(finder.hasMatch("beta"));
    }

    @Test
    public void testWordFinderPunctuationOnly()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("alpha"));
        assertFalse(finder.hasMatch("!"));
        assertFalse(finder.hasMatch("?"));
    }

    @Test
    public void testWordFinderStartingPunctuation()
    {
        WordFinder finder = new WordFinder(Collections.singletonList("alpha"));
        assertFalse(finder.hasMatch("!asas dfasdf"));
        assertFalse(finder.hasMatch("?asdfa sdfas df"));
    }

    private final UUID player1 = UUID.fromString("f13c5a98-3777-4659-a111-5617adb7d7fb");

    private final UUID player2 = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @Test
    public void testSpamDetectorBasicChatOK()
    {
        SpamDetector detector = new SpamDetector();
        String message = "Hi, everybody! :)";
        SpamAnalysisResult result = detector.AnalyzeMessage(player1, message, 1000);
        assertNull(result.muteReason);
        assertFalse(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
        assertEquals(result.finalMessage, message);
    }

    @Test
    public void testSpamDetectorCoordinatesOK()
    {
        SpamDetector detector = new SpamDetector();
        assertNull(detector.AnalyzeMessage(player1, "1029,2945", 0).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "x1029 z2945", 100000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "x=1029; y=60; z=2945", 200000).muteReason);
    }

    @Test
    public void testSpamDetectorNumbersOK()
    {
        SpamDetector detector = new SpamDetector();
        assertNull(detector.AnalyzeMessage(player1, "25", 0).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "12,234.89", 100000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "20078", 200000).muteReason);
    }

    @Test
    public void testSpamDetectorRepetitionExact()
    {
        SpamDetector detector = new SpamDetector();
        String message = "Hi, everybody!   :)";
        assertNull(detector.AnalyzeMessage(player1, message, 1000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player1, message, 28000).muteReason);
    }

    @Test
    public void testSpamDetectorRepetitionExactOK()
    {
        SpamDetector detector = new SpamDetector();
        String message = "Hi, everybody!   :)";
        assertNull(detector.AnalyzeMessage(player1, message, 1000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, message, 35000).muteReason);
    }

    @Test
    public void testSpamDetectorPadding()
    {
        SpamDetector detector = new SpamDetector();
        assertNull(
                detector.AnalyzeMessage(
                        player1,
                        "Hacking is really fun guys!! :) 123123123456.12398127498762935",
                        1000).muteReason);
        assertNotNull(
                detector.AnalyzeMessage(
                        player1,
                        "Hacking is really fun guys!! :) 112321523456.1239345498762935",
                        1000).muteReason);
    }

    @Test
    public void testSpamDetectorRepetition()
    {
        SpamDetector detector = new SpamDetector();
        assertNull(detector.AnalyzeMessage(player1, "Hi, everybody!   :)", 1000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "Hi, everybody!    :)", 5000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player1, "Hi, everybody!   :)", 9000).muteReason);
    }

    @Test
    public void testSpamDetectorTeamRepetition()
    {
        SpamDetector detector = new SpamDetector();
        assertNull(detector.AnalyzeMessage(player1, "Hi, everybody! :)", 1000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player2, "Hi, everybody! :)", 2500).muteReason);
    }

    @Test
    public void testSpamDetectorTeamRepetitionOK()
    {
        SpamDetector detector = new SpamDetector();
        assertNull(detector.AnalyzeMessage(player1, "hi", 1000).muteReason);
        assertNull(detector.AnalyzeMessage(player2, "hi", 3000).muteReason);
    }

    @Test
    public void testSpamDetectorGibberish()
    {
        SpamDetector detector = new SpamDetector();
        assertNotNull(
                detector.AnalyzeMessage(
                        player1,
                        "poiufpoiuasdfpoiuasdfuaufpoiasfopiuasdfpoiuasdufsdf",
                        1000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player2, "&^%(& (&^%(%    (*%#@^ #$&(_||", 3000).muteReason);
    }

    @Test
    public void testSpamDetectorRepetitionOK()
    {
        SpamDetector detector = new SpamDetector();
        assertNull(detector.AnalyzeMessage(player1, "Hi, everybody!   :)", 1000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "Hi, everybody!    :)", 12000).muteReason);
    }

    @Test
    public void testSpamDetectorTooFast()
    {
        SpamDetector detector = new SpamDetector();
        assertNull(detector.AnalyzeMessage(player1, "Hi, everybody! :)", 1000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "How's it going? :)", 2000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "Oh how I've missed you all! :)", 3000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player1, "Why is nobody responding to me??!", 4000).muteReason);
    }

    @Test
    public void testSpamDetectorTooMuchVolume()
    {
        SpamDetector detector = new SpamDetector();
        assertNull(
                detector.AnalyzeMessage(
                        player1,
                        "Once upon a time there was this guy who wanted to be a hacker.  So he started logging into Minecraft servers and threatening to DDOS them.",
                        1000).muteReason);
        assertNull(
                detector.AnalyzeMessage(
                        player1,
                        "Everybody knew that he couldn't be a real hacker, because no real hacker would consider hacking Minecraft to be worth their time, but he didn't understand that even after it was explained to him.",
                        3000).muteReason);

        //start of mute
        assertNotNull(
                detector.AnalyzeMessage(
                        player1,
                        "After I put him in jail and he wasted half an hour of his time trying to solve the (unsolvable) jail 'puzzle', he offered his services to me in exchange for being let out of jail.",
                        10000).muteReason);

        //forgiven after taking a break
        assertNull(
                detector.AnalyzeMessage(
                        player1,
                        "He promised to DDOS any of my 'rival servers'.  So I offered him an opportunity to prove he could do what he said, and I gave him his own IP address from our server logs.  Then he disappeared for a while.",
                        16000).muteReason);
        assertNull(
                detector.AnalyzeMessage(
                        player1,
                        "When he finally came back, I /SoftMuted him and left him in the jail.",
                        28000).muteReason);
    }

    @Test
    public void testSpamDetectorCaps()
    {
        SpamDetector detector = new SpamDetector();
        String message = "OMG I LUFF U KRISTINAAAAAA!";
        SpamAnalysisResult result = detector.AnalyzeMessage(player1, message, 1000);
        assertEquals(result.finalMessage, message.toLowerCase());
        assertNull(result.muteReason);
    }

    @Test
    public void testSpamDetectorCapsOK()
    {
        SpamDetector detector = new SpamDetector();
        String message = "=D";
        SpamAnalysisResult result = detector.AnalyzeMessage(player1, message, 1000);
        assertEquals(result.finalMessage, message);
        assertNull(result.muteReason);
    }

    @Test
    public void testSpamDetectorWarnAndBan()
    {
        SpamDetector detector = new SpamDetector();

        //allowable noise
        assertNull(detector.AnalyzeMessage(player1, "Hi, everybody! :)", 1000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "How's it going? :)", 2000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "Oh how I've missed you all! :)", 3000).muteReason);

        //begin mute and warning
        SpamAnalysisResult result = detector.AnalyzeMessage(player1, "Why is nobody responding to me??!", 4000);
        assertNotNull(result.muteReason);
        assertTrue(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
        assertNotNull(detector.AnalyzeMessage(player1, "Hi, everybody! :)", 5000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player1, "Oh how I've missed you all! :)", 6000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player1, "Hi, everybody! :)", 7000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player1, "How's it going? :)", 8000).muteReason);

        //ban
        result = detector.AnalyzeMessage(player1, "Why is nobody responding to me??!", 9000);
        assertTrue(result.shouldBanChatter);
    }

    @Test
    public void testSpamDetectorForgiveness()
    {
        SpamDetector detector = new SpamDetector();

        //allowable noise
        assertNull(detector.AnalyzeMessage(player1, "Hi, everybody! :)", 1000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "How's it going? :)", 2000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "Oh how I've missed you all! :)", 3000).muteReason);

        //start of mutes, and a warning
        SpamAnalysisResult result = detector.AnalyzeMessage(player1, "Why is nobody responding to me??!", 4000);
        assertNotNull(result.muteReason);
        assertTrue(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
        assertNotNull(detector.AnalyzeMessage(player1, "Hi, everybody! :)", 5000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player1, "Oh how I've missed you all! :)", 6000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player1, "Hi, everybody! :)", 7000).muteReason);
        assertNotNull(detector.AnalyzeMessage(player1, "How's it going? :)", 8000).muteReason);

        //long delay before next message, not muted anymore
        result = detector.AnalyzeMessage(player1, "Why is nobody responding to me??!", 20000);
        assertFalse(result.shouldBanChatter);
        assertNull(detector.AnalyzeMessage(player1, "Hi, everybody! :)", 21000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "How's it going? :)", 22000).muteReason);
        assertNull(detector.AnalyzeMessage(player1, "Oh how I've missed you all! :)", 23000).muteReason);

        //mutes start again, and warning appears again
        result = detector.AnalyzeMessage(player1, "Why is nobody responding to me??!", 24000);
        assertNotNull(result.muteReason);
        assertTrue(result.shouldWarnChatter);
        assertFalse(result.shouldBanChatter);
    }
}
