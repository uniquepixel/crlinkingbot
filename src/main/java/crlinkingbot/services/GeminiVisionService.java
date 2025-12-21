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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
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

			// Upload images to Gemini File API and get URIs
			List<String> fileUris = new ArrayList<>();
			for (String imageUrl : imageUrls) {
				String fileUri = uploadImageToGemini(imageUrl);
				if (fileUri != null) {
					fileUris.add(fileUri);
				}
			}

			if (fileUris.isEmpty()) {
				logger.warn("Failed to upload any images");
				return null;
			}

			// Build request JSON
			JSONObject request = buildGeminiRequest(fileUris);

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
	 * Upload an image to Gemini File API and return the file URI
	 */
	private static String uploadImageToGemini(String imageUrl) {
		try {
			logger.debug("Downloading image from: {}", imageUrl);
			
			// First, download the image
			URL url = new URL(imageUrl);
			HttpURLConnection downloadConnection = (HttpURLConnection) url.openConnection();
			downloadConnection.setRequestMethod("GET");
			downloadConnection.setConnectTimeout(10000);
			downloadConnection.setReadTimeout(10000);

			int responseCode = downloadConnection.getResponseCode();
			if (responseCode != 200) {
				logger.warn("Failed to download image, response code: {}", responseCode);
				return null;
			}

			// Get content type from response
			String contentType = downloadConnection.getContentType();
			if (contentType == null || !contentType.startsWith("image/")) {
				contentType = "image/png"; // Default to image/png if content type is unknown
			}

			// Read image bytes
			byte[] imageBytes;
			try (InputStream is = downloadConnection.getInputStream();
					ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = is.read(buffer)) != -1) {
					baos.write(buffer, 0, bytesRead);
				}
				imageBytes = baos.toByteArray();
			}

			logger.debug("Downloaded image, size: {} bytes, content-type: {}", imageBytes.length, contentType);

			// Now upload to Gemini File API
			String uploadUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + Bot.getGenaiApiKey();
			HttpURLConnection uploadConnection = (HttpURLConnection) new URL(uploadUrl).openConnection();
			uploadConnection.setRequestMethod("POST");
			uploadConnection.setDoOutput(true);
			uploadConnection.setConnectTimeout(30000);
			uploadConnection.setReadTimeout(30000);

			// Create multipart boundary
			String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
			uploadConnection.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);

			// Build multipart request body
			try (OutputStream os = uploadConnection.getOutputStream()) {
				// Write metadata part
				os.write(("--" + boundary + "\r\n").getBytes());
				os.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".getBytes());
				os.write("{\"file\":{\"display_name\":\"profile_screenshot\"}}\r\n".getBytes());

				// Write file data part
				os.write(("--" + boundary + "\r\n").getBytes());
				os.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes());
				os.write(imageBytes);
				os.write("\r\n".getBytes());

				// End boundary
				os.write(("--" + boundary + "--\r\n").getBytes());
				os.flush();
			}

			// Get response
			int uploadResponseCode = uploadConnection.getResponseCode();
			if (uploadResponseCode == 200) {
				try (InputStream responseStream = uploadConnection.getInputStream();
						ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream()) {
					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = responseStream.read(buffer)) != -1) {
						responseBuffer.write(buffer, 0, bytesRead);
					}
					String responseBody = responseBuffer.toString("UTF-8");
					
					// Parse JSON response to extract file URI
					JSONObject responseJson = new JSONObject(responseBody);
					JSONObject fileObject = responseJson.getJSONObject("file");
					String fileUri = fileObject.getString("uri");
					
					logger.info("Successfully uploaded image to Gemini File API: {}", fileUri);
					return fileUri;
				}
			} else {
				// Try to read error response
				String errorMessage = "Unknown error";
				try (InputStream errorStream = uploadConnection.getErrorStream()) {
					if (errorStream != null) {
						ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();
						byte[] buffer = new byte[4096];
						int bytesRead;
						while ((bytesRead = errorStream.read(buffer)) != -1) {
							errorBuffer.write(buffer, 0, bytesRead);
						}
						errorMessage = errorBuffer.toString("UTF-8");
					}
				}
				logger.error("Failed to upload image to Gemini File API, response code: {}, error: {}", 
						uploadResponseCode, errorMessage);
				return null;
			}
		} catch (Exception e) {
			logger.error("Error uploading image to Gemini File API from: {}", imageUrl, e);
			return null;
		}
	}

	/**
	 * Build Gemini API request JSON
	 */
	private static JSONObject buildGeminiRequest(List<String> fileUris) {
		JSONObject request = new JSONObject();

		JSONArray contents = new JSONArray();
		JSONObject content = new JSONObject();
		content.put("role", "user");

		JSONArray parts = new JSONArray();

		// Add text prompt
		JSONObject textPart = new JSONObject();
		textPart.put("text", PROMPT);
		parts.put(textPart);

		// Add file references
		for (String fileUri : fileUris) {
			JSONObject filePart = new JSONObject();
			JSONObject fileData = new JSONObject();
			fileData.put("file_uri", fileUri);
			filePart.put("file_data", fileData);
			parts.put(filePart);
		}

		content.put("parts", parts);
		contents.put(content);
		request.put("contents", contents);

		logger.debug("Gemini request: {}", request.toString());

		return request;
	}

	/**
	 * Call Gemini API with the request
	 */
	private static String callGeminiAPI(JSONObject request) throws Exception {

		Client client = Client.builder().apiKey(Bot.getGenaiApiKey()).build();

		GenerateContentResponse response = client.models.generateContent("gemini-2.5-flash", request.toString(), null);

		logger.debug("Gemini response: {}", response.text());

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
