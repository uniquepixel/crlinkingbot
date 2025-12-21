package crlinkingbot.services;

import crlinkingbot.Bot;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting Clash Royale player tags from images using Google
 * Gemini Vision API.
 */
public class GeminiVisionService {

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
			System.out.println("No image URLs provided");
			return null;
		}

		try {
			System.out.println("Processing " + imageUrls.size() + " images for player tag extraction");

			// Upload images to Gemini File API and get URIs
			List<String> fileUris = new ArrayList<>();
			for (String imageUrl : imageUrls) {
				String fileUri = uploadImageToGemini(imageUrl);
				if (fileUri != null) {
					fileUris.add(fileUri);
				}
			}

			if (fileUris.isEmpty()) {
				System.out.println("Failed to upload any images");
				return null;
			}

			// Build request JSON
			JSONObject request = buildGeminiRequest(fileUris);

			// Call Gemini API
			System.out.println("Calling Gemini Vision API...");
			String response = callGeminiAPI(request);

			// Parse response
			String playerTag = parseGeminiResponse(response);
			if (playerTag != null) {
				System.out.println("Successfully extracted player tag: " + playerTag);
			} else {
				System.out.println("No player tag found in response");
			}

			return playerTag;
		} catch (Exception e) {
			System.out.println("Error extracting player tag from images: " + e);
			return null;
		}
	}

	/**
	 * Upload an image to Gemini File API and return the file URI
	 */
	private static String uploadImageToGemini(String imageUrl) {
		try {
			System.out.println("Downloading image from: " + imageUrl);

			// First, download the image
			URL url = new URL(imageUrl);
			HttpURLConnection downloadConnection = (HttpURLConnection) url.openConnection();
			downloadConnection.setRequestMethod("GET");
			downloadConnection.setConnectTimeout(10000);
			downloadConnection.setReadTimeout(10000);

			int responseCode = downloadConnection.getResponseCode();
			if (responseCode != 200) {
				System.out.println("Failed to download image, response code: " + responseCode);
				return null;
			}

			// Get content type from response
			String contentType = downloadConnection.getContentType();
			if (contentType == null || !contentType.startsWith("image/")) {
				contentType = "image/png";
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

			System.out.println("Downloaded image, size: " + imageBytes.length + " bytes, content-type: " + contentType);

			// Now upload to Gemini File API using multipart/form-data
			String uploadUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files?key="
					+ Bot.getGenaiApiKey();
			HttpURLConnection uploadConnection = (HttpURLConnection) new URL(uploadUrl).openConnection();
			uploadConnection.setRequestMethod("POST");
			uploadConnection.setDoOutput(true);
			uploadConnection.setConnectTimeout(30000);
			uploadConnection.setReadTimeout(30000);

			// Create multipart boundary
			String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
			uploadConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

			// Build multipart/form-data request body
			try (OutputStream os = uploadConnection.getOutputStream()) {
				// Write file part
				os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
				os.write("Content-Disposition: form-data; name=\"file\"; filename=\"profile.png\"\r\n"
						.getBytes(StandardCharsets.UTF_8));
				os.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
				os.write(imageBytes);
				os.write("\r\n".getBytes(StandardCharsets.UTF_8));

				// End boundary
				os.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
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
					String responseBody = responseBuffer.toString(StandardCharsets.UTF_8);

					// Parse JSON response to extract file URI
					JSONObject responseJson = new JSONObject(responseBody);
					JSONObject fileObject = responseJson.getJSONObject("file");
					String fileUri = fileObject.getString("uri");

					System.out.println("Successfully uploaded image to Gemini File API: " + fileUri);
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
						errorMessage = errorBuffer.toString(StandardCharsets.UTF_8);
					}
				}
				System.out.println("Failed to upload image to Gemini File API, response code: " + uploadResponseCode + ", error: " + errorMessage);
				return null;
			}
		} catch (Exception e) {
			System.out.println("Error uploading image to Gemini File API from: " + imageUrl + " - " + e);
			e.printStackTrace();
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

		System.out.println("Gemini request: " + request.toString());

		return request;
	}

	/**
	 * Call Gemini API with the request
	 */
	private static String callGeminiAPI(JSONObject request) throws Exception {

		Client client = Client.builder().apiKey(Bot.getGenaiApiKey()).build();

		GenerateContentResponse response = client.models.generateContent("gemini-2.5-flash", request.toString(), null);

		System.out.println("Gemini response: " + response.text());

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
					System.out.println("Gemini response text: " + text);
					return parsePlayerTag(text);
				}
			}
		} catch (Exception e) {
			System.out.println("Error parsing Gemini response: " + e);
			e.printStackTrace();
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
