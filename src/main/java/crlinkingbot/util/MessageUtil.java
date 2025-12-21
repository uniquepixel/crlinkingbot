package crlinkingbot.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.awt.Color;
import java.time.Instant;

/**
 * Utility class for formatting Discord messages and embeds.
 */
public class MessageUtil {
    
    /**
     * Send a success embed message
     * 
     * @param channel The channel to send the message to
     * @param title The title of the embed
     * @param description The description of the embed
     */
    public static void sendSuccess(MessageChannelUnion channel, String title, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ " + title)
                .setDescription(description)
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now());
        
        channel.sendMessageEmbeds(embed.build()).queue();
    }
    
    /**
     * Send an error embed message
     * 
     * @param channel The channel to send the message to
     * @param title The title of the embed
     * @param description The description of the embed
     */
    public static void sendError(MessageChannelUnion channel, String title, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("❌ " + title)
                .setDescription(description)
                .setColor(Color.RED)
                .setTimestamp(Instant.now());
        
        channel.sendMessageEmbeds(embed.build()).queue();
    }
    
    /**
     * Send an info embed message
     * 
     * @param channel The channel to send the message to
     * @param title The title of the embed
     * @param description The description of the embed
     */
    public static void sendInfo(MessageChannelUnion channel, String title, String description) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("ℹ️ " + title)
                .setDescription(description)
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now());
        
        channel.sendMessageEmbeds(embed.build()).queue();
    }
    
    /**
     * Create a success embed
     * 
     * @param title The title of the embed
     * @param description The description of the embed
     * @return MessageEmbed object
     */
    public static MessageEmbed createSuccessEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle("✅ " + title)
                .setDescription(description)
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now())
                .build();
    }
    
    /**
     * Create an error embed
     * 
     * @param title The title of the embed
     * @param description The description of the embed
     * @return MessageEmbed object
     */
    public static MessageEmbed createErrorEmbed(String title, String description) {
        return new EmbedBuilder()
                .setTitle("❌ " + title)
                .setDescription(description)
                .setColor(Color.RED)
                .setTimestamp(Instant.now())
                .build();
    }
}
