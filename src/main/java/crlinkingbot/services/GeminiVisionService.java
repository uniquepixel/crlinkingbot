package crlinkingbot.services;

import crlinkingbot.Bot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting Clash Royale player tags from images using Google
 * Gemini Vision API.
 */
public class GeminiVisionService {
	private static final Logger logger = LoggerFactory.getLogger(GeminiVisionService.class);

	private static final String PROMPT = """
			Analysiere diese Clash Royale Profil-Screenshots und extrahiere den Spieler-Tag.

			Der Spieler-Tag:
			- Beginnt IMMER mit dem # Symbol
			- Enthält 8-10 Zeichen (Großbuchstaben und Zahlen)
			- Beispiel: #ABC123XYZ oder #2PP
			- Ist normalerweise unter dem Spielernamen oder im Profil zu finden

			WICHTIG:
			- Gib NUR den Spieler-Tag zurück, nichts anderes
			- Format: #TAG (mit # am Anfang)
			- Wenn kein Tag gefunden wurde, antworte mit: NOT_FOUND
			- Keine Erklärungen, keine zusätzlichen Texte

			Antwort (nur der Tag):
			""";

	private static final Pattern TAG_PATTERN = Pattern.compile("#[A-Z0-9]{3,10}");

	/**
	 * Extract player tag from a list of image URLs
	 * 
	 * @param imageUrls List of image URLs to process
	 * @return Player tag in format #ABC123 or null if not found
	 */
	public static String extractPlayerTag(List<String> imageUrls) {
		if (imageUrls == null || imageUrls.isEmpty()) {
			logger.warn("No image URLs provided");
			return null;
		}

		try {
			logger.info("Processing {} images for player tag extraction", imageUrls.size());

			// Download images and convert to base64
			List<String> base64Images = new ArrayList<>();
			for (String imageUrl : imageUrls) {
				String base64 = downloadAndEncodeImage(imageUrl);
				if (base64 != null) {
					base64Images.add(base64);
				}
			}

			if (base64Images.isEmpty()) {
				logger.warn("Failed to download any images");
				return null;
			}

			// Build request JSON
			JSONObject request = buildGeminiRequest(base64Images);

			// Call Gemini API
			logger.info("Calling Gemini Vision API...");
			String response = callGeminiAPI(request);

			// Parse response
			String playerTag = parseGeminiResponse(response);
			if (playerTag != null) {
				logger.info("Successfully extracted player tag: {}", playerTag);
			} else {
				logger.warn("No player tag found in response");
			}

			return playerTag;
		} catch (Exception e) {
			logger.error("Error extracting player tag from images", e);
			return null;
		}
	}

	/**
	 * Download an image and encode it as base64
	 */
	private static String downloadAndEncodeImage(String imageUrl) {
		try {
			logger.debug("Downloading image from: {}", imageUrl);
			URL url = new URL(imageUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(10000);
			connection.setReadTimeout(10000);

			int responseCode = connection.getResponseCode();
			if (responseCode != 200) {
				logger.warn("Failed to download image, response code: {}", responseCode);
				return null;
			}

			// Get content type from response
			String contentType = connection.getContentType();

			try (InputStream is = connection.getInputStream();
					ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = is.read(buffer)) != -1) {
					baos.write(buffer, 0, bytesRead);
				}
				byte[] imageBytes = baos.toByteArray();
				String base64 = Base64.getEncoder().encodeToString(imageBytes);

				// Store both base64 and content type
				// Return format: "content-type:base64-data"
				if (contentType != null && (contentType.startsWith("image/"))) {
					return contentType + ":" + base64;
				} else {
					// Default to image/png if content type is unknown
					return "image/png:" + base64;
				}
			}
		} catch (Exception e) {
			logger.error("Error downloading image from: {}", imageUrl, e);
			return null;
		}
	}

	/**
	 * Build Gemini API request JSON
	 */
	private static JSONObject buildGeminiRequest(List<String> base64ImagesWithType) {
		JSONObject request = new JSONObject();

		JSONArray contents = new JSONArray();
		JSONObject content = new JSONObject();
		content.put("role", "user");

		JSONArray parts = new JSONArray();

		// Add text prompt
		JSONObject textPart = new JSONObject();
		textPart.put("text", PROMPT);
		parts.put(textPart);

		// Add images
		for (String imageData : base64ImagesWithType) {
			String[] parts_split = imageData.split(":", 2);
			String mimeType = parts_split[0];
			String base64 = parts_split.length > 1 ? parts_split[1] : parts_split[0];

			JSONObject imagePart = new JSONObject();
			JSONObject inlineData = new JSONObject();
			inlineData.put("mime_type", mimeType);
			inlineData.put("data", base64);
			imagePart.put("inline_data", inlineData);
			parts.put(imagePart);
		}

		content.put("parts", parts);
		contents.put(content);
		request.put("contents", contents);

		System.out.println(request.toString());

		return request;
	}

	/**
	 * Call Gemini API with the request
	 */
	private static String callGeminiAPI(JSONObject request) throws Exception {

		Client client = Client.builder().apiKey(Bot.getGenaiApiKey()).build();

		GenerateContentResponse response = client.models.generateContent("gemini-2.5-flash", request.toString(), null);

		System.out.println(response.text());

		return response.text();
	}

	/**
	 * Parse Gemini API response to extract player tag
	 */
	private static String parseGeminiResponse(String responseJson) {
		try {
			JSONObject response = new JSONObject(responseJson);
			JSONArray candidates = response.getJSONArray("candidates");
			if (candidates.length() > 0) {
				JSONObject candidate = candidates.getJSONObject(0);
				JSONObject content = candidate.getJSONObject("content");
				JSONArray parts = content.getJSONArray("parts");
				if (parts.length() > 0) {
					JSONObject part = parts.getJSONObject(0);
					String text = part.getString("text");
					logger.info("Gemini response text: {}", text);
					return parsePlayerTag(text);
				}
			}
		} catch (Exception e) {
			logger.error("Error parsing Gemini response", e);
		}
		return null;
	}

	/**
	 * Parse player tag from text using regex
	 */
	private static String parsePlayerTag(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}

		// Check if response indicates no tag found
		if (text.contains("NOT_FOUND")) {
			return null;
		}

		// Extract tag using regex
		Matcher matcher = TAG_PATTERN.matcher(text);
		if (matcher.find()) {
			return matcher.group();
		}

		return null;
	}
}
