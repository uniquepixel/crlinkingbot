package crlinkingbot.services;

import crlinkingbot.Bot;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client for calling the lostcrmanager REST API.
 */
public class LostCRManagerClient {
    
    /**
     * Link a player tag to a Discord user via the lostcrmanager API
     * 
     * @param playerTag The Clash Royale player tag (e.g., #ABC123)
     * @param userId The Discord user ID
     * @return JSONObject with response or null on error
     */
    public static JSONObject linkPlayer(String playerTag, String userId) {
        try {
            String apiUrl = Bot.getLostCRManagerUrl() + "/api/link";
            System.out.println("Calling lostcrmanager API: " + apiUrl + " for user " + userId + " with tag " + playerTag);
            
            // Build request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("tag", playerTag);
            requestBody.put("userId", userId);
            requestBody.put("source", "ticket-autolink");
            
            // Create HTTP connection
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + Bot.getLostCRManagerSecret());
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = connection.getResponseCode();
            System.out.println("API response code: " + responseCode);
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 200 && responseCode < 300 
                                    ? connection.getInputStream() 
                                    : connection.getErrorStream(),
                            StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
            
            String responseBody = response.toString();
            System.out.println("API response body: " + responseBody);
            
            // Parse response
            JSONObject result = new JSONObject();
            result.put("statusCode", responseCode);
            result.put("success", responseCode >= 200 && responseCode < 300);
            
            if (!responseBody.isEmpty()) {
                try {
                    JSONObject responseJson = new JSONObject(responseBody);
                    result.put("data", responseJson);
                } catch (Exception e) {
                    result.put("message", responseBody);
                }
            }
            
            return result;
        } catch (Exception e) {
            System.out.println("Error calling lostcrmanager API: " + e);
            e.printStackTrace();
            JSONObject errorResult = new JSONObject();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
}
