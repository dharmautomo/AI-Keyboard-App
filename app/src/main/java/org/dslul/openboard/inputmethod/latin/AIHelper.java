
package org.dslul.openboard.inputmethod.latin;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import android.util.Log;

public class AIHelper {
    private static final String TAG = "AIHelper";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String API_KEY = "YOUR_API_KEY_HERE"; // Replace with your actual API key
    
    public static String callOpenAI(String input) {
        try {
            URL url = new URL(OPENAI_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Create the JSON payload manually (without external JSON library)
            String jsonPayload = "{"
                + "\"model\":\"gpt-3.5-turbo\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"Improve the following text for clarity and grammar: " + escapeJson(input) + "\"}],"
                + "\"max_tokens\":150,"
                + "\"temperature\":0.7"
                + "}";

            // Send the request
            OutputStream os = conn.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
            os.close();

            // Read the response
            int responseCode = conn.getResponseCode();
            InputStream inputStream = (responseCode >= 200 && responseCode < 300) 
                ? conn.getInputStream() 
                : conn.getErrorStream();
                
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            StringBuilder response = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) {
                response.append(output);
            }
            br.close();

            if (responseCode >= 200 && responseCode < 300) {
                // Simple string parsing to extract content (basic implementation)
                String responseStr = response.toString();
                int contentStart = responseStr.indexOf("\"content\":\"");
                if (contentStart != -1) {
                    contentStart += 11; // length of "\"content\":\""
                    int contentEnd = responseStr.indexOf("\"", contentStart);
                    if (contentEnd != -1) {
                        return responseStr.substring(contentStart, contentEnd)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                    }
                }
            }
            
            Log.e(TAG, "API Error: " + response.toString());
            return "Error: Unable to process text with AI";
            
        } catch (Exception e) {
            Log.e(TAG, "Exception in callOpenAI", e);
            return "Error: " + e.getMessage();
        }
    }
    
    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
