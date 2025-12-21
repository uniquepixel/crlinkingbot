# CR Linking Bot

A Discord bot that extracts Clash Royale player tags from images via a slash command and links them to Discord users via the lostcrmanager REST API.

## Features

- **Command-Based Linking**: Use `/link` command with a message link to process screenshots
- **Role-Based Permissions**: Only users with specific roles can execute the link command
- **Gemini Vision API**: Automatically extracts player tags from Clash Royale profile screenshots
- **API Integration**: Links player tags to Discord users via the lostcrmanager REST API
- **User Feedback**: Provides clear feedback with emoji reactions and embed messages
- **Error Handling**: Comprehensive error handling with helpful error messages in German

## How It Works

1. A user posts a message with Clash Royale profile screenshots
2. An authorized user (with required role) executes `/link` command with the message link
3. Bot retrieves the message, extracts images, and adds a ⏳ reaction
4. Images are analyzed using Google Gemini Vision API to extract the player tag
5. Bot calls the lostcrmanager API to link the player tag to the Discord user
6. On success: ✅ reaction and success message with player info
7. On failure: ❌ reaction and error message explaining the issue

## Command Usage

### `/link message_link:`

Links a Clash Royale account by analyzing screenshots from a Discord message.

**Parameters:**
- `message_link` (required): The full Discord message link containing CR profile screenshots

**Example:**
```
/link message_link:https://discord.com/channels/123456789/987654321/111222333
```

**Required Roles:**
- Role ID: `1404574565350506587`
- Role ID: `1108472754149281822`

Only users with one of these roles can execute the command.

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Discord Bot Token
- Google Gemini API Key
- Access to a running lostcrmanager API instance

## Setup

### 1. Clone the Repository

```bash
git clone https://github.com/uniquepixel/crlinkingbot.git
cd crlinkingbot
```

### 2. Configure Environment Variables

Copy the example environment file and fill in your credentials:

```bash
cp .env.example .env
```

Edit `.env` and set the following variables:

- `CRLINKING_BOT_TOKEN`: Your Discord bot token from the [Discord Developer Portal](https://discord.com/developers/applications)
- `GOOGLE_GENAI_API_KEY`: Your Google Gemini API key from [Google AI Studio](https://makersuite.google.com/app/apikey)
- `LOSTCRMANAGER_API_URL`: URL to your lostcrmanager API (e.g., `http://localhost:7070`)
- `LOSTCRMANAGER_API_SECRET`: Shared secret for API authentication

### 3. Build the Project

```bash
mvn clean package
```

This will create a fat JAR with all dependencies in the `target` directory.

### 4. Run the Bot

You can run the bot in several ways:

#### Using Maven

```bash
export $(cat .env | xargs) && mvn exec:java -Dexec.mainClass="crlinkingbot.Bot"
```

#### Using the JAR

```bash
export $(cat .env | xargs)
java -jar target/crlinkingbot-0.0.1-SNAPSHOT.jar
```

#### Using Docker (optional)

Create a `Dockerfile`:

```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY target/crlinkingbot-0.0.1-SNAPSHOT.jar app.jar
CMD ["java", "-jar", "app.jar"]
```

Build and run:

```bash
docker build -t crlinkingbot .
docker run --env-file .env crlinkingbot
```

## Discord Bot Setup

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application
3. Go to the "Bot" section and create a bot
4. Copy the bot token to your `.env` file
5. Enable the following Privileged Gateway Intents:
   - Message Content Intent
   - Server Members Intent
6. Go to OAuth2 > URL Generator
7. Select scopes: `bot`, `applications.commands`
8. Select bot permissions:
   - Read Messages/View Channels
   - Send Messages
   - Embed Links
   - Attach Files
   - Read Message History
   - Add Reactions
   - Use Slash Commands
9. Use the generated URL to invite the bot to your server

## Usage Example

1. User posts a Clash Royale profile screenshot in any channel
2. Copy the message link (Right-click message → Copy Message Link)
3. Authorized user executes the slash command:
   ```
   /link message_link:https://discord.com/channels/123456789/987654321/111222333
   ```
4. Bot automatically:
   - Reacts with ⏳ (processing) on the target message
   - Extracts the player tag using AI
   - Links the account via the API
   - Reacts with ✅ (success) or ❌ (error)
   - Sends a detailed response message

## Architecture

### Components

- **Bot.java**: Main entry point, initializes JDA with slash command registration
- **LinkCommand.java**: Slash command handler with role-based permission checking
- **GeminiVisionService.java**: Handles image processing and tag extraction
- **LostCRManagerClient.java**: HTTP client for the lostcrmanager API
- **MessageUtil.java**: Utility for formatting Discord messages

### Dependencies

- **JDA 5.0.0-alpha.14**: Discord API wrapper for Java
- **Google API Client 2.2.0**: Google API HTTP client
- **Google HTTP Client Gson 1.43.3**: JSON support for Google API client
- **org.json 20230227**: JSON parsing library
- **SLF4J 2.0.7**: Logging framework

## Deployment

### Production Considerations

1. **Environment Variables**: Never commit `.env` to version control
2. **API Keys**: Keep your Discord token and Gemini API key secure
3. **Rate Limiting**: The bot handles rate limits, but monitor API usage
4. **Logging**: Check logs regularly for errors and issues
5. **Monitoring**: Consider adding health checks and monitoring

### Systemd Service (Linux)

Create `/etc/systemd/system/crlinkingbot.service`:

```ini
[Unit]
Description=CR Linking Bot
After=network.target

[Service]
Type=simple
User=crlinkingbot
WorkingDirectory=/opt/crlinkingbot
EnvironmentFile=/opt/crlinkingbot/.env
ExecStart=/usr/bin/java -jar /opt/crlinkingbot/crlinkingbot-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl enable crlinkingbot
sudo systemctl start crlinkingbot
sudo systemctl status crlinkingbot
```

## Troubleshooting

### Command doesn't appear

- Make sure the bot has been invited with `applications.commands` scope
- The slash command is registered when the bot starts
- Wait a few minutes for Discord to sync the command

### "No permission" error

- Verify the user has one of the required roles:
  - Role ID: `1404574565350506587`
  - Role ID: `1108472754149281822`
- Check that the Server Members Intent is enabled

### "Channel not found" error

- Ensure the bot has access to the channel in the message link
- Verify the message link format is correct
- Check that the bot is in the same server as the linked message

### "Spieler-Tag nicht gefunden" error

- Ensure the screenshot shows the Clash Royale profile clearly
- The player tag should be visible in the image
- Try posting a clearer or higher resolution screenshot

### API linking errors

- Verify the lostcrmanager API is running and accessible
- Check that the API secret matches in both services
- Review lostcrmanager logs for details

### Gemini API errors

- Verify your API key is valid
- Check your API quota hasn't been exceeded
- Ensure the image URLs are accessible from the internet

## Development

### Building

```bash
mvn clean compile
```

### Running tests

```bash
mvn test
```

### Code style

The project follows standard Java conventions. Use an IDE with Maven support for the best experience.

## License

This project is proprietary software. All rights reserved.

## Support

For issues or questions, please contact the development team or open an issue on the repository.
