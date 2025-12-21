package crlinkingbot.queue;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe persistent queue for linking requests.
 */
public class RequestQueue {
    private final ConcurrentLinkedQueue<LinkingRequest> queue;
    private final File queueFile;

    /**
     * Constructor initializes queue and loads from file
     */
    public RequestQueue() {
        this.queue = new ConcurrentLinkedQueue<>();
        // Queue file is in the same directory as the JAR
        String jarDir = System.getProperty("user.dir");
        this.queueFile = new File(jarDir, "linking_queue.json");
        loadQueue();
    }

    /**
     * Add request to queue and save to file
     */
    public synchronized void enqueue(LinkingRequest request) {
        queue.offer(request);
        saveQueue();
        System.out.println("Enqueued request " + request.getId() + " for user " + request.getUserTag());
    }

    /**
     * Remove and return next request, save to file
     */
    public synchronized LinkingRequest dequeue() {
        LinkingRequest request = queue.poll();
        if (request != null) {
            saveQueue();
            System.out.println("Dequeued request " + request.getId() + " for user " + request.getUserTag());
        }
        return request;
    }

    /**
     * View next request without removing
     */
    public LinkingRequest peek() {
        return queue.peek();
    }

    /**
     * Check if queue is empty
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Get queue size
     */
    public int size() {
        return queue.size();
    }

    /**
     * Get all requests for viewing
     */
    public List<LinkingRequest> getAll() {
        return new ArrayList<>(queue);
    }

    /**
     * Clear all requests
     */
    public synchronized void clear() {
        queue.clear();
        saveQueue();
        System.out.println("Cleared all requests from queue");
    }

    /**
     * Load queue from file on startup
     */
    private void loadQueue() {
        if (!queueFile.exists()) {
            System.out.println("Queue file does not exist, starting with empty queue");
            return;
        }

        try {
            String content = Files.readString(queueFile.toPath(), StandardCharsets.UTF_8);
            JSONArray jsonArray = new JSONArray(content);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                LinkingRequest request = LinkingRequest.fromJSON(json);
                queue.offer(request);
            }
            
            System.out.println("Loaded " + queue.size() + " requests from queue file");
        } catch (IOException e) {
            System.out.println("Error loading queue from file: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Error parsing queue file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save queue to file after modifications
     */
    private void saveQueue() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (LinkingRequest request : queue) {
                jsonArray.put(request.toJSON());
            }

            try (FileWriter writer = new FileWriter(queueFile, StandardCharsets.UTF_8)) {
                writer.write(jsonArray.toString(2)); // Pretty print with indent
            }

            System.out.println("Saved " + queue.size() + " requests to queue file");
        } catch (IOException e) {
            System.out.println("Error saving queue to file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
