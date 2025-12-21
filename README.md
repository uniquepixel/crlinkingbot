# CR Linking Bot

A Discord bot that extracts Clash Royale player tags from images via a slash command and links them to Discord users via the lostcrmanager REST API.

## Features

- **Command-Based Linking**: Use `/link` command with a message link to process screenshots
- **Role-Based Permissions**: Only users with specific roles can execute the link command
- **Queue System**: Requests are queued and processed on-demand by an external queue worker
- **REST API**: Exposes endpoints for external queue processing
- **Automatic Retry**: Failed requests are automatically retried up to 3 times
- **Persistent Queue**: Queue survives bot restarts
- **Gemini Vision API**: Automatically extracts player tags from Clash Royale profile screenshots
- **API Integration**: Links player tags to Discord users via the lostcrmanager REST API
- **User Feedback**: Provides clear feedback with emoji reactions and embed messages
- **Error Handling**: Comprehensive error handling with helpful error messages in German

## How It Works

1. A user posts a message with Clash Royale profile screenshots
2. An authorized user (with required role) executes `/link` command with the message link
3. Bot validates the message and adds the request to the queue with a ⏳ reaction
4. User receives immediate feedback with queue position
5. An external queue worker processes requests via the REST API:
   - Worker fetches pending requests from `/api/queue/pending`
   - Worker processes images using Google Gemini Vision API to extract player tags
   - Worker submits results via `/api/queue/result`
   - Bot updates Discord reactions (✅ for success, ❌ for failure) and sends result messages
6. Failed requests are automatically retried up to 3 times

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
- `QUEUE_API_PORT`: Port for the queue API server (default: `8090`)
- `QUEUE_API_SECRET`: Secret token for authenticating queue API requests

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
4. Bot responds with queue confirmation showing:
   - Request added to queue
   - Current queue position
   - Note about PC activity checking (every 5 minutes)
5. When PC is active, the bot automatically:
   - Retrieves the message and adds ⏳ (processing) reaction
   - Extracts the player tag using AI
   - Links the account via the API
   - Adds ✅ (success) or ❌ (error) reaction
   - Sends a detailed response message in the channel

## Queue System

The bot uses a persistent queue system with a REST API for external queue processing.

### How the Queue Works

- **Immediate Queueing**: When you use the `/link` command, the request is immediately added to the queue with a ⏳ reaction
- **External Processing**: An external queue worker processes requests via the REST API
- **Retry Logic**: Failed requests are automatically retried up to 3 times
- **Persistence**: The queue is saved to disk and survives bot restarts

### Queue File Location

The queue is stored in `linking_queue.json` in the same directory as the bot JAR file. This file is automatically created and managed by the bot.

### Queue API Endpoints

The bot exposes a REST API for queue management on the configured port (default: 8090).

#### Authentication

All endpoints (except `/api/health`) require Bearer token authentication:

```bash
Authorization: Bearer <QUEUE_API_SECRET>
```

#### `GET /api/health`

Health check endpoint (no authentication required).

**Response:**
```json
{
  "status": "healthy",
  "queueSize": 5,
  "timestamp": 1234567890
}
```

#### `GET /api/queue/pending`

Get all pending requests in the queue.

**Response:**
```json
{
  "success": true,
  "count": 5,
  "requests": [
    {
      "id": "uuid",
      "messageId": "123",
      "channelId": "456",
      "guildId": "789",
      "userId": "user123",
      "userTag": "username#1234",
      "imageUrls": ["url1", "url2"],
      "timestamp": 1234567890,
      "retryCount": 0
    }
  ]
}
```

#### `POST /api/queue/result`

Submit processing result for a request.

**Request:**
```json
{
  "requestId": "uuid",
  "success": true,
  "playerTag": "#ABC123",
  "errorMessage": "optional error message"
}
```

**Response (success):**
```json
{
  "success": true,
  "action": "completed",
  "message": "Player linked successfully"
}
```

**Response (retry):**
```json
{
  "success": true,
  "action": "requeued",
  "message": "Request re-queued for retry (attempt 1/3)"
}
```

**Response (failed):**
```json
{
  "success": true,
  "action": "failed",
  "message": "Request failed after max retries"
}
```

#### `GET /api/queue/stats`

Get queue statistics.

**Response:**
```json
{
  "success": true,
  "queueSize": 5,
  "oldestRequest": 1234567890,
  "newestRequest": 1234567999
}
```

### Example Queue Worker

Here's an example Python script for processing the queue:

```python
import requests
import time

API_BASE_URL = "http://localhost:8090"
API_SECRET = "your_secret_token_here"
HEADERS = {"Authorization": f"Bearer {API_SECRET}"}

def process_queue():
    # Get pending requests
    response = requests.get(f"{API_BASE_URL}/api/queue/pending", headers=HEADERS)
    data = response.json()
    
    if not data.get("success") or data.get("count") == 0:
        print("No pending requests")
        return
    
    # Process each request
    for request in data["requests"]:
        request_id = request["id"]
        image_urls = request["imageUrls"]
        
        print(f"Processing request {request_id}")
        
        # Process images (implement your image processing logic here)
        # For example, use Gemini Vision API to extract player tag
        try:
            player_tag = extract_player_tag(image_urls)
            
            # Submit success result
            result = {
                "requestId": request_id,
                "success": True,
                "playerTag": player_tag
            }
            requests.post(f"{API_BASE_URL}/api/queue/result", json=result, headers=HEADERS)
            print(f"Successfully processed {request_id}")
            
        except Exception as e:
            # Submit failure result
            result = {
                "requestId": request_id,
                "success": False,
                "errorMessage": str(e)
            }
            requests.post(f"{API_BASE_URL}/api/queue/result", json=result, headers=HEADERS)
            print(f"Failed to process {request_id}: {e}")

# Run worker in a loop
while True:
    try:
        process_queue()
    except Exception as e:
        print(f"Error: {e}")
    time.sleep(60)  # Check every minute
```

### Key Behaviors

- **Queue survives restarts**: If the bot restarts, pending requests remain in the queue
- **External processing**: Queue is processed by external workers via the REST API
- **Automatic retries**: Up to 3 retry attempts for failed requests
- **Ordered processing**: Requests are processed in the order they were received
- **Thread-safe**: Queue operations are thread-safe for concurrent API access

## Architecture

### Components

- **Bot.java**: Main entry point, initializes JDA, queue system, and API server
- **LinkCommand.java**: Slash command handler that enqueues requests
- **Queue System**:
  - **LinkingRequest.java**: Data model for queue requests
  - **RequestQueue.java**: Thread-safe persistent queue
- **API Server**:
  - **QueueAPIServer.java**: REST API server for queue management
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
