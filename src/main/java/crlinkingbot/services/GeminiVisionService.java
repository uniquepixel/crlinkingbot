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
			Extrahiere den Spielertag aus dem folgenden Clash-Royale-Profil-Screenshot fehlerfrei, auch bei schlechter Bildqualität.

			Aufgabe:

			Finde im Bild das Textfeld mit dem Spielertag.

			Der Spielertag steht im Profilbereich unter dem Spielernamen und beginnt immer mit # (Beispiel: #2YLJPV0LQ).

			Gib ausschließlich den erkannten Spielertag als Text aus, ohne Zusatz, ohne Erklärung, ohne Anführungszeichen.

			Qualitätsanforderungen:

			Nutze alle verfügbaren Techniken zur Texterkennung (OCR, Vergrößerung, Schärfung, Rauschunterdrückung), um auch bei Unschärfe oder Kompression den Text korrekt zu lesen.

			Wenn einzelne Zeichen unscharf sind, wähle das wahrscheinlichste Zeichen basierend auf:

			dem offiziellen Format von Clash-Royale-Tags (Großbuchstaben A–Z und Ziffern 0–9, beginnend mit #),

			typischen Verwechslungen (z.B. 0 vs. O, 1 vs. I, 8 vs. B) und ihrer Form im Bild.

			Vergleiche das Ergebnis mit gültigen Tag-Mustern und korrigiere offensichtliche OCR-Fehler.

			Fehlersicherheit:

			Wenn du dir nicht zu mindestens 95 % sicher bist, gib kein Ergebnis aus, sondern genau den Text:

			UNSICHER

			Sobald du einen Tag mit ≥95 % Sicherheit bestimmt hast, gib nur diesen Tag aus.
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
			String playerTag = parsePlayerTag(callGeminiAPI(request));

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
				System.out.println("Failed to upload image to Gemini File API, response code: " + uploadResponseCode
						+ ", error: " + errorMessage);
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
