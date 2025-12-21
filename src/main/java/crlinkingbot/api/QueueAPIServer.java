package crlinkingbot.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import crlinkingbot.queue.LinkingRequest;
import crlinkingbot.queue.RequestQueue;
import crlinkingbot.services.LostCRManagerClient;
import crlinkingbot.util.MessageUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API server for queue management
 */
public class QueueAPIServer {
    private static final int MAX_RETRIES = 3;
    
    private final RequestQueue requestQueue;
    private final JDA jda;
    private final HttpServer server;
    private final String apiSecret;
    private final int port;

    /**
     * Constructor
     */
    public QueueAPIServer(RequestQueue requestQueue, JDA jda) throws IOException {
        this.requestQueue = requestQueue;
        this.jda = jda;
        
        // Get configuration from environment
        String portStr = System.getenv("QUEUE_API_PORT");
        int portValue = 8090;
        try {
            if (portStr != null && !portStr.isEmpty()) {
                portValue = Integer.parseInt(portStr);
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid QUEUE_API_PORT value: " + portStr + ", using default port 8090");
        }
        this.port = portValue;
        
        this.apiSecret = System.getenv("QUEUE_API_SECRET");
        if (apiSecret == null || apiSecret.isEmpty()) {
            throw new IllegalStateException("QUEUE_API_SECRET environment variable must be set");
        }
        
        // Create HTTP server
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Register endpoints
        server.createContext("/api/queue/pending", new PendingHandler());
        server.createContext("/api/queue/result", new ResultHandler());
        server.createContext("/api/queue/stats", new StatsHandler());
        server.createContext("/api/health", new HealthHandler());
        
        // Use default executor (creates a thread pool)
        server.setExecutor(null);
        
        System.out.println("Queue API Server initialized on port " + port);
    }

    /**
     * Start the server
     */
    public void start() {
        server.start();
        System.out.println("Queue API Server started on port " + port);
    }

    /**
     * Shutdown the server
     */
    public void shutdown() {
        System.out.println("Shutting down Queue API Server...");
        server.stop(2);
        System.out.println("Queue API Server stopped");
    }

    /**
     * Validate Bearer token from Authorization header
     */
    private boolean validateAuth(HttpExchange exchange) {
        List<String> authHeaders = exchange.getRequestHeaders().get("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            return false;
        }
        
        String authHeader = authHeaders.get(0);
        if (!authHeader.startsWith("Bearer ")) {
            return false;
        }
        
        String token = authHeader.substring(7);
        return apiSecret.equals(token);
    }

    /**
     * Send JSON response
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, JSONObject response) throws IOException {
        byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Read request body as string
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Handler for GET /api/queue/pending
     */
    private class PendingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("API Request: GET /api/queue/pending from " + exchange.getRemoteAddress());
            
            try {
                // Check authentication
                if (!validateAuth(exchange)) {
                    System.out.println("Authentication failed for GET /api/queue/pending");
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Unauthorized");
                    sendJsonResponse(exchange, 401, error);
                    return;
                }
                
                // Check method
                if (!"GET".equals(exchange.getRequestMethod())) {
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Method not allowed");
                    sendJsonResponse(exchange, 405, error);
                    return;
                }
                
                // Get all pending requests
                List<LinkingRequest> requests = requestQueue.getAll();
                
                JSONArray requestsArray = new JSONArray();
                for (LinkingRequest request : requests) {
                    JSONObject reqJson = new JSONObject();
                    reqJson.put("id", request.getId());
                    reqJson.put("messageId", request.getMessageId());
                    reqJson.put("channelId", request.getChannelId());
                    reqJson.put("guildId", request.getGuildId());
                    reqJson.put("userId", request.getUserId());
                    reqJson.put("userTag", request.getUserTag());
                    reqJson.put("imageUrls", new JSONArray(request.getImageUrls()));
                    reqJson.put("timestamp", request.getTimestamp());
                    reqJson.put("retryCount", request.getRetryCount());
                    requestsArray.put(reqJson);
                }
                
                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("count", requests.size());
                response.put("requests", requestsArray);
                
                System.out.println("Returning " + requests.size() + " pending requests");
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                System.out.println("Error handling GET /api/queue/pending: " + e.getMessage());
                e.printStackTrace();
                
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("error", "Internal server error: " + e.getMessage());
                sendJsonResponse(exchange, 500, error);
            }
        }
    }

    /**
     * Handler for POST /api/queue/result
     */
    private class ResultHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("API Request: POST /api/queue/result from " + exchange.getRemoteAddress());
            
            try {
                // Check authentication
                if (!validateAuth(exchange)) {
                    System.out.println("Authentication failed for POST /api/queue/result");
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Unauthorized");
                    sendJsonResponse(exchange, 401, error);
                    return;
                }
                
                // Check method
                if (!"POST".equals(exchange.getRequestMethod())) {
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Method not allowed");
                    sendJsonResponse(exchange, 405, error);
                    return;
                }
                
                // Read and parse request body
                String body = readRequestBody(exchange);
                JSONObject requestBody;
                try {
                    requestBody = new JSONObject(body);
                } catch (Exception e) {
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Invalid JSON in request body");
                    sendJsonResponse(exchange, 400, error);
                    return;
                }
                
                // Validate required fields
                if (!requestBody.has("requestId") || !requestBody.has("success")) {
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Missing required fields: requestId and success");
                    sendJsonResponse(exchange, 400, error);
                    return;
                }
                
                String requestId = requestBody.getString("requestId");
                boolean success = requestBody.getBoolean("success");
                String playerTag = requestBody.optString("playerTag", null);
                String errorMessage = requestBody.optString("errorMessage", null);
                
                System.out.println("Processing result for request " + requestId + ", success=" + success);
                
                // Find and remove the request from the queue
                LinkingRequest request = requestQueue.removeById(requestId);
                
                if (request == null) {
                    System.out.println("Request not found: " + requestId);
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Request not found in queue");
                    sendJsonResponse(exchange, 404, error);
                    return;
                }
                
                // Get Discord message
                MessageChannelUnion channel = jda.getChannelById(MessageChannelUnion.class, request.getChannelId());
                if (channel == null) {
                    System.out.println("Channel not found: " + request.getChannelId());
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Channel not found");
                    sendJsonResponse(exchange, 500, error);
                    return;
                }
                
                Message message;
                try {
                    message = channel.retrieveMessageById(request.getMessageId()).complete();
                } catch (Exception e) {
                    System.out.println("Message not found: " + request.getMessageId());
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Message not found");
                    sendJsonResponse(exchange, 500, error);
                    return;
                }
                
                // Process based on success/failure
                if (success) {
                    // Success - remove processing reaction, add success reaction
                    message.removeReaction(Emoji.fromUnicode("⏳")).queue();
                    message.addReaction(Emoji.fromUnicode("✅")).queue();
                    
                    // Link player if tag provided
                    if (playerTag != null && !playerTag.isEmpty()) {
                        JSONObject linkResult = LostCRManagerClient.linkPlayer(playerTag, request.getUserId());
                        
                        if (linkResult.getBoolean("success")) {
                            String successMsg = String.format("Account wurde erfolgreich verknüpft!\n\n"
                                    + "**Spieler-Tag:** `%s`\n"
                                    + "**Discord User:** <@%s>", playerTag, request.getUserId());
                            MessageUtil.sendSuccess(channel, "Account verknüpft", successMsg);
                        } else {
                            String errorMsg = "Es gab einen Fehler beim Verknüpfen des Accounts.";
                            if (linkResult.has("data") && linkResult.getJSONObject("data").has("message")) {
                                errorMsg += "\n\n**Fehler:** " + linkResult.getJSONObject("data").getString("message");
                            }
                            MessageUtil.sendError(channel, "Verknüpfung fehlgeschlagen", errorMsg);
                        }
                    } else {
                        String successMsg = String.format("Anfrage wurde erfolgreich verarbeitet!\n\n"
                                + "**Discord User:** <@%s>", request.getUserId());
                        MessageUtil.sendSuccess(channel, "Verarbeitung erfolgreich", successMsg);
                    }
                    
                    JSONObject response = new JSONObject();
                    response.put("success", true);
                    response.put("action", "completed");
                    response.put("message", "Player linked successfully");
                    sendJsonResponse(exchange, 200, response);
                    
                } else {
                    // Failure - check retry count
                    if (request.getRetryCount() < MAX_RETRIES) {
                        // Re-queue for retry
                        request.incrementRetryCount();
                        requestQueue.enqueue(request);
                        
                        System.out.println("Request re-queued for retry (" + request.getRetryCount() + "/" + MAX_RETRIES + ")");
                        
                        JSONObject response = new JSONObject();
                        response.put("success", true);
                        response.put("action", "requeued");
                        response.put("message", "Request re-queued for retry (attempt " + request.getRetryCount() + "/" + MAX_RETRIES + ")");
                        sendJsonResponse(exchange, 200, response);
                        
                    } else {
                        // Max retries reached - final failure
                        message.removeReaction(Emoji.fromUnicode("⏳")).queue();
                        message.addReaction(Emoji.fromUnicode("❌")).queue();
                        
                        String errorMsg = errorMessage != null ? errorMessage : "Die Verarbeitung ist fehlgeschlagen.";
                        errorMsg += "\n\n*Maximale Anzahl an Wiederholungsversuchen erreicht.*";
                        MessageUtil.sendError(channel, "Verarbeitung fehlgeschlagen", errorMsg);
                        
                        JSONObject response = new JSONObject();
                        response.put("success", true);
                        response.put("action", "failed");
                        response.put("message", "Request failed after max retries");
                        sendJsonResponse(exchange, 200, response);
                    }
                }
                
            } catch (Exception e) {
                System.out.println("Error handling POST /api/queue/result: " + e.getMessage());
                e.printStackTrace();
                
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("error", "Internal server error: " + e.getMessage());
                sendJsonResponse(exchange, 500, error);
            }
        }
    }

    /**
     * Handler for GET /api/queue/stats
     */
    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("API Request: GET /api/queue/stats from " + exchange.getRemoteAddress());
            
            try {
                // Check authentication
                if (!validateAuth(exchange)) {
                    System.out.println("Authentication failed for GET /api/queue/stats");
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Unauthorized");
                    sendJsonResponse(exchange, 401, error);
                    return;
                }
                
                // Check method
                if (!"GET".equals(exchange.getRequestMethod())) {
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Method not allowed");
                    sendJsonResponse(exchange, 405, error);
                    return;
                }
                
                // Get queue statistics
                List<LinkingRequest> requests = requestQueue.getAll();
                int queueSize = requests.size();
                
                Long oldestRequest = null;
                Long newestRequest = null;
                
                if (!requests.isEmpty()) {
                    oldestRequest = requests.get(0).getTimestamp();
                    newestRequest = requests.get(requests.size() - 1).getTimestamp();
                }
                
                JSONObject response = new JSONObject();
                response.put("success", true);
                response.put("queueSize", queueSize);
                
                if (oldestRequest != null) {
                    response.put("oldestRequest", oldestRequest);
                }
                if (newestRequest != null) {
                    response.put("newestRequest", newestRequest);
                }
                
                System.out.println("Returning queue stats: size=" + queueSize);
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                System.out.println("Error handling GET /api/queue/stats: " + e.getMessage());
                e.printStackTrace();
                
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("error", "Internal server error: " + e.getMessage());
                sendJsonResponse(exchange, 500, error);
            }
        }
    }

    /**
     * Handler for GET /api/health
     * No authentication required
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("API Request: GET /api/health from " + exchange.getRemoteAddress());
            
            try {
                // Check method
                if (!"GET".equals(exchange.getRequestMethod())) {
                    JSONObject error = new JSONObject();
                    error.put("success", false);
                    error.put("error", "Method not allowed");
                    sendJsonResponse(exchange, 405, error);
                    return;
                }
                
                JSONObject response = new JSONObject();
                response.put("status", "healthy");
                response.put("queueSize", requestQueue.size());
                response.put("timestamp", System.currentTimeMillis());
                
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                System.out.println("Error handling GET /api/health: " + e.getMessage());
                e.printStackTrace();
                
                JSONObject error = new JSONObject();
                error.put("status", "unhealthy");
                error.put("error", e.getMessage());
                sendJsonResponse(exchange, 500, error);
            }
        }
    }
}
