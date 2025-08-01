package com.unboxy.gamemanagerservice.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class GameUpdateRequest {
    private String userMessage; // The user's request for changes
    private List<ConversationMessage> conversationHistory; // Optional: chat history for context
    private Map<String, Object> gameModifications; // Optional: structured modifications
    
    @Data
    public static class ConversationMessage {
        private String role; // "user" or "assistant"
        private String message;
        private Long timestamp; // Optional: when the message was sent
    }
}