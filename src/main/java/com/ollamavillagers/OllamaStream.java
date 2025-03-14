package com.ollamavillagers;

import java.util.ArrayList;
import java.util.LinkedList;
import java.net.*;
import java.io.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OllamaStream {

    public static class Message {
        public enum Role {
            USER("user"),
            SYSTEM("system"),
            ASSISTANT("assistant");
            private Role(String s) { this.s = s; }
            private String s;
            public String toString() { return s; }
        }

        public Role role;
        public String content;

        public Message(Role role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class InternalMessage {
        public String role;
        public String content;
    }

    private static class OllamaRequest {
        public String model;
        public InternalMessage[] messages;
        public String keep_alive = "5m";
    }

    private static class OllamaResponse {
        public InternalMessage message = null;
        public boolean done = false;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private HttpURLConnection conn = null;
    private ArrayList<InternalMessage> messages = new ArrayList<>();
    private boolean finished = false;
    private StringBuilder currentJson = new StringBuilder();
    private int charRead;
    private int depth = 0;

    private final String HOST = ConfigManager.config.host;

    public OllamaStream() {}

    public OllamaStream addMessage(Message message) {
        InternalMessage m = new InternalMessage();
        m.content = message.content;
        m.role = message.role.toString();
        messages.add(m);
        return this;
    }

    public OllamaStream addMessages(Message[] messages) {
        for(Message message : messages) addMessage(message);
        return this;
    }

    public void beginChat() {
        try {
            OllamaRequest req = new OllamaRequest();
            req.model = ConfigManager.config.model;
            InternalMessage[] messagesArr = new InternalMessage[messages.size()];
            for(int i = 0; i < messagesArr.length; ++i) messagesArr[i] = messages.get(i);
            req.messages = messagesArr;
            req.keep_alive = ConfigManager.config.keepAlive;            

            URL url = new URL(HOST + "api/chat");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setDoOutput(true);
            conn.setConnectTimeout(1000 * (int)ConfigManager.config.requestTimeoutSeconds);
            conn.setReadTimeout(1000 * (int)ConfigManager.config.requestTimeoutSeconds);
            System.out.println(GSON.toJson(req));
            conn.getOutputStream().write(GSON.toJson(req).getBytes("UTF-8"));
            conn.connect();
        } catch(Exception e) {
            System.err.println("Failed to create an Ollama stream: " + e.getMessage());
        }
    }

    public boolean isOver() {
        return finished;
    }

    public Message getNext() {
        if(finished) return new Message(Message.Role.SYSTEM, "TRIED TO GET MESSAGE ON A FINISHED STREAM.");

        try {
            InputStream inputStream = conn.getInputStream();
            Reader reader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(reader);
            while ((charRead = bufferedReader.read()) != -1) {
                char currentChar = (char) charRead;
                // build the current JSON object string
                if (currentChar == '{') {
                    if(depth == 0) {
                        currentJson.setLength(0);  // reset the builder for the new object   
                    }
                    depth++;
                }

                if (depth > 0) {
                    currentJson.append(currentChar);
                }

                // if we encounter a closing brace and we're inside an object, process the JSON
                if (currentChar == '}') {
                    depth--;
                    if(depth == 0) {
                        OllamaResponse re = GSON.fromJson(currentJson.toString(), OllamaResponse.class);
                        if(re.done) {
                            finished = true;
                            return new Message(Message.Role.ASSISTANT, "");
                        }
                        
                        return new Message(Message.Role.ASSISTANT, re.message.content);
                    }
                }
            }

            finished = true;
            bufferedReader.close();
            conn.disconnect();
        } catch(IOException e) {
            System.err.println("IOException was thrown during reading incoming JSON.");
            finished = true;
            conn.disconnect();
        }

        return new Message(Message.Role.SYSTEM, "EXITED THE getNext FUNCTION WITHOUT RECEIVING A MESSAGE!");
    }
}