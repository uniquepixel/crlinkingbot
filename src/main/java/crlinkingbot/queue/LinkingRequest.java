package crlinkingbot.queue;

import org.json.JSONObject;

import java.util.UUID;

/**
 * Represents a linking request in the queue.
 * Note: Image URLs are no longer stored - they are fetched dynamically from Discord
 * using the messageId and channelId when needed.
 */
public class LinkingRequest {
    private final String id;
    private final String messageId;
    private final String channelId;
    private final String guildId;
    private final String userId;
    private final String userTag;
    private final long timestamp;
    private int retryCount;

    /**
     * Constructor for a new linking request
     */
    public LinkingRequest(String messageId, String channelId, String guildId, String userId, 
                         String userTag) {
        this.id = UUID.randomUUID().toString();
        this.messageId = messageId;
        this.channelId = channelId;
        this.guildId = guildId;
        this.userId = userId;
        this.userTag = userTag;
        this.timestamp = System.currentTimeMillis();
        this.retryCount = 0;
    }

    /**
     * Constructor for loading from JSON
     */
    private LinkingRequest(String id, String messageId, String channelId, String guildId,
                          String userId, String userTag, 
                          long timestamp, int retryCount) {
        this.id = id;
        this.messageId = messageId;
        this.channelId = channelId;
        this.guildId = guildId;
        this.userId = userId;
        this.userTag = userTag;
        this.timestamp = timestamp;
        this.retryCount = retryCount;
    }

    /**
     * Serialize to JSON for persistence
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("messageId", messageId);
        json.put("channelId", channelId);
        json.put("guildId", guildId);
        json.put("userId", userId);
        json.put("userTag", userTag);
        json.put("timestamp", timestamp);
        json.put("retryCount", retryCount);
        return json;
    }

    /**
     * Deserialize from JSON
     */
    public static LinkingRequest fromJSON(JSONObject json) {
        // Handle backward compatibility - ignore imageUrls if present in old JSON
        return new LinkingRequest(
            json.getString("id"),
            json.getString("messageId"),
            json.getString("channelId"),
            json.getString("guildId"),
            json.getString("userId"),
            json.getString("userTag"),
            json.getLong("timestamp"),
            json.getInt("retryCount")
        );
    }

    /**
     * Increment retry counter
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserTag() {
        return userTag;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
