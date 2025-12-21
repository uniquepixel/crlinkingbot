package crlinkingbot.queue;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks if PC (LM Studio) is available via health check.
 */
public class PCActivityChecker {
    private final String healthCheckUrl;
    private static final int TIMEOUT_MS = 5000; // 5 second timeout

    /**
     * Constructor with health check URL from environment
     */
    public PCActivityChecker() {
        String envUrl = System.getenv("LLM_PROXY_HEALTH_URL");
        this.healthCheckUrl = (envUrl != null && !envUrl.isEmpty()) 
            ? envUrl 
            : "http://localhost:8080/health";
        System.out.println("PC Activity Checker initialized with URL: " + healthCheckUrl);
    }

    /**
     * Check if PC is active (LM Studio is running)
     * 
     * @return true if PC is active, false otherwise
     */
    public boolean isPCActive() {
        try {
            URL url = new URL(healthCheckUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            boolean isActive = responseCode >= 200 && responseCode < 300;
            
            if (isActive) {
                System.out.println("PC is ACTIVE - Health check returned: " + responseCode);
            } else {
                System.out.println("PC is INACTIVE - Health check returned: " + responseCode);
            }
            
            connection.disconnect();
            return isActive;
        } catch (Exception e) {
            System.out.println("PC is INACTIVE - Health check failed: " + e.getMessage());
            return false;
        }
    }
}
