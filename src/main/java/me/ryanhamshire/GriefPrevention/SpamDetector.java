package me.ryanhamshire.GriefPrevention;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

class SpamDetector
{

    //last chat message shown and its timestamp, regardless of who sent it
    private String lastChatMessage = "";

    private long lastChatMessageTimestamp = 0;

    //number of identical chat messages in a row
    private int duplicateMessageCount = 0;

    //data for individual chatters
    ConcurrentHashMap<UUID, ChatterData> dataStore = new ConcurrentHashMap<>();

    private ChatterData getChatterData(UUID chatterID)
    {
        ChatterData data = this.dataStore.get(chatterID);
        if (data == null)
        {
            data = new ChatterData();
            this.dataStore.put(chatterID, data);
        }

        return data;
    }

    SpamAnalysisResult AnalyzeMessage(UUID chatterID, String message, long timestamp)
    {
        SpamAnalysisResult result = new SpamAnalysisResult();
        result.finalMessage = message;

        //remedy any CAPS SPAM, exception for very short messages which could be emoticons like =D or XD
        if (message.length() > 4 && this.stringsAreSimilar(message.toUpperCase(), message))
        {
            message = message.toLowerCase();
            result.finalMessage = message;
        }

        boolean spam = false;
        ChatterData chatterData = this.getChatterData(chatterID);

        //mute if total volume of text from this player is too high
        if (message.length() > 50 && chatterData.getTotalRecentLength(timestamp) > 200)
        {
            spam = true;
            result.muteReason = "too much chat sent in 10 seconds";
            chatterData.spamLevel++;
        }

        //always mute an exact match to the last chat message
        if (result.finalMessage.equals(this.lastChatMessage) && timestamp - this.lastChatMessageTimestamp < 2000)
        {
            chatterData.spamLevel += ++this.duplicateMessageCount;
            spam = true;
            result.muteReason = "repeat message";
        }
        else
        {
            this.lastChatMessage = message;
            this.lastChatMessageTimestamp = timestamp;
            this.duplicateMessageCount = 0;
        }

        //check message content and timing
        long millisecondsSinceLastMessage = timestamp - chatterData.lastMessageTimestamp;

        //if the message came too close to the last one
        if (millisecondsSinceLastMessage < 1500)
        {
            //increment the spam counter
            chatterData.spamLevel++;
            spam = true;
        }

        //if it's exactly the same as the last message from the same player and within 30 seconds
        if (result.muteReason == null && millisecondsSinceLastMessage < 30000
                && result.finalMessage.equalsIgnoreCase(chatterData.lastMessage))
        {
            chatterData.spamLevel++;
            spam = true;
            result.muteReason = "repeat message";
        }

        //if it's very similar to the last message from the same player and within 10 seconds of that message
        if (result.muteReason == null && millisecondsSinceLastMessage < 10000
                && this.stringsAreSimilar(message.toLowerCase(), chatterData.lastMessage.toLowerCase()))
        {
            chatterData.spamLevel++;
            spam = true;
            if (chatterData.spamLevel > 2)
            {
                result.muteReason = "similar message";
            }
        }

        //if the message was mostly non-alpha-numerics or doesn't include much whitespace, consider it a spam (probably ansi art or random text gibberish)
        if (result.muteReason == null && message.length() > 5)
        {
            int symbolsCount = 0;
            int whitespaceCount = 0;
            for (int i = 0; i < message.length(); i++)
            {
                char character = message.charAt(i);
                if (!(Character.isLetterOrDigit(character)))
                {
                    symbolsCount++;
                }

                if (Character.isWhitespace(character))
                {
                    whitespaceCount++;
                }
            }

            if (symbolsCount > message.length() / 2
                    || (message.length() > 15 && whitespaceCount < message.length() / 10))
            {
                spam = true;
                if (chatterData.spamLevel > 0) result.muteReason = "gibberish";
                chatterData.spamLevel++;
            }
        }

        //very short messages close together are spam
        if (result.muteReason == null && message.length() < 5 && millisecondsSinceLastMessage < 3000)
        {
            spam = true;
            chatterData.spamLevel++;
        }

        //if the message was determined to be a spam, consider taking action
        if (spam)
        {
            //anything above level 8 for a player which has received a warning...  kick or if enabled, ban
            if (chatterData.spamLevel > 8 && chatterData.spamWarned)
            {
                result.shouldBanChatter = true;
            }
            else if (chatterData.spamLevel >= 4)
            {
                if (!chatterData.spamWarned)
                {
                    chatterData.spamWarned = true;
                    result.shouldWarnChatter = true;
                }

                if (result.muteReason == null)
                {
                    result.muteReason = "too-frequent text";
                }
            }
        }

        //otherwise if not a spam, reduce the spam level for this player
        else
        {
            chatterData.spamLevel = 0;
            chatterData.spamWarned = false;
        }

        chatterData.AddMessage(message, timestamp);

        return result;
    }

    //if two strings are 75% identical, they're too close to follow each other in the chat
    private boolean stringsAreSimilar(String message, String lastMessage)
    {
        //ignore differences in only punctuation and whitespace
        message = message.replaceAll("[^\\p{Alpha}]", "");
        lastMessage = lastMessage.replaceAll("[^\\p{Alpha}]", "");

        //determine which is shorter
        String shorterString, longerString;
        if (lastMessage.length() < message.length())
        {
            shorterString = lastMessage;
            longerString = message;
        }
        else
        {
            shorterString = message;
            longerString = lastMessage;
        }

        if (shorterString.length() <= 5) return shorterString.equals(longerString);

        //set similarity tolerance
        int maxIdenticalCharacters = longerString.length() - longerString.length() / 4;

        //trivial check on length
        if (shorterString.length() < maxIdenticalCharacters) return false;

        //compare forward
        int identicalCount = 0;
        int i;
        for (i = 0; i < shorterString.length(); i++)
        {
            if (shorterString.charAt(i) == longerString.charAt(i)) identicalCount++;
            if (identicalCount > maxIdenticalCharacters) return true;
        }

        //compare backward
        int j;
        for (j = 0; j < shorterString.length() - i; j++)
        {
            if (shorterString.charAt(shorterString.length() - j - 1) == longerString
                    .charAt(longerString.length() - j - 1))
                identicalCount++;
            if (identicalCount > maxIdenticalCharacters) return true;
        }

        return false;
    }
}

class SpamAnalysisResult
{

    String finalMessage;

    boolean shouldWarnChatter = false;

    boolean shouldBanChatter = false;

    String muteReason;
}

class ChatterData
{

    public String lastMessage = ""; //the player's last chat message, or slash command complete with parameters

    public long lastMessageTimestamp; //last time the player sent a chat message or used a monitored slash command

    public int spamLevel = 0; //number of consecutive "spams"

    public boolean spamWarned = false; //whether the player has received a warning recently

    //all recent message lengths and their total
    private final ConcurrentLinkedQueue<LengthTimestampPair> recentMessageLengths = new ConcurrentLinkedQueue<>();

    private int recentTotalLength = 0;

    public void AddMessage(String message, long timestamp)
    {
        int length = message.length();
        this.recentMessageLengths.add(new LengthTimestampPair(length, timestamp));
        this.recentTotalLength += length;

        this.lastMessage = message;
        this.lastMessageTimestamp = timestamp;
    }

    public int getTotalRecentLength(long timestamp)
    {
        LengthTimestampPair oldestPair = this.recentMessageLengths.peek();
        while (oldestPair != null && timestamp - oldestPair.timestamp > 10000)
        {
            this.recentMessageLengths.poll();
            this.recentTotalLength -= oldestPair.length;
            oldestPair = this.recentMessageLengths.peek();
        }

        return this.recentTotalLength;
    }
}

class LengthTimestampPair
{

    public long timestamp;

    public int length;

    public LengthTimestampPair(int length, long timestamp)
    {
        this.length = length;
        this.timestamp = timestamp;
    }
}
