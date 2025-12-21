package crlinkingbot;

import crlinkingbot.listeners.TicketListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main bot class that initializes the Discord bot and stores configuration.
 */
public class Bot {
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);
    
    // Configuration from environment variables
    private static String genaiApiKey;
    private static String lostCRManagerUrl;
    private static String lostCRManagerSecret;
    
    public static void main(String[] args) {
        logger.info("Starting CR Linking Bot...");
        
        // Load environment variables
        if (!loadEnvironmentVariables()) {
            logger.error("Failed to load environment variables. Exiting.");
            System.exit(1);
        }
        
        logger.info("Configuration loaded successfully");
        
        // Initialize JDA
        String botToken = System.getenv("CRLINKING_BOT_TOKEN");
        try {
            JDA jda = JDABuilder.createDefault(botToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS
                    )
                    .addEventListeners(new TicketListener())
                    .build();
            
            jda.awaitReady();
            logger.info("CR Linking Bot is ready! Logged in as: {}", jda.getSelfUser().getAsTag());
        } catch (Exception e) {
            logger.error("Failed to initialize JDA", e);
            System.exit(1);
        }
    }
    
    /**
     * Load required environment variables
     */
    private static boolean loadEnvironmentVariables() {
        String botToken = System.getenv("CRLINKING_BOT_TOKEN");
        genaiApiKey = System.getenv("GOOGLE_GENAI_API_KEY");
        lostCRManagerUrl = System.getenv("LOSTCRMANAGER_API_URL");
        lostCRManagerSecret = System.getenv("LOSTCRMANAGER_API_SECRET");
        
        if (botToken == null || botToken.isEmpty()) {
            logger.error("CRLINKING_BOT_TOKEN environment variable is not set");
            return false;
        }
        
        if (genaiApiKey == null || genaiApiKey.isEmpty()) {
            logger.error("GOOGLE_GENAI_API_KEY environment variable is not set");
            return false;
        }
        
        if (lostCRManagerUrl == null || lostCRManagerUrl.isEmpty()) {
            logger.error("LOSTCRMANAGER_API_URL environment variable is not set");
            return false;
        }
        
        if (lostCRManagerSecret == null || lostCRManagerSecret.isEmpty()) {
            logger.error("LOSTCRMANAGER_API_SECRET environment variable is not set");
            return false;
        }
        
        logger.info("Environment variables loaded successfully");
        return true;
    }
    
    /**
     * Get the Gemini API key
     */
    public static String getGenaiApiKey() {
        return genaiApiKey;
    }
    
    /**
     * Get the LostCRManager API URL
     */
    public static String getLostCRManagerUrl() {
        return lostCRManagerUrl;
    }
    
    /**
     * Get the LostCRManager API secret
     */
    public static String getLostCRManagerSecret() {
        return lostCRManagerSecret;
    }
}
