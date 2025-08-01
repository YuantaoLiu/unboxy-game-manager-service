package com.unboxy.gamemanagerservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboxy.gamemanagerservice.config.AnthropicConfig;
import com.unboxy.gamemanagerservice.model.GameGenerationMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedAIGameGenerationService {

    private final AnthropicConfig anthropicConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;

    public Mono<Map<String, String>> generateGameProject(GameGenerationMetadata metadata) {
        return generateMultipleFiles(metadata)
                .onErrorResume(e -> {
                    log.warn("Claude API with tools failed, falling back to simple generation: {}", e.getMessage());
                    return generateSimpleProject(metadata);
                });
    }

    private Mono<Map<String, String>> generateMultipleFiles(GameGenerationMetadata metadata) {
        // Generate all files in parallel using separate API calls
        Mono<String> htmlFile = generateSingleFile(metadata, "html")
                .onErrorReturn(generateSimpleGameTemplate(metadata));
        Mono<String> cssFile = generateSingleFile(metadata, "css")
                .onErrorReturn(generateSimpleCSS());
        Mono<String> jsFile = generateSingleFile(metadata, "js")
                .onErrorReturn(generateSimpleJS());
        Mono<String> jsonFile = generateSingleFile(metadata, "json")
                .onErrorReturn(generatePackageJson(metadata));

        return Mono.zip(htmlFile, cssFile, jsFile, jsonFile)
                .map(tuple -> {
                    Map<String, String> projectFiles = new HashMap<>();
                    projectFiles.put("index.html", tuple.getT1());
                    projectFiles.put("styles.css", tuple.getT2());
                    projectFiles.put("game.js", tuple.getT3());
                    projectFiles.put("package.json", tuple.getT4());
                    
                    // Validate that no files are empty and use fallbacks if needed
                    if (tuple.getT3().trim().isEmpty()) {
                        log.warn("JS file was empty, using fallback");
                        projectFiles.put("game.js", generateSimpleJS());
                    }
                    if (tuple.getT2().trim().isEmpty()) {
                        log.warn("CSS file was empty, using fallback");
                        projectFiles.put("styles.css", generateSimpleCSS());
                    }
                    
                    log.info("Generated all 4 files successfully");
                    return projectFiles;
                });
    }

    private Mono<String> generateSingleFile(GameGenerationMetadata metadata, String fileType) {
        String prompt = buildSingleFilePrompt(metadata, fileType);
        String toolName = getToolNameForFileType(fileType);
        
        Map<String, Object> requestBody = Map.of(
            "model", anthropicConfig.getModel(),
            "max_tokens", 2000,  // Smaller limit for single files
            "tools", List.of(createSingleFileTool(fileType)),
            "messages", List.of(Map.of(
                "role", "user",
                "content", prompt
            ))
        );

        return webClientBuilder.build()
                .post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", anthropicConfig.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> {
                    if ("js".equals(fileType)) {
                        log.info("Claude API raw response for JS file: {}", response.substring(0, Math.min(1000, response.length())));
                    }
                })
                .map(response -> extractFileContent(response, toolName))
                .doOnNext(content -> {
                    log.info("Generated {} file: {} chars", fileType, content.length());
                    if ("js".equals(fileType) && content.trim().isEmpty()) {
                        log.error("JS file is empty! This indicates Claude API failed to generate JS content.");
                    }
                });
    }

    private Mono<Map<String, String>> callClaudeWithTools(GameGenerationMetadata metadata) {
        String prompt = buildProjectGenerationPrompt(metadata);
        
        Map<String, Object> requestBody = Map.of(
            "model", anthropicConfig.getModel(),
            "max_tokens", anthropicConfig.getMaxTokens(),
            "tools", createFileCreationTools(),
            "messages", List.of(Map.of(
                "role", "user",
                "content", prompt
            ))
        );

        return webClientBuilder.build()
                .post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", anthropicConfig.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .doOnNext(errorBody -> log.error("Claude API error response: {}", errorBody))
                        .then(Mono.error(new RuntimeException("Claude API error: " + response.statusCode()))))
                .bodyToMono(String.class)
                .doOnNext(response -> log.debug("Claude API response: {}", response))
                .flatMap(response -> processToolUseResponse(response, metadata));
    }

    private List<Map<String, Object>> createFileCreationTools() {
        return List.of(
            Map.of(
                "name", "create_html_file",
                "description", "Create an HTML file for the game",
                "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "filename", Map.of("type", "string", "description", "Name of the HTML file"),
                        "content", Map.of("type", "string", "description", "Complete HTML content")
                    ),
                    "required", List.of("filename", "content")
                )
            ),
            Map.of(
                "name", "create_css_file",
                "description", "Create a CSS file for styling",
                "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "filename", Map.of("type", "string", "description", "Name of the CSS file"),
                        "content", Map.of("type", "string", "description", "CSS content")
                    ),
                    "required", List.of("filename", "content")
                )
            ),
            Map.of(
                "name", "create_js_file",
                "description", "Create a JavaScript file for game logic",
                "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "filename", Map.of("type", "string", "description", "Name of the JS file"),
                        "content", Map.of("type", "string", "description", "JavaScript content")
                    ),
                    "required", List.of("filename", "content")
                )
            ),
            Map.of(
                "name", "create_json_file",
                "description", "Create a JSON configuration file",
                "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "filename", Map.of("type", "string", "description", "Name of the JSON file"),
                        "content", Map.of("type", "string", "description", "JSON content")
                    ),
                    "required", List.of("filename", "content")
                )
            )
        );
    }

    private Mono<Map<String, String>> processToolUseResponse(String responseBody, GameGenerationMetadata metadata) {
        return Mono.fromCallable(() -> {
            Map<String, String> projectFiles = new HashMap<>();
            
            try {
                JsonNode response = objectMapper.readTree(responseBody);
                JsonNode content = response.path("content");
                
                boolean hasToolUse = false;
                
                log.info("Processing Claude response with {} content items", content.size());
                
                for (JsonNode item : content) {
                    String itemType = item.path("type").asText();
                    log.info("Processing content item of type: {}", itemType);
                    
                    if ("tool_use".equals(itemType)) {
                        hasToolUse = true;
                        String toolName = item.path("name").asText();
                        JsonNode input = item.path("input");
                        
                        String filename = input.path("filename").asText();
                        String fileContent = input.path("content").asText();
                        
                        log.info("Creating file via tool: {} - {} (content length: {})", toolName, filename, fileContent.length());
                        projectFiles.put(filename, fileContent);
                    } else if ("text".equals(itemType)) {
                        log.info("Found text content: {}", item.path("text").asText().substring(0, Math.min(100, item.path("text").asText().length())));
                    }
                }
                
                log.info("Total files created: {}, hasToolUse: {}", projectFiles.size(), hasToolUse);
                
                // If no tool use, extract text and create a single HTML file
                if (!hasToolUse) {
                    String textContent = extractTextContent(response);
                    projectFiles.put("index.html", textContent);
                }
                
                return projectFiles;
                
            } catch (Exception e) {
                log.error("Failed to process tool use response: {}", e.getMessage());
                throw new RuntimeException("Failed to process Claude response", e);
            }
        });
    }

    private String extractTextContent(JsonNode response) {
        for (JsonNode item : response.path("content")) {
            if ("text".equals(item.path("type").asText())) {
                return item.path("text").asText();
            }
        }
        return "";
    }

    private String buildProjectGenerationPrompt(GameGenerationMetadata metadata) {
        return String.format("""
            Create a complete HTML5 game project with multiple files based on these specifications:
            
            Title: %s
            Description: %s
            Game Type: %s
            Tags: %s
            
            IMPORTANT: You MUST create ALL 4 files using the provided tools. Do not stop after creating just one file.
            
            Step 1: Use create_html_file to create "index.html":
            - Basic HTML structure with <head> and <body>
            - Canvas element for the game
            - Links to "styles.css" and "game.js"
            - Game controls and UI elements
            
            Step 2: Use create_css_file to create "styles.css":
            - Modern, attractive styling
            - Responsive design for the game container
            - Canvas and button styling
            - Color scheme appropriate for the game theme
            
            Step 3: Use create_js_file to create "game.js":
            - Complete game logic with canvas rendering
            - User input handling (keyboard/mouse)
            - Game mechanics for the specified game type
            - Scoring system and game loop
            - Start/restart functionality
            
            Step 4: Use create_json_file to create "package.json":
            - Project metadata with name, version, description
            - Game information and keywords
            - Basic npm scripts
            
            You MUST use all 4 tools to create a complete, professional game project. Create each file separately using the appropriate tool.
            """, 
            metadata.getTitle(),
            metadata.getDescription(),
            metadata.getGameType() != null ? metadata.getGameType() : "HTML5 Game",
            metadata.getTags() != null ? metadata.getTags() : "game, html5"
        );
    }

    private Mono<Map<String, String>> generateSimpleProject(GameGenerationMetadata metadata) {
        return Mono.fromCallable(() -> {
            Map<String, String> projectFiles = new HashMap<>();
            
            // Create a simple single-file game as fallback
            String htmlContent = generateSimpleGameTemplate(metadata);
            projectFiles.put("index.html", htmlContent);
            
            // Add a simple CSS file
            projectFiles.put("styles.css", generateSimpleCSS());
            
            // Add a simple JS file
            projectFiles.put("game.js", generateSimpleJS());
            
            // Add package.json
            projectFiles.put("package.json", generatePackageJson(metadata));
            
            return projectFiles;
        });
    }

    private String generateSimpleGameTemplate(GameGenerationMetadata metadata) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - Generation Failed</title>
                <link rel="stylesheet" href="styles.css">
            </head>
            <body>
                <div class="game-container error-container">
                    <div class="error-banner">⚠️ AI Generation Failed</div>
                    <h1>%s</h1>
                    <p>%s</p>
                    <div class="error-message">
                        <p><strong>🤖 Claude AI was unable to generate the custom game.</strong></p>
                        <p>This is a basic fallback game. Please try generating again or contact support.</p>
                    </div>
                    <canvas id="gameCanvas" width="600" height="400"></canvas>
                    <div class="controls">
                        <button id="startButton" onclick="startGame()">Start Fallback Game</button>
                        <button id="restartButton" onclick="resetGame()">Reset</button>
                    </div>
                    <div class="info">
                        <p><strong>Type:</strong> %s (Fallback)</p>
                        <p><strong>Tags:</strong> %s</p>
                        <p><strong>Status:</strong> ❌ AI Generation Failed</p>
                    </div>
                </div>
                <script src="game.js"></script>
            </body>
            </html>
            """, 
            metadata.getTitle(),
            metadata.getTitle(),
            metadata.getDescription(),
            metadata.getGameType() != null ? metadata.getGameType() : "HTML5 Game",
            metadata.getTags() != null ? metadata.getTags() : "game, html5"
        );
    }

    private String generateSimpleCSS() {
        return """
            body {
                margin: 0;
                padding: 20px;
                font-family: Arial, sans-serif;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                display: flex;
                justify-content: center;
                align-items: center;
                min-height: 100vh;
            }
            
            .game-container {
                background: white;
                border-radius: 10px;
                padding: 30px;
                text-align: center;
                box-shadow: 0 10px 30px rgba(0,0,0,0.3);
                max-width: 800px;
            }
            
            .error-container {
                border-left: 5px solid #ff6b6b;
            }
            
            .error-banner {
                background: #ff6b6b;
                color: white;
                padding: 10px;
                margin: -30px -30px 20px -30px;
                border-radius: 10px 10px 0 0;
                font-weight: bold;
                font-size: 18px;
            }
            
            .error-message {
                background: #fff3cd;
                border: 1px solid #ffeaa7;
                border-radius: 5px;
                padding: 15px;
                margin: 20px 0;
                color: #856404;
            }
            
            .error-message p {
                margin: 5px 0;
            }
            
            canvas {
                border: 2px solid #333;
                border-radius: 5px;
                margin: 20px 0;
            }
            
            .controls {
                margin: 20px 0;
            }
            
            button {
                background: #667eea;
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 5px;
                cursor: pointer;
                margin: 5px;
                font-size: 16px;
            }
            
            button:hover {
                background: #764ba2;
            }
            
            .info {
                margin-top: 20px;
                color: #666;
            }
            """;
    }

    private String generateSimpleJS() {
        return """
            // Universal game script that works with any HTML structure
            const canvas = document.getElementById('gameCanvas');
            const ctx = canvas.getContext('2d');
            
            let gameRunning = false;
            let player = { x: 50, y: canvas ? canvas.height - 50 : 350, width: 30, height: 30, color: '#667eea' };
            let obstacles = [];
            let score = 0;
            let lives = 3;
            
            // Game state
            function drawPlayer() {
                if (!ctx) return;
                ctx.fillStyle = player.color;
                ctx.fillRect(player.x, player.y, player.width, player.height);
            }
            
            function drawObstacles() {
                if (!ctx) return;
                ctx.fillStyle = '#764ba2';
                obstacles.forEach(obstacle => {
                    ctx.fillRect(obstacle.x, obstacle.y, obstacle.width, obstacle.height);
                });
            }
            
            function updateScore() {
                // Update score in DOM if elements exist
                const scoreElement = document.getElementById('score');
                if (scoreElement) {
                    scoreElement.textContent = score;
                }
                
                const livesElement = document.getElementById('lives');
                if (livesElement) {
                    livesElement.textContent = lives;
                }
                
                // Also draw on canvas as fallback
                if (ctx) {
                    ctx.fillStyle = '#333';
                    ctx.font = '20px Arial';
                    ctx.fillText('Score: ' + score, 10, 30);
                }
            }
            
            function updateGame() {
                if (!gameRunning || !ctx) return;
                
                ctx.clearRect(0, 0, canvas.width, canvas.height);
                
                // Move obstacles
                obstacles.forEach(obstacle => obstacle.x -= 2);
                obstacles = obstacles.filter(obstacle => obstacle.x + obstacle.width > 0);
                
                // Add new obstacles
                if (Math.random() < 0.02) {
                    obstacles.push({
                        x: canvas.width,
                        y: Math.random() * (canvas.height - 50),
                        width: 20,
                        height: 50,
                    });
                }
                
                drawPlayer();
                drawObstacles();
                updateScore();
                
                score += 1;
                requestAnimationFrame(updateGame);
            }
            
            function startGame() {
                gameRunning = true;
                score = 0;
                lives = 3;
                obstacles = [];
                
                // Hide start button, show restart button
                const startBtn = document.getElementById('startButton');
                const restartBtn = document.getElementById('restartButton');
                if (startBtn) startBtn.style.display = 'none';
                if (restartBtn) restartBtn.style.display = 'inline-block';
                
                updateGame();
            }
            
            function resetGame() {
                gameRunning = false;
                score = 0;
                lives = 3;
                obstacles = [];
                
                // Show start button, hide restart button
                const startBtn = document.getElementById('startButton');
                const restartBtn = document.getElementById('restartButton');
                if (startBtn) startBtn.style.display = 'inline-block';
                if (restartBtn) restartBtn.style.display = 'none';
                
                if (ctx) {
                    ctx.clearRect(0, 0, canvas.width, canvas.height);
                    drawPlayer();
                }
                updateScore();
            }
            
            // Controls - support both arrow keys and WASD
            document.addEventListener('keydown', (e) => {
                if (!gameRunning || !canvas) return;
                
                switch(e.key.toLowerCase()) {
                    case 'arrowup':
                    case 'w':
                        player.y = Math.max(0, player.y - 20);
                        break;
                    case 'arrowdown':
                    case 's':
                        player.y = Math.min(canvas.height - player.height, player.y + 20);
                        break;
                    case 'arrowleft':
                    case 'a':
                        player.x = Math.max(0, player.x - 20);
                        break;
                    case 'arrowright':
                    case 'd':
                        player.x = Math.min(canvas.width - player.width, player.x + 20);
                        break;
                }
            });
            
            // Initialize when DOM is ready
            document.addEventListener('DOMContentLoaded', function() {
                // Set up button event listeners
                const startBtn = document.getElementById('startButton');
                const restartBtn = document.getElementById('restartButton');
                
                if (startBtn) {
                    startBtn.addEventListener('click', startGame);
                }
                if (restartBtn) {
                    restartBtn.addEventListener('click', resetGame);
                }
                
                // Initial draw
                if (ctx) {
                    drawPlayer();
                    updateScore();
                }
            });
            
            // Fallback: Also support onclick functions for compatibility
            window.startGame = startGame;
            window.resetGame = resetGame;
            """;
    }

    private String generatePackageJson(GameGenerationMetadata metadata) {
        return String.format("""
            {
              "name": "%s",
              "version": "1.0.0",
              "description": "%s",
              "main": "index.html",
              "scripts": {
                "start": "npx http-server .",
                "build": "echo 'No build process needed for static HTML game'"
              },
              "keywords": [%s],
              "author": "Unboxy Game Generator",
              "license": "MIT",
              "devDependencies": {
                "http-server": "^14.1.1"
              }
            }
            """,
            metadata.getTitle().toLowerCase().replaceAll("\\s+", "-"),
            metadata.getDescription(),
            Arrays.stream((metadata.getTags() != null ? metadata.getTags() : "game,html5").split(","))
                  .map(tag -> "\"" + tag.trim() + "\"")
                  .reduce((a, b) -> a + ", " + b)
                  .orElse("\"game\"")
        );
    }

    public Mono<String> deployProjectToS3(Map<String, String> projectFiles, String gameId) {
        String projectPath = gameId + "/";
        
        log.info("Deploying {} files to S3 for game: {}", projectFiles.size(), gameId);
        projectFiles.keySet().forEach(fileName -> log.info("File to upload: {}", fileName));
        
        // Upload each file to S3 using reactive operations
        List<Mono<String>> uploadTasks = projectFiles.entrySet().stream()
                .map(file -> {
                    String fileName = file.getKey();
                    String content = file.getValue();
                    String s3Key = projectPath + fileName;
                    String contentType = getContentType(fileName);
                    
                    log.info("Starting upload: {} (size: {} chars, type: {})", s3Key, content.length(), contentType);
                    
                    return s3Service.uploadContent(s3Key, content, contentType)
                            .doOnNext(url -> log.info("Successfully uploaded file to S3: {}", s3Key))
                            .doOnError(error -> log.error("Failed to upload file {}: {}", s3Key, error.getMessage()));
                })
                .toList();
        
        // Wait for all uploads to complete, then return the main game URL
        return Mono.when(uploadTasks)
                .then(Mono.fromCallable(() -> s3Service.getPublicUrl(projectPath + "index.html")))
                .doOnNext(url -> log.info("Game deployment complete. Main URL: {}", url));
    }

    private String getContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            default -> "text/plain";
        };
    }

    private String buildSingleFilePrompt(GameGenerationMetadata metadata, String fileType) {
        String baseInfo = String.format("""
            Game Project: %s
            Description: %s
            Game Type: %s
            Tags: %s
            """, 
            metadata.getTitle(),
            metadata.getDescription(),
            metadata.getGameType() != null ? metadata.getGameType() : "HTML5 Game",
            metadata.getTags() != null ? metadata.getTags() : "game, html5"
        );

        return switch (fileType) {
            case "html" -> baseInfo + """
                
                Create the main HTML file (index.html) for this game:
                - Complete HTML5 structure with doctype, head, and body
                - Canvas element with id="gameCanvas" that fills most of the screen
                - Link to external styles.css and game.js files
                - Minimal UI overlay with game controls positioned over the canvas
                - Control buttons: <button id="startButton" onclick="startGame()">Start Game</button>
                - Control buttons: <button id="restartButton" onclick="resetGame()">Reset</button>
                - Score display: <span id="score">0</span>
                - Lives display: <span id="lives">3</span>
                
                IMPORTANT: Use these exact element IDs so JavaScript can find them:
                - gameCanvas (canvas) - should be large and fill available space
                - startButton (start button)
                - restartButton (reset button) 
                - score (score display)
                - lives (lives display)
                
                IMPORTANT: Design for full-screen gaming experience with large canvas.
                
                Use the create_html_file tool with filename "index.html".
                """;
            case "css" -> baseInfo + """
                
                Create the CSS file (styles.css) for this game:
                - Full-screen gaming layout with canvas taking most of the viewport
                - Responsive design that works on different screen sizes
                - Canvas should fill available space (e.g., 90vw, 80vh)
                - Minimal UI overlay positioned over or around the canvas
                - Button styling with hover effects and good visibility
                - Color scheme that matches the game theme
                - Remove unnecessary margins/padding for immersive experience
                
                IMPORTANT: Design for large canvas that fills most of the screen space.
                
                Use the create_css_file tool with filename "styles.css".
                """;
            case "js" -> baseInfo + """
                
                Create the JavaScript file (game.js) for this game:
                - Complete game logic and mechanics appropriate for the specified game type
                - Canvas-based rendering system using canvas with id="gameCanvas"
                - Dynamic canvas sizing to fill available screen space
                - User input handling (keyboard/mouse) with proper event listeners
                - Game loop with proper timing using requestAnimationFrame
                - Score tracking and display updating element with id="score"
                - Lives tracking and display updating element with id="lives"
                - Start/restart functionality with these exact functions:
                  * function startGame() - starts the game
                  * function resetGame() - resets the game
                
                IMPORTANT: The HTML will have these exact elements, use these IDs:
                - Canvas: document.getElementById('gameCanvas')
                - Start button: document.getElementById('startButton') 
                - Restart button: document.getElementById('restartButton')
                - Score display: document.getElementById('score')
                - Lives display: document.getElementById('lives')
                
                IMPORTANT: Design for large canvas gameplay with scalable game elements.
                IMPORTANT: Create window.startGame and window.resetGame functions for onclick compatibility.
                
                Use the create_js_file tool with filename "game.js".
                """;
            case "json" -> baseInfo + """
                
                Create the package.json file for this game project:
                - Project metadata with name, version, and description
                - Game-related keywords and tags
                - Basic npm scripts for running the game
                - Development dependencies if needed
                
                Use the create_json_file tool with filename "package.json".
                """;
            default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
        };
    }

    private String getToolNameForFileType(String fileType) {
        return switch (fileType) {
            case "html" -> "create_html_file";
            case "css" -> "create_css_file";
            case "js" -> "create_js_file";
            case "json" -> "create_json_file";
            default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
        };
    }

    private Map<String, Object> createSingleFileTool(String fileType) {
        String toolName = getToolNameForFileType(fileType);
        String description = switch (fileType) {
            case "html" -> "Create an HTML file for the game";
            case "css" -> "Create a CSS file for styling";
            case "js" -> "Create a JavaScript file for game logic";
            case "json" -> "Create a JSON configuration file";
            default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
        };

        return Map.of(
            "name", toolName,
            "description", description,
            "input_schema", Map.of(
                "type", "object",
                "properties", Map.of(
                    "filename", Map.of("type", "string", "description", "Name of the file"),
                    "content", Map.of("type", "string", "description", "File content")
                ),
                "required", List.of("filename", "content")
            )
        );
    }

    private String extractFileContent(String responseBody, String expectedToolName) {
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode content = response.path("content");
            
            for (JsonNode item : content) {
                if ("tool_use".equals(item.path("type").asText())) {
                    String toolName = item.path("name").asText();
                    if (expectedToolName.equals(toolName)) {
                        return item.path("input").path("content").asText();
                    }
                }
            }
            
            // Fallback: extract any text content
            for (JsonNode item : content) {
                if ("text".equals(item.path("type").asText())) {
                    return item.path("text").asText();
                }
            }
            
            throw new RuntimeException("No tool use or text content found in response");
        } catch (Exception e) {
            log.error("Failed to extract file content: {}", e.getMessage());
            throw new RuntimeException("Failed to extract file content", e);
        }
    }
}