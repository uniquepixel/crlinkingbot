package crlinkingbot.queue;

import crlinkingbot.services.GeminiVisionService;
import crlinkingbot.services.LostCRManagerClient;
import crlinkingbot.util.MessageUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Processes the queue with scheduled checking every 5 minutes.
 */
public class QueueProcessor {
    private static final int MAX_RETRIES = 3;
    private static final int CHECK_INTERVAL_MINUTES = 5;
    private static final int DELAY_BETWEEN_REQUESTS_MS = 2000; // 2 seconds

    private final RequestQueue requestQueue;
    private final JDA jda;
    private final PCActivityChecker activityChecker;
    private final ScheduledExecutorService scheduler;

    /**
     * Constructor accepts RequestQueue and JDA instance
     */
    public QueueProcessor(RequestQueue requestQueue, JDA jda) {
        this.requestQueue = requestQueue;
        this.jda = jda;
        this.activityChecker = new PCActivityChecker();
        this.scheduler = Executors.newScheduledThreadPool(1);
        System.out.println("QueueProcessor initialized");
    }

    /**
     * Start scheduled processing every 5 minutes
     */
    public void start() {
        System.out.println("Starting queue processor with " + CHECK_INTERVAL_MINUTES + " minute check interval");
        
        // Process immediately on startup, then every 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processQueue();
            } catch (Exception e) {
                System.out.println("Error in queue processor: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Process the queue if PC is active
     */
    private void processQueue() {
        System.out.println("Queue processor checking PC activity...");
        
        if (!activityChecker.isPCActive()) {
            System.out.println("PC is not active, skipping queue processing");
            if (!requestQueue.isEmpty()) {
                System.out.println("Queue has " + requestQueue.size() + " pending requests");
            }
            return;
        }

        System.out.println("PC is active, processing queue...");
        
        int processed = 0;
        while (!requestQueue.isEmpty()) {
            LinkingRequest request = requestQueue.dequeue();
            if (request != null) {
                processed++;
                System.out.println("Processing request " + processed + "/" + requestQueue.size() + " from user " + request.getUserTag());
                
                boolean success = processRequest(request);
                
                if (!success && request.getRetryCount() < MAX_RETRIES) {
                    // Re-queue for retry
                    request.incrementRetryCount();
                    requestQueue.enqueue(request);
                    System.out.println("Request failed, re-queued for retry (" + request.getRetryCount() + "/" + MAX_RETRIES + ")");
                } else if (!success) {
                    System.out.println("Request failed after max retries, not re-queuing");
                }
                
                // Small delay between processing requests
                if (!requestQueue.isEmpty()) {
                    try {
                        Thread.sleep(DELAY_BETWEEN_REQUESTS_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        if (processed > 0) {
            System.out.println("Finished processing " + processed + " requests");
        } else {
            System.out.println("No requests to process");
        }
    }

    /**
     * Process a single request
     * 
     * @return true if successful, false if failed
     */
    private boolean processRequest(LinkingRequest request) {
        try {
            // Retrieve message from Discord
            MessageChannelUnion channel = jda.getChannelById(MessageChannelUnion.class, request.getChannelId());
            if (channel == null) {
                System.out.println("Channel not found: " + request.getChannelId());
                return false;
            }

            Message message = channel.retrieveMessageById(request.getMessageId()).complete();
            if (message == null) {
                System.out.println("Message not found: " + request.getMessageId());
                return false;
            }

            // Add processing reaction
            message.addReaction(Emoji.fromUnicode("⏳")).queue();

            // Extract player tag
            System.out.println("Extracting player tag from images for user " + request.getUserTag());
            String playerTag = GeminiVisionService.extractPlayerTag(request.getImageUrls());

            if (playerTag == null) {
                System.out.println("No player tag found in images for user " + request.getUserTag());
                message.removeReaction(Emoji.fromUnicode("⏳")).queue();
                message.addReaction(Emoji.fromUnicode("❌")).queue();

                String errorMessage = "Ich konnte keinen Clash Royale Spieler-Tag in den Screenshots finden.\n\n"
                        + "Bitte stelle sicher, dass:\n"
                        + "• Der Screenshot ein Clash Royale Profil zeigt\n"
                        + "• Der Spieler-Tag (z.B. #ABC123) gut lesbar ist\n"
                        + "• Das Bild nicht verschwommen oder zu klein ist";

                MessageUtil.sendError(channel, "Spieler-Tag nicht gefunden", errorMessage);
                return false;
            }

            System.out.println("Found player tag: " + playerTag + " for user " + request.getUserTag());

            // Link player via API
            JSONObject response = LostCRManagerClient.linkPlayer(playerTag, request.getUserId());

            if (response.getBoolean("success")) {
                // Success
                System.out.println("Successfully linked player " + playerTag + " to user " + request.getUserTag());
                message.removeReaction(Emoji.fromUnicode("⏳")).queue();
                message.addReaction(Emoji.fromUnicode("✅")).queue();

                String successMessage = String.format("Account wurde erfolgreich verknüpft!\n\n"
                        + "**Spieler-Tag:** `%s`\n"
                        + "**Discord User:** <@%s>", playerTag, request.getUserId());

                MessageUtil.sendSuccess(channel, "Account verknüpft", successMessage);
                return true;
            } else {
                // Error
                System.out.println("Failed to link player: " + response.toString());
                message.removeReaction(Emoji.fromUnicode("⏳")).queue();
                message.addReaction(Emoji.fromUnicode("❌")).queue();

                String errorMessage = "Es gab einen Fehler beim Verknüpfen des Accounts.";

                // Try to extract error message from response
                if (response.has("data") && response.getJSONObject("data").has("message")) {
                    String apiMessage = response.getJSONObject("data").getString("message");
                    errorMessage += "\n\n**Fehler:** " + apiMessage;
                } else if (response.has("message")) {
                    String apiMessage = response.getString("message");
                    errorMessage += "\n\n**Fehler:** " + apiMessage;
                } else if (response.has("error")) {
                    String apiError = response.getString("error");
                    errorMessage += "\n\n**Fehler:** " + apiError;
                }

                MessageUtil.sendError(channel, "Verknüpfung fehlgeschlagen", errorMessage);
                return false;
            }
        } catch (Exception e) {
            System.out.println("Error processing request: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Cleanup scheduler
     */
    public void shutdown() {
        System.out.println("Shutting down queue processor");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
