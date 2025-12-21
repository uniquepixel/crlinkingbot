package crlinkingbot.listeners;

import crlinkingbot.services.GeminiVisionService;
import crlinkingbot.services.LostCRManagerClient;
import crlinkingbot.util.MessageUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.json.JSONObject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command listener for manually linking Clash Royale accounts via message
 * links.
 */
public class LinkCommand extends ListenerAdapter {

	// Allowed role IDs
	private static final String ROLE_ID_1 = "1404574565350506587";
	private static final String ROLE_ID_2 = "1108472754149281822";

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("link")) {
			return;
		}

		event.deferReply().queue();

		new Thread(() -> {
			String title = "CR Account Link";

			// Check if user has required role
			Member member = event.getMember();
			if (member == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title,
						"Dieser Befehl kann nur auf einem Server ausgeführt werden.")).queue();
				return;
			}

			boolean hasPermission = member.getRoles().stream().map(Role::getId)
					.anyMatch(roleId -> roleId.equals(ROLE_ID_1) || roleId.equals(ROLE_ID_2));

			if (!hasPermission) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.createErrorEmbed(title, "Du hast keine Berechtigung, diesen Befehl auszuführen."))
						.queue();
				System.out.println("User " + event.getUser().getAsTag() + " attempted to use link command without permission");
				return;
			}

			// Get message link parameter
			OptionMapping messagelinkOption = event.getOption("message_link");
			if (messagelinkOption == null) {
				event.getHook()
						.editOriginalEmbeds(
								MessageUtil.createErrorEmbed(title, "Der Parameter `message_link` ist erforderlich."))
						.queue();
				return;
			}

			String messagelink = messagelinkOption.getAsString();

			// Parse message link to extract channel ID and message ID
			// Expected format: https://discord.com/channels/SERVER_ID/CHANNEL_ID/MESSAGE_ID
			String[] parts = messagelink.split("/");
			if (parts.length < 7) {
				event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title,
						"Ungültiger Message-Link. Format sollte sein: https://discord.com/channels/SERVER_ID/CHANNEL_ID/MESSAGE_ID"))
						.queue();
				return;
			}

			String messageId = parts[parts.length - 1];
			String channelId = parts[parts.length - 2];

			// Get the channel
			MessageChannelUnion channel = null;
			try {
				channel = event.getJDA().getChannelById(MessageChannelUnion.class, channelId);
			} catch (Exception e) {
				System.out.println("Error getting channel by ID: " + channelId + " - " + e);
				e.printStackTrace();
			}

			if (channel == null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.createErrorEmbed(title, "Channel mit der ID `" + channelId + "` nicht gefunden."))
						.queue();
				return;
			}

			channel.retrieveMessageById(messageId).queue(message -> {
				// Check if message has image attachments
				List<String> imageUrls = message.getAttachments().stream().filter(attachment -> attachment.isImage())
						.map(attachment -> attachment.getUrl()).collect(Collectors.toList());

				if (imageUrls.isEmpty()) {
					event.getHook().editOriginalEmbeds(
							MessageUtil.createErrorEmbed(title, "Die verlinkte Nachricht enthält keine Bilder."))
							.queue();
					return;
				}

				// Add processing reaction to the target message
				message.addReaction(Emoji.fromUnicode("⏳")).queue();

				// Update command response
				event.getHook()
						.editOriginalEmbeds(MessageUtil.createInfoEmbed(title,
								"Verarbeite " + imageUrls.size() + " Bild(er) aus der verlinkten Nachricht..."))
						.queue();

				System.out.println("Processing " + imageUrls.size() + " images from message " + messageId
						+ " in channel " + channelId + " by command from user " + event.getUser().getAsTag());

				// Process images
				processImages(event, message, imageUrls, message.getAuthor().getId());
			}, error -> {
				event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title,
						"Nachricht mit der ID `" + messageId + "` konnte nicht gefunden werden.")).queue();
				System.out.println("Error retrieving message: " + messageId + " - " + error);
				error.printStackTrace();
			});

		}, "LinkCommand-" + event.getUser().getId() + "-" + System.currentTimeMillis()).start();
	}

	/**
	 * Process images asynchronously to extract player tag and link player
	 */
	private void processImages(SlashCommandInteractionEvent event, Message message, List<String> imageUrls,
			String targetUserId) {
		String title = "CR Account Link";

		try {
			// Extract player tag using Gemini Vision
			System.out.println("Extracting player tag from images...");
			String playerTag = GeminiVisionService.extractPlayerTag(imageUrls);

			if (playerTag == null) {
				// Tag not found
				System.out.println("No player tag found in images");
				message.removeReaction(Emoji.fromUnicode("⏳")).queue();
				message.addReaction(Emoji.fromUnicode("❌")).queue();

				event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title,
						"Ich konnte keinen Clash Royale Spieler-Tag in den Screenshots finden.\n\n"
								+ "Bitte stelle sicher, dass:\n" + "• Der Screenshot ein Clash Royale Profil zeigt\n"
								+ "• Der Spieler-Tag (z.B. #ABC123) gut lesbar ist\n"
								+ "• Das Bild nicht verschwommen oder zu klein ist"))
						.queue();
				return;
			}

			System.out.println("Found player tag: " + playerTag);

			// Call lostcrmanager API to link player
			System.out.println("Linking player " + playerTag + " to user " + targetUserId);
			JSONObject response = LostCRManagerClient.linkPlayer(playerTag, targetUserId);

			if (response.getBoolean("success")) {
				// Success
				System.out.println("Successfully linked player " + playerTag + " to user " + targetUserId);
				message.removeReaction(Emoji.fromUnicode("⏳")).queue();
				message.addReaction(Emoji.fromUnicode("✅")).queue();

				String successMessage = String.format("Account wurde erfolgreich verknüpft!\n\n"
						+ "**Spieler-Tag:** `%s`\n" + "**Discord User:** <@%s>", playerTag, targetUserId);

				event.getHook().editOriginalEmbeds(MessageUtil.createSuccessEmbed(title, successMessage)).queue();

				MessageUtil.sendSuccess(message.getChannel(), "Account verknüpft", successMessage);
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

				event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title, errorMessage)).queue();

				MessageUtil.sendError(message.getChannel(), "Verknüpfung fehlgeschlagen", errorMessage);
			}
		} catch (Exception e) {
			System.out.println("Error processing images: " + e);
			e.printStackTrace();
			message.removeReaction(Emoji.fromUnicode("⏳")).queue(
					success -> message.addReaction(Emoji.fromUnicode("❌")).queue(),
					error -> {
						System.out.println("Failed to update reactions: " + error);
						error.printStackTrace();
					});

			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.createErrorEmbed(title,
									"Es gab einen unerwarteten Fehler beim Verarbeiten der Bilder.\n"
											+ "Bitte versuche es erneut oder kontaktiere einen Administrator."))
					.queue();

			MessageUtil.sendError(message.getChannel(), "Fehler",
					"Es gab einen unerwarteten Fehler beim Verarbeiten der Bilder.\n"
							+ "Bitte versuche es erneut oder kontaktiere einen Administrator.");
		}
	}
}
