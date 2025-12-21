package crlinkingbot.queue;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a linking request in the queue.
 */
public class LinkingRequest {
    private final String id;
    private final String messageId;
    private final String channelId;
    private final String guildId;
    private final String userId;
    private final String userTag;
    private final List<String> imageUrls;
    private final long timestamp;
    private int retryCount;

    /**
     * Constructor for a new linking request
     */
    public LinkingRequest(String messageId, String channelId, String guildId, String userId, 
                         String userTag, List<String> imageUrls) {
        this.id = UUID.randomUUID().toString();
        this.messageId = messageId;
        this.channelId = channelId;
        this.guildId = guildId;
        this.userId = userId;
        this.userTag = userTag;
        this.imageUrls = new ArrayList<>(imageUrls);
        this.timestamp = System.currentTimeMillis();
        this.retryCount = 0;
    }

    /**
     * Constructor for loading from JSON
     */
    private LinkingRequest(String id, String messageId, String channelId, String guildId,
                          String userId, String userTag, List<String> imageUrls, 
                          long timestamp, int retryCount) {
        this.id = id;
        this.messageId = messageId;
        this.channelId = channelId;
        this.guildId = guildId;
        this.userId = userId;
        this.userTag = userTag;
        this.imageUrls = new ArrayList<>(imageUrls);
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
        json.put("imageUrls", new JSONArray(imageUrls));
        json.put("timestamp", timestamp);
        json.put("retryCount", retryCount);
        return json;
    }

    /**
     * Deserialize from JSON
     */
    public static LinkingRequest fromJSON(JSONObject json) {
        List<String> urls = new ArrayList<>();
        JSONArray urlArray = json.getJSONArray("imageUrls");
        for (int i = 0; i < urlArray.length(); i++) {
            urls.add(urlArray.getString(i));
        }

        return new LinkingRequest(
            json.getString("id"),
            json.getString("messageId"),
            json.getString("channelId"),
            json.getString("guildId"),
            json.getString("userId"),
            json.getString("userTag"),
            urls,
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

    public List<String> getImageUrls() {
        return new ArrayList<>(imageUrls);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
