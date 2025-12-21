package crlinkingbot;

import crlinkingbot.listeners.LinkCommand;
import crlinkingbot.queue.QueueProcessor;
import crlinkingbot.queue.RequestQueue;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * Main bot class that initializes the Discord bot and stores configuration.
 */
public class Bot {
    // Configuration from environment variables
    private static String genaiApiKey;
    private static String lostCRManagerUrl;
    private static String lostCRManagerSecret;
    
    // Queue system components
    private static RequestQueue requestQueue;
    private static QueueProcessor queueProcessor;
    
    public static void main(String[] args) {
        System.out.println("Starting CR Linking Bot...");
        
        // Load environment variables
        if (!loadEnvironmentVariables()) {
            System.out.println("Failed to load environment variables. Exiting.");
            System.exit(1);
        }
        
        System.out.println("Configuration loaded successfully");
        
        // Initialize request queue before JDA
        System.out.println("Initializing request queue...");
        requestQueue = new RequestQueue();
        
        // Initialize JDA
        String botToken = System.getenv("CRLINKING_BOT_TOKEN");
        try {
            JDA jda = JDABuilder.createDefault(botToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            GatewayIntent.GUILD_MEMBERS
                    )
                    .addEventListeners(new LinkCommand(requestQueue))
                    .build();
            
            jda.awaitReady();
            
            // Register slash commands
            jda.updateCommands().addCommands(
                    Commands.slash("link", "Link einen Clash Royale Account über eine Nachricht mit Screenshots")
                            .addOption(OptionType.STRING, "message_link", "Link zur Nachricht mit den CR Screenshots", true)
            ).queue();
            
            System.out.println("CR Linking Bot is ready! Logged in as: " + jda.getSelfUser().getAsTag());
            System.out.println("Slash command '/link' registered successfully");
            
            // Initialize and start queue processor
            System.out.println("Starting queue processor...");
            queueProcessor = new QueueProcessor(requestQueue, jda);
            queueProcessor.start();
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down bot...");
                if (queueProcessor != null) {
                    queueProcessor.shutdown();
                }
            }));
            
        } catch (Exception e) {
            System.out.println("Failed to initialize JDA: " + e);
            e.printStackTrace();
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
            System.out.println("CRLINKING_BOT_TOKEN environment variable is not set");
            return false;
        }
        
        if (genaiApiKey == null || genaiApiKey.isEmpty()) {
            System.out.println("GOOGLE_GENAI_API_KEY environment variable is not set");
            return false;
        }
        
        if (lostCRManagerUrl == null || lostCRManagerUrl.isEmpty()) {
            System.out.println("LOSTCRMANAGER_API_URL environment variable is not set");
            return false;
        }
        
        if (lostCRManagerSecret == null || lostCRManagerSecret.isEmpty()) {
            System.out.println("LOSTCRMANAGER_API_SECRET environment variable is not set");
            return false;
        }
        
        System.out.println("Environment variables loaded successfully");
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
    
    /**
     * Get the request queue
     */
    public static RequestQueue getRequestQueue() {
        return requestQueue;
    }
}
