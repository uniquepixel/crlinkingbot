package crlinkingbot.listeners;

import crlinkingbot.services.GeminiVisionService;
import crlinkingbot.services.LostCRManagerClient;
import crlinkingbot.util.MessageUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Event listener for processing messages in ticket channels.
 */
public class TicketListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TicketListener.class);
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }
        
        Message message = event.getMessage();
        String channelName = event.getChannel().getName();
        
        // Check if this is a ticket channel
        if (!isTicketChannel(channelName)) {
            return;
        }
        
        // Check if message has image attachments
        List<String> imageUrls = message.getAttachments().stream()
                .filter(attachment -> attachment.isImage())
                .map(attachment -> attachment.getUrl())
                .collect(Collectors.toList());
        
        if (imageUrls.isEmpty()) {
            return;
        }
        
        logger.info("Processing {} images from user {} in channel {}", 
                imageUrls.size(), event.getAuthor().getAsTag(), channelName);
        
        // Add processing reaction
        message.addReaction(Emoji.fromUnicode("⏳")).queue();
        
        // Process images asynchronously
        String userId = event.getAuthor().getId();
        long timestamp = System.currentTimeMillis();
        Thread processingThread = new Thread(() -> processImages(message, imageUrls, userId), 
                "TicketAutoLink-" + userId + "-" + timestamp);
        processingThread.start();
    }
    
    /**
     * Check if a channel is a ticket channel based on its name
     */
    private boolean isTicketChannel(String channelName) {
        if (channelName == null) {
            return false;
        }
        
        String lowerName = channelName.toLowerCase();
        
        // Check for common ticket channel patterns
        if (lowerName.contains("ticket")) {
            return true;
        }
        if (lowerName.contains("bewerbung")) {
            return true;
        }
        if (lowerName.contains("application")) {
            return true;
        }
        if (lowerName.matches("ticket-\\d+")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Process images asynchronously to extract player tag and link player
     */
    private void processImages(Message message, List<String> imageUrls, String userId) {
        try {
            // Extract player tag using Gemini Vision
            logger.info("Extracting player tag from images...");
            String playerTag = GeminiVisionService.extractPlayerTag(imageUrls);
            
            if (playerTag == null) {
                // Tag not found
                logger.warn("No player tag found in images");
                message.removeReaction(Emoji.fromUnicode("⏳")).queue();
                message.addReaction(Emoji.fromUnicode("❌")).queue();
                MessageUtil.sendError(
                        message.getChannel(),
                        "Spieler-Tag nicht gefunden",
                        "Ich konnte keinen Clash Royale Spieler-Tag in den Screenshots finden.\n\n" +
                        "Bitte stelle sicher, dass:\n" +
                        "• Der Screenshot dein Clash Royale Profil zeigt\n" +
                        "• Der Spieler-Tag (z.B. #ABC123) gut lesbar ist\n" +
                        "• Das Bild nicht verschwommen oder zu klein ist"
                );
                return;
            }
            
            logger.info("Found player tag: {}", playerTag);
            
            // Call lostcrmanager API to link player
            logger.info("Linking player {} to user {}", playerTag, userId);
            JSONObject response = LostCRManagerClient.linkPlayer(playerTag, userId);
            
            if (response.getBoolean("success")) {
                // Success
                logger.info("Successfully linked player {} to user {}", playerTag, userId);
                message.removeReaction(Emoji.fromUnicode("⏳")).queue();
                message.addReaction(Emoji.fromUnicode("✅")).queue();
                
                String successMessage = String.format(
                        "Dein Account wurde erfolgreich verknüpft!\n\n" +
                        "**Spieler-Tag:** `%s`\n" +
                        "**Discord User:** <@%s>",
                        playerTag, userId
                );
                
                MessageUtil.sendSuccess(
                        message.getChannel(),
                        "Account verknüpft",
                        successMessage
                );
            } else {
                // Error
                logger.error("Failed to link player: {}", response.toString());
                message.removeReaction(Emoji.fromUnicode("⏳")).queue();
                message.addReaction(Emoji.fromUnicode("❌")).queue();
                
                String errorMessage = "Es gab einen Fehler beim Verknüpfen deines Accounts.";
                
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
                
                MessageUtil.sendError(
                        message.getChannel(),
                        "Verknüpfung fehlgeschlagen",
                        errorMessage
                );
            }
        } catch (Exception e) {
            logger.error("Error processing images", e);
            message.removeReaction(Emoji.fromUnicode("⏳")).queue(
                    success -> message.addReaction(Emoji.fromUnicode("❌")).queue(),
                    error -> logger.error("Failed to update reactions", error)
            );
            MessageUtil.sendError(
                    message.getChannel(),
                    "Fehler",
                    "Es gab einen unerwarteten Fehler beim Verarbeiten der Bilder.\n" +
                    "Bitte versuche es erneut oder kontaktiere einen Administrator."
            );
        }
    }
}
