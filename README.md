# CR Linking Bot

A Discord bot that automatically extracts Clash Royale player tags from images posted in ticket channels and links them to Discord users via the lostcrmanager REST API.

## Features

- **Automatic Tag Detection**: Uses Google Gemini Vision API to extract player tags from Clash Royale profile screenshots
- **Ticket Channel Monitoring**: Automatically processes images posted in designated ticket channels
- **API Integration**: Links player tags to Discord users via the lostcrmanager REST API
- **User Feedback**: Provides clear feedback with emoji reactions and embed messages
- **Error Handling**: Comprehensive error handling with helpful error messages in German

## How It Works

1. User posts a message with Clash Royale profile screenshots in a ticket channel
2. Bot detects the images and adds a ⏳ reaction to indicate processing
3. Images are analyzed using Google Gemini Vision API to extract the player tag
4. Bot calls the lostcrmanager API to link the player tag to the Discord user
5. On success: ✅ reaction and success message with player info
6. On failure: ❌ reaction and error message explaining the issue

## Ticket Channel Detection

The bot considers a channel to be a ticket channel if its name:
- Contains "ticket"
- Contains "bewerbung" (German for application)
- Contains "application"
- Matches the pattern `ticket-<number>` (e.g., `ticket-123`)

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
6. Go to OAuth2 > URL Generator
7. Select scopes: `bot`
8. Select bot permissions:
   - Read Messages/View Channels
   - Send Messages
   - Embed Links
   - Attach Files
   - Read Message History
   - Add Reactions
9. Use the generated URL to invite the bot to your server

## Usage Example

1. Create a ticket channel (e.g., `ticket-123` or `bewerbung-john`)
2. User posts a message with Clash Royale profile screenshot(s)
3. Bot automatically:
   - Reacts with ⏳ (processing)
   - Extracts the player tag using AI
   - Links the account via the API
   - Reacts with ✅ (success) or ❌ (error)
   - Sends a detailed message with the result

## Architecture

### Components

- **Bot.java**: Main entry point, initializes JDA and Gemini client
- **TicketListener.java**: Listens for messages with images in ticket channels
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

### Bot doesn't respond to images

- Check that the channel name matches ticket detection criteria
- Verify the bot has permission to read messages and add reactions
- Check logs for errors

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
