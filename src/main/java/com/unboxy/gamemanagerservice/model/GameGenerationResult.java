package com.unboxy.gamemanagerservice.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Wrapper class to hold both the generated game files and AI's text response
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameGenerationResult {
    private Map<String, String> files; // filename -> file content
    private String aiResponse; // AI's textual response explaining what it did
}