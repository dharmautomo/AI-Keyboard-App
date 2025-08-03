
package org.dslul.openboard.inputmethod.latin;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import org.json.JSONObject;
import org.json.JSONArray;

public class AIHelper {
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

            // Create the JSON payload for ChatGPT API
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", "Improve the following text for clarity and grammar: " + input);
            
            JSONArray messages = new JSONArray();
            messages.put(message);
            
            JSONObject json = new JSONObject();
            json.put("model", "gpt-3.5-turbo");
            json.put("messages", messages);
            json.put("max_tokens", 150);
            json.put("temperature", 0.7);

            // Send the request
            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes("UTF-8"));
            os.flush();

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
                // Parse the JSON response to extract the improved text
                JSONObject responseJson = new JSONObject(response.toString());
                JSONArray choices = responseJson.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    return messageObj.getString("content").trim();
                }
            }
            
            return "Error: Unable to process text with AI";
            
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
