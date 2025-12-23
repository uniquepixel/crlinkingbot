package crlinkingbot.listeners;

import crlinkingbot.queue.LinkingRequest;
import crlinkingbot.queue.RequestQueue;
import crlinkingbot.util.MessageUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

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

	private final RequestQueue requestQueue;

	/**
	 * Constructor accepts RequestQueue
	 */
	public LinkCommand(RequestQueue requestQueue) {
		this.requestQueue = requestQueue;
	}

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
				System.out.println(
						"User " + event.getUser().getAsTag() + " attempted to use link command without permission");
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

			final MessageChannelUnion finalChannel = channel;

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

				// Create linking request
				String targetUserId = message.getAuthor().getId();
				String targetUserTag = message.getAuthor().getAsTag();
				String guildId = event.getGuild() != null ? event.getGuild().getId() : "unknown";

				LinkingRequest request = new LinkingRequest(messageId, channelId, guildId, targetUserId, targetUserTag,
						imageUrls);

				// Enqueue the request
				requestQueue.enqueue(request);
				int queuePosition = requestQueue.size();

				// Add processing reaction to the original message
				message.addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⏳")).queue();

				// Reply with success embed
				String successMessage = "Hallo <@" + targetUserId + ">,\r\n"
						+ "Wir haben deine Bewerbung erfolgreich erhalten!\r\n" + "\r\n"
						+ "Im nächsten Schritt wirst du mit unserem **Tracking-Bot** verlinkt.\r\n"
						+ "Dieser Bot erfasst automatisch deine **Trophäen- und Ranked-Statistiken**, damit wir deinen aktuellen Fortschritt im Spiel nachvollziehen können.\r\n"
						+ "\r\n"
						+ "Sobald die Verknüpfung hergestellt ist, läuft das Tracking automatisch weiter – du musst dafür nichts weiter tun.\r\n"
						+ "Nach dem Verlinken wirst du **wieder von uns hören**, sobald es mit deiner Bewerbung weitergeht.\r\n"
						+ "\r\n" + "Vielen Dank für dein Interesse an der Lost Family!\r\n" + "LG die CR-Vize";

				event.getHook().editOriginal(".").queue(msg -> {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					msg.delete().queue();
				});
				finalChannel.sendMessage(successMessage).queue();

				System.out.println("Enqueued request for " + imageUrls.size() + " images from message " + messageId
						+ " in channel " + channelId + " by command from user " + event.getUser().getAsTag()
						+ " (queue position: " + queuePosition + ")");

			}, error -> {
				event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title,
						"Nachricht mit der ID `" + messageId + "` konnte nicht gefunden werden.")).queue();
				System.out.println("Error retrieving message: " + messageId + " - " + error);
				error.printStackTrace();
			});

		}, "LinkCommand-" + event.getUser().getId() + "-" + System.currentTimeMillis()).start();
	}
}
