package com.unboxy.gamemanagerservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboxy.gamemanagerservice.config.AnthropicConfig;
import com.unboxy.gamemanagerservice.model.GameGenerationMetadata;
import com.unboxy.gamemanagerservice.model.GameGenerationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhaserGameGenerationService {

    private final AnthropicConfig anthropicConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;

    public Mono<Map<String, String>> generatePhaserGameProject(GameGenerationMetadata metadata) {
        return generateMultiplePhaserFiles(metadata)
                .doOnError(e -> {
                    log.error("Claude API with Phaser tools failed: {}", e.getMessage());
                });
        // Don't fallback - let the error propagate to the client
    }

    public Mono<Map<String, String>> updatePhaserGame(GameGenerationMetadata metadata, String existingGameContent, String userUpdateRequest) {
        return generateUpdatedPhaserFile(metadata, existingGameContent, userUpdateRequest)
                .doOnError(e -> {
                    log.error("Claude API update failed: {}", e.getMessage());
                });
        // Don't fallback to regeneration - let the error propagate
    }

    // New methods that return both files and AI response
    public Mono<GameGenerationResult> generatePhaserGameProjectWithResponse(GameGenerationMetadata metadata) {
        return generateMultiplePhaserFilesWithResponse(metadata)
                .doOnError(e -> {
                    log.error("Claude API with Phaser tools failed: {}", e.getMessage());
                });
        // Don't fallback - let the error propagate to the client
    }

    public Mono<GameGenerationResult> updatePhaserGameWithResponse(GameGenerationMetadata metadata, String existingGameContent, String userUpdateRequest) {
        return generateUpdatedPhaserFileWithResponse(metadata, existingGameContent, userUpdateRequest)
                .doOnError(e -> {
                    log.error("Claude API update failed: {}", e.getMessage());
                });
        // Don't fallback to regeneration - let the error propagate
    }

    private Mono<Map<String, String>> generateMultiplePhaserFiles(GameGenerationMetadata metadata) {
        // Generate single HTML file with everything embedded for Phaser.js games
        Mono<String> htmlFile = generateSinglePhaserFile(metadata, "html")
                .onErrorReturn(generateCompletePhaserGameTemplate(metadata));

        return htmlFile
                .map(htmlContent -> {
                    Map<String, String> projectFiles = new HashMap<>();
                    projectFiles.put("index.html", htmlContent);
                    
                    log.info("Generated single complete Phaser HTML file successfully");
                    return projectFiles;
                });
    }

    private Mono<String> generateSinglePhaserFile(GameGenerationMetadata metadata, String fileType) {
        String prompt = buildSinglePhaserFilePrompt(metadata, fileType);
        String toolName = getToolNameForFileType(fileType);
        
        Map<String, Object> requestBody = Map.of(
            "model", anthropicConfig.getModel(),
            "max_tokens", 8000,  // Increased limit for complete Phaser games
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
                    log.info("Claude API raw response for Phaser {} file: {}", fileType, response.substring(0, Math.min(1000, response.length())));
                })
                .map(response -> extractFileContent(response, toolName))
                .doOnNext(content -> {
                    log.info("Generated Phaser {} file: {} chars", fileType, content.length());
                    if ("js".equals(fileType) && content.trim().isEmpty()) {
                        log.error("Phaser JS file is empty! This indicates Claude API failed to generate JS content.");
                    }
                });
    }

    private Mono<Map<String, String>> generateSimplePhaserProject(GameGenerationMetadata metadata) {
        return Mono.fromCallable(() -> {
            Map<String, String> projectFiles = new HashMap<>();
            
            // Create a complete single HTML file with embedded Phaser game
            String htmlContent = generateCompletePhaserGameTemplate(metadata);
            projectFiles.put("index.html", htmlContent);
            
            return projectFiles;
        });
    }

    private String generateCompletePhaserGameTemplate(GameGenerationMetadata metadata) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - Complete Phaser Game</title>
                <script src="https://cdn.jsdelivr.net/npm/phaser@3.80.1/dist/phaser.min.js"></script>
                <style>
                    body {
                        margin: 0;
                        padding: 20px;
                        font-family: Arial, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .game-container {
                        background: rgba(255, 255, 255, 0.95);
                        border-radius: 15px;
                        padding: 20px;
                        text-align: center;
                        box-shadow: 0 15px 35px rgba(0,0,0,0.4);
                        max-width: 900px;
                    }
                    #phaser-game {
                        border: 3px solid #333;
                        border-radius: 10px;
                        overflow: hidden;
                        margin: 20px auto;
                    }
                    .game-info { margin: 10px 0; color: #333; }
                    .game-controls { margin-top: 20px; color: #666; }
                </style>
            </head>
            <body>
                <div class="game-container">
                    <div class="game-header">
                        <h1>%s</h1>
                        <p>%s</p>
                        <div class="game-info">
                            <span class="game-type">🎮 %s (Phaser.js)</span>
                            <span class="game-tags">🏷️ %s</span>
                        </div>
                    </div>
                    <div id="phaser-game"></div>
                    <div class="game-controls">
                        <div class="instructions">
                            <p><strong>Controls:</strong> Use arrow keys or WASD to move</p>
                            <p><strong>Goal:</strong> Enjoy this complete Phaser.js game!</p>
                        </div>
                    </div>
                </div>
                <script>
                    %s
                </script>
            </body>
            </html>
            """, 
            metadata.getTitle(),
            metadata.getTitle(),
            metadata.getDescription(),
            metadata.getGameType() != null ? metadata.getGameType() : "Phaser Game",
            metadata.getTags() != null ? metadata.getTags() : "game, phaser, html5",
            generateSimplePhaserJS()
        );
    }

    private String generateSimplePhaserGameTemplate(GameGenerationMetadata metadata) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - Phaser Game</title>
                <script src="https://cdn.jsdelivr.net/npm/phaser@3.80.1/dist/phaser.min.js"></script>
                <style>
                    body {
                        margin: 0;
                        padding: 20px;
                        font-family: Arial, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        min-height: 100vh;
                    }
                    .game-container {
                        background: rgba(255, 255, 255, 0.95);
                        border-radius: 15px;
                        padding: 20px;
                        text-align: center;
                        box-shadow: 0 15px 35px rgba(0,0,0,0.4);
                        max-width: 900px;
                    }
                    #phaser-game {
                        border: 3px solid #333;
                        border-radius: 10px;
                        overflow: hidden;
                        margin: 20px auto;
                    }
                    .game-info { margin: 10px 0; color: #333; }
                    .game-controls { margin-top: 20px; color: #666; }
                </style>
            </head>
            <body>
                <div class="game-container">
                    <div class="game-header">
                        <h1>%s</h1>
                        <p>%s</p>
                        <div class="game-info">
                            <span class="game-type">🎮 %s (Phaser.js)</span>
                            <span class="game-tags">🏷️ %s</span>
                        </div>
                    </div>
                    <div id="phaser-game"></div>
                    <div class="game-controls">
                        <div class="instructions">
                            <p><strong>Controls:</strong> Use arrow keys or WASD to move</p>
                            <p><strong>Goal:</strong> Enjoy this Phaser.js powered game!</p>
                        </div>
                    </div>
                </div>
                <script src="game.js"></script>
            </body>
            </html>
            """, 
            metadata.getTitle(),
            metadata.getTitle(),
            metadata.getDescription(),
            metadata.getGameType() != null ? metadata.getGameType() : "Phaser Game",
            metadata.getTags() != null ? metadata.getTags() : "game, phaser, html5"
        );
    }


    private String generateSimplePhaserJS() {
        return """
            // Phaser Game Configuration
            const config = {
                type: Phaser.AUTO,
                width: 800,
                height: 600,
                parent: 'phaser-game',
                backgroundColor: '#2c3e50',
                physics: {
                    default: 'arcade',
                    arcade: {
                        gravity: { y: 0 },
                        debug: false
                    }
                },
                scene: {
                    preload: preload,
                    create: create,
                    update: update
                }
            };
            
            // Game variables
            let player;
            let cursors;
            let wasdKeys;
            let score = 0;
            let scoreText;
            let enemies;
            let collectibles;
            let gameStarted = false;
            
            // Initialize the game
            const game = new Phaser.Game(config);
            
            function preload() {
                // Create simple colored rectangles as sprites
                this.add.graphics()
                    .fillStyle(0x3498db)
                    .fillRect(0, 0, 32, 32)
                    .generateTexture('player', 32, 32);
                
                this.add.graphics()
                    .fillStyle(0xe74c3c)
                    .fillRect(0, 0, 24, 24)
                    .generateTexture('enemy', 24, 24);
                
                this.add.graphics()
                    .fillStyle(0xf1c40f)
                    .fillCircle(12, 12, 12)
                    .generateTexture('collectible', 24, 24);
            }
            
            function create() {
                // Create player
                player = this.physics.add.sprite(100, 300, 'player');
                player.setCollideWorldBounds(true);
                player.setTint(0x3498db);
                
                // Create enemies group
                enemies = this.physics.add.group();
                
                // Create collectibles group
                collectibles = this.physics.add.group();
                
                // Spawn initial collectibles
                for (let i = 0; i < 5; i++) {
                    spawnCollectible.call(this);
                }
                
                // Create input controls
                cursors = this.input.keyboard.createCursorKeys();
                wasdKeys = this.input.keyboard.addKeys('W,S,A,D');
                
                // Create score text
                scoreText = this.add.text(16, 16, 'Score: 0', {
                    fontSize: '24px',
                    color: '#ffffff',
                    fontFamily: 'Arial'
                });
                
                // Create instructions
                this.add.text(config.width / 2, 50, 'Collect yellow items, avoid red enemies!', {
                    fontSize: '18px',
                    color: '#ffffff',
                    fontFamily: 'Arial'
                }).setOrigin(0.5);
                
                // Setup collisions
                this.physics.add.overlap(player, collectibles, collectItem, null, this);
                this.physics.add.overlap(player, enemies, hitEnemy, null, this);
                
                // Spawn enemies periodically
                this.time.addEvent({
                    delay: 2000,
                    callback: spawnEnemy,
                    callbackScope: this,
                    loop: true
                });
                
                // Spawn collectibles periodically
                this.time.addEvent({
                    delay: 3000,
                    callback: spawnCollectible,
                    callbackScope: this,
                    loop: true
                });
                
                gameStarted = true;
            }
            
            function update() {
                if (!gameStarted) return;
                
                // Player movement
                const speed = 200;
                
                if (cursors.left.isDown || wasdKeys.A.isDown) {
                    player.setVelocityX(-speed);
                } else if (cursors.right.isDown || wasdKeys.D.isDown) {
                    player.setVelocityX(speed);
                } else {
                    player.setVelocityX(0);
                }
                
                if (cursors.up.isDown || wasdKeys.W.isDown) {
                    player.setVelocityY(-speed);
                } else if (cursors.down.isDown || wasdKeys.S.isDown) {
                    player.setVelocityY(speed);
                } else {
                    player.setVelocityY(0);
                }
                
                // Update enemies - make them move toward player
                enemies.children.entries.forEach(enemy => {
                    if (enemy.active) {
                        const angle = Phaser.Math.Angle.Between(enemy.x, enemy.y, player.x, player.y);
                        enemy.setVelocity(Math.cos(angle) * 50, Math.sin(angle) * 50);
                    }
                });
                
                // Remove off-screen enemies and collectibles
                enemies.children.entries.forEach(enemy => {
                    if (enemy.x < -50 || enemy.x > config.width + 50 || 
                        enemy.y < -50 || enemy.y > config.height + 50) {
                        enemy.destroy();
                    }
                });
                
                collectibles.children.entries.forEach(collectible => {
                    if (collectible.x < -50 || collectible.x > config.width + 50 || 
                        collectible.y < -50 || collectible.y > config.height + 50) {
                        collectible.destroy();
                    }
                });
            }
            
            function spawnEnemy() {
                if (!gameStarted) return;
                
                const side = Phaser.Math.Between(0, 3);
                let x, y;
                
                switch (side) {
                    case 0: // top
                        x = Phaser.Math.Between(0, config.width);
                        y = -30;
                        break;
                    case 1: // right
                        x = config.width + 30;
                        y = Phaser.Math.Between(0, config.height);
                        break;
                    case 2: // bottom
                        x = Phaser.Math.Between(0, config.width);
                        y = config.height + 30;
                        break;
                    case 3: // left
                        x = -30;
                        y = Phaser.Math.Between(0, config.height);
                        break;
                }
                
                const enemy = enemies.create(x, y, 'enemy');
                enemy.setTint(0xe74c3c);
            }
            
            function spawnCollectible() {
                if (!gameStarted) return;
                
                const x = Phaser.Math.Between(50, config.width - 50);
                const y = Phaser.Math.Between(100, config.height - 50);
                
                const collectible = collectibles.create(x, y, 'collectible');
                collectible.setTint(0xf1c40f);
                
                // Add a gentle floating animation
                game.scene.scenes[0].tweens.add({
                    targets: collectible,
                    y: collectible.y - 10,
                    duration: 1000,
                    ease: 'Sine.easeInOut',
                    yoyo: true,
                    repeat: -1
                });
            }
            
            function collectItem(player, collectible) {
                collectible.destroy();
                score += 10;
                scoreText.setText('Score: ' + score);
                
                // Add visual feedback
                const scorePopup = game.scene.scenes[0].add.text(collectible.x, collectible.y, '+10', {
                    fontSize: '20px',
                    color: '#f1c40f',
                    fontFamily: 'Arial'
                });
                
                game.scene.scenes[0].tweens.add({
                    targets: scorePopup,
                    y: scorePopup.y - 50,
                    alpha: 0,
                    duration: 1000,
                    onComplete: () => scorePopup.destroy()
                });
            }
            
            function hitEnemy(player, enemy) {
                // Game over effect
                gameStarted = false;
                player.setTint(0xff0000);
                
                // Stop all enemies
                enemies.children.entries.forEach(e => e.setVelocity(0, 0));
                
                // Show game over text
                const gameOverText = game.scene.scenes[0].add.text(config.width / 2, config.height / 2, 
                    'Game Over!\\nFinal Score: ' + score + '\\nClick to restart', {
                    fontSize: '32px',
                    color: '#ffffff',
                    fontFamily: 'Arial',
                    align: 'center'
                }).setOrigin(0.5);
                
                // Add click to restart
                game.scene.scenes[0].input.once('pointerdown', () => {
                    game.scene.restart();
                });
            }
            
            // Fallback functions for compatibility
            window.startGame = function() {
                if (game && game.scene && game.scene.scenes[0]) {
                    game.scene.restart();
                }
            };
            
            window.resetGame = function() {
                if (game && game.scene && game.scene.scenes[0]) {
                    game.scene.restart();
                }
            };
            """;
    }

    private String generatePhaserPackageJson(GameGenerationMetadata metadata) {
        return String.format("""
            {
              "name": "%s",
              "version": "1.0.0",
              "description": "%s - A Phaser.js HTML5 game",
              "main": "index.html",
              "scripts": {
                "start": "npx http-server .",
                "build": "echo 'No build process needed for static Phaser HTML game'",
                "dev": "npx http-server . -p 8080 -o"
              },
              "keywords": [%s, "phaser", "phaser3", "html5"],
              "author": "Unboxy Game Generator",
              "license": "MIT",
              "dependencies": {
                "phaser": "^3.80.1"
              },
              "devDependencies": {
                "http-server": "^14.1.1"
              },
              "engines": {
                "node": ">=14.0.0"
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

    public Mono<String> deployPhaserProjectToS3(Map<String, String> projectFiles, String gameId) {
        String projectPath = gameId + "/";
        
        log.info("Deploying {} Phaser files to S3 for game: {}", projectFiles.size(), gameId);
        projectFiles.keySet().forEach(fileName -> log.info("Phaser file to upload: {}", fileName));
        
        // Validate that all files have content
        for (Map.Entry<String, String> file : projectFiles.entrySet()) {
            if (file.getValue() == null || file.getValue().trim().isEmpty()) {
                log.error("Refusing to upload empty file: {} for game: {}", file.getKey(), gameId);
                return Mono.error(new RuntimeException("Cannot upload empty file: " + file.getKey()));
            }
        }
        
        // Upload each file to S3 using reactive operations
        List<Mono<String>> uploadTasks = projectFiles.entrySet().stream()
                .map(file -> {
                    String fileName = file.getKey();
                    String content = file.getValue();
                    String s3Key = projectPath + fileName;
                    String contentType = getContentType(fileName);
                    
                    log.info("Starting Phaser upload: {} (size: {} chars, type: {})", s3Key, content.length(), contentType);
                    
                    return s3Service.uploadContent(s3Key, content, contentType)
                            .doOnNext(url -> log.info("Successfully uploaded Phaser file to S3: {}", s3Key))
                            .doOnError(error -> log.error("Failed to upload Phaser file {}: {}", s3Key, error.getMessage()));
                })
                .toList();
        
        // Wait for all uploads to complete, then return the main game URL
        return Mono.when(uploadTasks)
                .then(Mono.fromCallable(() -> s3Service.getPublicUrl(projectPath + "index.html")))
                .doOnNext(url -> log.info("Phaser game deployment complete. Main URL: {}", url));
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

    private String buildSinglePhaserFilePrompt(GameGenerationMetadata metadata, String fileType) {
        String baseInfo = String.format("""
            Phaser.js Game Project: %s
            Description: %s
            Game Type: %s
            Tags: %s
            """, 
            metadata.getTitle(),
            metadata.getDescription(),
            metadata.getGameType() != null ? metadata.getGameType() : "Phaser Game",
            metadata.getTags() != null ? metadata.getTags() : "game, phaser, html5"
        );

        return switch (fileType) {
            case "html" -> baseInfo + """
                
                    Create a COMPLETE, PRODUCTION-READY single HTML file for a Phaser.js 3.80.1 game with the following MANDATORY requirements:
                     ## 1. HTML STRUCTURE REQUIREMENTS:
                     - Complete HTML5 document with proper DOCTYPE declaration
                     - Include Phaser.js 3.80.1 from CDN: <script src="https://cdn.jsdelivr.net/npm/phaser@3.80.1/dist/phaser.min.js"></script>
                     - Set viewport meta tag: <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0">
                     - Remove all margins and padding from body
                     - Prevent touch scrolling and bouncing on mobile devices

                     ## 2. RESPONSIVE DESIGN REQUIREMENTS:
                     - The game canvas MUST fill 100% of the available width and height
                     - Use CSS to ensure the game container takes full viewport: width: 100vw; height: 100vh;
                     - Set body overflow to hidden to prevent scrollbars
                     - The game MUST dynamically resize when the browser window is resized
                     - Implement proper scale manager configuration in Phaser:
                       ```javascript
                       scale: {
                           mode: Phaser.Scale.RESIZE,
                           parent: 'game-container',
                           width: '100%',
                           height: '100%',
                           autoCenter: Phaser.Scale.CENTER_BOTH
                       }
                       ```

                     ## 3. GAME IMPLEMENTATION REQUIREMENTS:
                     - Create a FULLY PLAYABLE game with complete game mechanics
                     - Include AT LEAST 3 distinct game states/scenes: Menu, Game, GameOver
                     - Implement smooth animations using Phaser tweens for ALL moving objects
                     - Add particle effects for visual feedback (explosions, collectibles, etc.)
                     - Include sound effects placeholders with Web Audio API comments
                     - Implement proper game physics (Arcade or Matter.js)
                     - Add visual feedback for all player interactions (hover effects, click animations)
                     - Include a scoring system with visual score display
                     - Add progressive difficulty scaling
                     - Implement smooth transitions between game states

                     ## 4. VISUAL POLISH REQUIREMENTS:
                     - Use Phaser's built-in shape rendering for all game objects (no external images)
                     - Create visually appealing graphics with gradients and multiple colors
                     - Add background animations or parallax scrolling effects
                     - Implement screen shake effects for impacts
                     - Add trailing effects for moving objects
                     - Use easing functions for all animations (bounce, elastic, etc.)
                     - Include visual juice: squash/stretch animations, rotation effects, scaling animations

                     ## 5. GAME CONTROLS:
                     - Implement BOTH mouse/touch AND keyboard controls
                     - Add visual indicators for controls in the game
                     - Ensure controls work properly on mobile devices
                     - Include control instructions in the menu scene

                     ## 6. PERFORMANCE OPTIMIZATION:
                     - Use object pooling for frequently created/destroyed objects
                     - Implement proper game loop with consistent frame rate
                     - Add requestAnimationFrame for smooth rendering
                     - Optimize collision detection

                     ## 7. CODE STRUCTURE:
                     - Use ES6 classes for game scenes
                     - Implement proper game state management
                     - Add comprehensive comments explaining game mechanics
                     - Use meaningful variable and function names
                     - Structure code with clear separation of concerns

                     ## 8. IFRAME COMPATIBILITY:
                     - Ensure the game works perfectly when embedded in an iframe
                     - Handle focus/blur events properly
                     - Prevent any console errors or warnings
                     - Add proper error handling for all game functions

                     ## EXAMPLE STRUCTURE:
                     ```html
                     <!DOCTYPE html>
                     <html lang="en">
                     <head>
                         <meta charset="UTF-8">
                         <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0">
                         <title>Game Title</title>
                         <style>
                             * { margin: 0; padding: 0; box-sizing: border-box; }
                             body {\s
                                 overflow: hidden;\s
                                 touch-action: none;
                                 -webkit-touch-callout: none;
                                 -webkit-user-select: none;
                                 user-select: none;
                             }
                             #game-container {\s
                                 width: 100vw;\s
                                 height: 100vh;\s
                                 display: flex;
                                 justify-content: center;
                                 align-items: center;
                             }
                         </style>
                     </head>
                     <body>
                         <div id="game-container"></div>
                         <script src="https://cdn.jsdelivr.net/npm/phaser@3.80.1/dist/phaser.min.js"></script>
                         <script>
                             // Full game implementation here with all required features
                         </script>
                     </body>
                     </html>
                     ```

                     IMPORTANT: Generate a COMPLETE, WORKING game that demonstrates professional game development practices. The game should be engaging, polished, and ready for production use. Include extensive animations, visual effects, and smooth gameplay. Make it feel like a real game, not a prototype.

                     Use the create_html_file tool with filename "index.html".
                """;
            case "js" -> baseInfo + """
                
                Create the JavaScript file (game.js) for this Phaser.js game:
                - Complete Phaser.js 3.x game implementation
                - Use proper Phaser config with physics system (arcade physics recommended)
                - Implement preload(), create(), and update() functions
                - Create sprites using Phaser's graphics API or simple colored shapes
                - Add player movement with arrow keys and WASD
                - Include game mechanics appropriate for the specified game type
                - Add enemies, collectibles, or other game objects as needed
                - Implement scoring system with on-screen display
                - Add game over conditions and restart functionality
                - Use Phaser's physics system for collisions and movement
                - Target the 'phaser-game' div as the parent container
                
                IMPORTANT: Use Phaser.js 3.x syntax and features.
                IMPORTANT: Set parent: 'phaser-game' in the Phaser config.
                IMPORTANT: Create engaging gameplay with proper game mechanics.
                IMPORTANT: Include collision detection and game state management.
                
                Use the create_js_file tool with filename "game.js".
                """;
            case "json" -> baseInfo + """
                
                Create the package.json file for this Phaser.js game project:
                - Project metadata with name, version, and description mentioning Phaser.js
                - Include "phaser": "^3.80.1" in dependencies
                - Game-related keywords including "phaser", "phaser3", "html5"
                - Basic npm scripts for running and developing the game
                - Development dependencies like http-server for local testing
                
                Use the create_json_file tool with filename "package.json".
                """;
            default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
        };
    }

    private String getToolNameForFileType(String fileType) {
        return switch (fileType) {
            case "html" -> "create_html_file";
            case "js" -> "create_js_file";
            case "json" -> "create_json_file";
            default -> throw new IllegalArgumentException("Unknown file type: " + fileType);
        };
    }

    private Map<String, Object> createSingleFileTool(String fileType) {
        String toolName = getToolNameForFileType(fileType);
        String description = switch (fileType) {
            case "html" -> "Create an HTML file for the Phaser.js game";
            case "js" -> "Create a JavaScript file for Phaser.js game logic";
            case "json" -> "Create a JSON configuration file for the Phaser.js project";
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
            log.info("Extracting content for tool: {}", expectedToolName);
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode content = response.path("content");
            
            log.info("Response has {} content items", content.size());
            
            for (JsonNode item : content) {
                String itemType = item.path("type").asText();
                log.info("Processing content item of type: {}", itemType);
                
                if ("tool_use".equals(itemType)) {
                    String toolName = item.path("name").asText();
                    log.info("Found tool use: {} (expected: {})", toolName, expectedToolName);
                    if (expectedToolName.equals(toolName)) {
                        String fileContent = item.path("input").path("content").asText();
                        log.info("Extracted content length: {}", fileContent.length());
                        return fileContent;
                    }
                }
            }
            
            // Fallback: extract any text content
            for (JsonNode item : content) {
                if ("text".equals(item.path("type").asText())) {
                    String textContent = item.path("text").asText();
                    log.info("Using text fallback, content length: {}", textContent.length());
                    return textContent;
                }
            }
            
            log.error("No tool use or text content found in response. Response structure: {}", 
                response.toPrettyString().substring(0, Math.min(500, response.toPrettyString().length())));
            throw new RuntimeException("No tool use or text content found in response");
        } catch (Exception e) {
            log.error("Failed to extract Phaser file content: {}", e.getMessage());
            throw new RuntimeException("Failed to extract Phaser file content", e);
        }
    }

    private GameGenerationResult extractFileContentAndResponse(String responseBody, String expectedToolName) {
        try {
            log.info("Extracting content and AI response for tool: {}", expectedToolName);
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode content = response.path("content");
            
            log.info("Response has {} content items", content.size());
            
            String fileContent = null;
            String aiTextResponse = null;
            
            // Extract both text response and file content
            for (JsonNode item : content) {
                String itemType = item.path("type").asText();
                log.info("Processing content item of type: {}", itemType);
                
                if ("text".equals(itemType)) {
                    aiTextResponse = item.path("text").asText();
                    log.info("Extracted AI text response length: {}", aiTextResponse.length());
                } else if ("tool_use".equals(itemType)) {
                    String toolName = item.path("name").asText();
                    log.info("Found tool use: {} (expected: {})", toolName, expectedToolName);
                    if (expectedToolName.equals(toolName)) {
                        JsonNode inputNode = item.path("input");
                        log.info("Tool input structure: {}", inputNode.toString());
                        fileContent = inputNode.path("content").asText();
                        log.info("Extracted file content length: {}", fileContent.length());
                        if (fileContent.isEmpty()) {
                            log.error("Content is empty! Full tool_use item: {}", item.toPrettyString());
                        }
                    }
                }
            }
            
            // Fallback: use text content as file content if no tool use found
            if (fileContent == null && aiTextResponse != null) {
                log.info("Using text content as file content fallback");
                fileContent = aiTextResponse;
            }
            
            if (fileContent == null || fileContent.trim().isEmpty()) {
                log.error("No tool use or text content found in response, or content is empty. Response structure: {}", 
                    response.toPrettyString().substring(0, Math.min(1000, response.toPrettyString().length())));
                throw new RuntimeException("No valid content found in response - file content is empty");
            }
            
            Map<String, String> files = new HashMap<>();
            files.put("index.html", fileContent);
            
            return new GameGenerationResult(files, aiTextResponse);
            
        } catch (Exception e) {
            log.error("Failed to extract Phaser content and response: {}", e.getMessage());
            throw new RuntimeException("Failed to extract Phaser content and response", e);
        }
    }

    private Mono<Map<String, String>> generateUpdatedPhaserFile(GameGenerationMetadata metadata, String existingGameContent, String userUpdateRequest) {
        return generateUpdatedSinglePhaserFile(metadata, existingGameContent, userUpdateRequest)
                .map(htmlContent -> {
                    Map<String, String> projectFiles = new HashMap<>();
                    projectFiles.put("index.html", htmlContent);
                    
                    log.info("Generated updated Phaser HTML file successfully");
                    return projectFiles;
                });
        // Don't fallback - let the error propagate so no S3 upload happens
    }

    // New helper methods that return GameGenerationResult with AI response
    private Mono<GameGenerationResult> generateMultiplePhaserFilesWithResponse(GameGenerationMetadata metadata) {
        Mono<String> htmlFile = generateSinglePhaserFileWithResponse(metadata, "html")
                .map(result -> result.getFiles().get("index.html"));

        return htmlFile
                .map(htmlContent -> {
                    Map<String, String> projectFiles = new HashMap<>();
                    projectFiles.put("index.html", htmlContent);
                    
                    log.info("Generated Phaser HTML file successfully");
                    return new GameGenerationResult(projectFiles, "Generated a complete Phaser.js game based on your description.");
                });
    }

    private Mono<GameGenerationResult> generateSimplePhaserProjectWithResponse(GameGenerationMetadata metadata) {
        // Fallback template generation
        String templateContent = generateCompletePhaserGameTemplate(metadata);
        Map<String, String> files = new HashMap<>();
        files.put("index.html", templateContent);
        
        return Mono.just(new GameGenerationResult(files, "Generated a Phaser.js game using the built-in template due to API limitations."));
    }

    private Mono<GameGenerationResult> generateUpdatedPhaserFileWithResponse(GameGenerationMetadata metadata, String existingGameContent, String userUpdateRequest) {
        return generateUpdatedSinglePhaserFileWithResponse(metadata, existingGameContent, userUpdateRequest);
        // Don't fallback - let the error propagate so no S3 upload happens
    }

    private Mono<GameGenerationResult> generateSinglePhaserFileWithResponse(GameGenerationMetadata metadata, String fileType) {
        String prompt = buildSinglePhaserFilePrompt(metadata, fileType);
        String toolName = fileType.equals("html") ? "create_html_file" : "create_javascript_file";
        
        Map<String, Object> requestBody = Map.of(
            "model", anthropicConfig.getModel(),
            "max_tokens", 8000,
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
            .doOnNext(response -> log.info("Claude API raw response for Phaser generation: {}", 
                response.substring(0, Math.min(200, response.length()))))
            .map(response -> extractFileContentAndResponse(response, toolName));
    }

    private Mono<GameGenerationResult> generateUpdatedSinglePhaserFileWithResponse(GameGenerationMetadata metadata, String existingGameContent, String userUpdateRequest) {
        String prompt = buildUpdatePrompt(metadata, existingGameContent, userUpdateRequest);
        String toolName = "create_html_file";
        
        Map<String, Object> requestBody = Map.of(
            "model", anthropicConfig.getModel(),
            "max_tokens", 8000,  // Increased limit for updates
            "tools", List.of(createSingleFileTool("html")),
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
            .doOnNext(response -> log.info("Claude API raw response for Phaser update: {}", 
                response.substring(0, Math.min(200, response.length()))))
            .map(response -> extractFileContentAndResponse(response, toolName));
    }

    private Mono<String> generateUpdatedSinglePhaserFile(GameGenerationMetadata metadata, String existingGameContent, String userUpdateRequest) {
        String prompt = buildUpdatePrompt(metadata, existingGameContent, userUpdateRequest);
        String toolName = "create_html_file";
        
        Map<String, Object> requestBody = Map.of(
            "model", anthropicConfig.getModel(),
            "max_tokens", 8000,  // Increased limit for updates with existing content
            "tools", List.of(createSingleFileTool("html")),
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
                    log.info("Claude API raw response for Phaser update: {}", response.substring(0, Math.min(1000, response.length())));
                })
                .map(response -> extractFileContent(response, toolName))
                .doOnNext(content -> {
                    log.info("Generated updated Phaser HTML file: {} chars", content.length());
                });
    }

    private String buildUpdatePrompt(GameGenerationMetadata metadata, String existingContent, String userRequest) {
        return String.format("""
            GAME UPDATE REQUEST:
            
            Current Game: %s
            Description: %s
            Game Type: %s
            User Update Request: %s
            
            EXISTING GAME CODE:
            ```html
            %s
            ```
            
            === MODIFICATION INSTRUCTIONS ===
                        
            Analyze the existing Phaser.js game code above and implement the requested modifications while following these STRICT REQUIREMENTS:

            ## 1. PRESERVE EXISTING FUNCTIONALITY:
            - Maintain ALL existing game mechanics unless explicitly asked to change them
            - Keep the current responsive design system intact (100vw, 100vh, Scale.RESIZE)
            - Preserve the existing scene structure and transitions
            - Retain all current animations and visual effects
            - Keep the existing control schemes (keyboard + mouse/touch)

            ## 2. CODE MODIFICATION APPROACH:
            - Identify the specific sections that need modification
            - Add clear comments marking the beginning and end of modified sections:\s
              // === MODIFICATION START: [description] ===
              // === MODIFICATION END ===
            - Preserve the existing code style and structure
            - Maintain the ES6 class architecture
            - Keep all existing variable and function naming conventions

            ## 3. COMMON MODIFICATION TYPES:

            ### A. GAMEPLAY MODIFICATIONS:
            - If changing difficulty: Modify speed, spawn rates, or score multipliers
            - If adding power-ups: Create new collectible objects with tween animations
            - If modifying controls: Add to existing input handlers, don't replace them
            - If changing game rules: Update the game logic in the update() method

            ### B. VISUAL MODIFICATIONS:
            - If changing colors: Update the color values in object creation
            - If adding effects: Use Phaser tweens and particle systems
            - If modifying animations: Adjust tween configurations and durations
            - If changing UI: Update text styles and positions while maintaining responsiveness

            ### C. FEATURE ADDITIONS:
            - If adding new enemies/obstacles: Create new classes extending existing patterns
            - If implementing new mechanics: Add methods to relevant scenes
            - If adding sound: Include Web Audio API placeholders with detailed comments
            - If creating new levels: Extend the existing scene management system

            ### D. PERFORMANCE IMPROVEMENTS:
            - If optimizing: Implement object pooling without breaking existing spawning
            - If fixing lag: Add frame skipping or reduce particle counts
            - If improving collisions: Optimize physics bodies and detection zones

            ## 4. SPECIFIC REQUIREMENTS FOR MODIFICATIONS:

            - **Animation Consistency**: Any new elements MUST include smooth animations matching the existing style
            - **Responsive Integrity**: Modifications MUST work with the current responsive scaling system
            - **Visual Cohesion**: New visual elements must match the existing art style and color scheme
            - **Code Quality**: Modified code must be as clean and well-commented as the original
            - **Error Prevention**: Add try-catch blocks for any risky operations
            - **Backward Compatibility**: Ensure saved game states (if any) remain compatible

            ## 5. TESTING CHECKLIST:
            After modifications, ensure:
            - [ ] Game still fills the entire iframe/viewport
            - [ ] Responsive resizing still works properly
            - [ ] All original features remain functional
            - [ ] New features integrate seamlessly
            - [ ] No console errors or warnings
            - [ ] Performance remains smooth (60 FPS target)
            - [ ] Controls work on both desktop and mobile

            ## 6. OUTPUT REQUIREMENTS:
            - Return the COMPLETE modified HTML file
            - Include ALL original code with modifications clearly integrated
            - Maintain the single-file structure (HTML with embedded CSS and JavaScript)
            - Keep the Phaser.js CDN link unchanged
            - Ensure the game is immediately playable after modification

            ## 7. MODIFICATION EXAMPLES:

            ```javascript
            // Example: Adding a new power-up
            // === MODIFICATION START: Added speed boost power-up ===
            class SpeedBoost extends Phaser.GameObjects.Sprite {
                constructor(scene, x, y) {
                    super(scene, x, y);
                    // Implementation with animations
                    scene.tweens.add({
                        targets: this,
                        y: y - 10,
                        duration: 1000,
                        yoyo: true,
                        repeat: -1,
                        ease: 'Sine.easeInOut'
                    });
                }
            }
            // === MODIFICATION END ===

            // Example: Modifying difficulty
            // === MODIFICATION START: Increased enemy spawn rate ===
            this.enemySpawnDelay = 1000; // Was 2000
            // === MODIFICATION END ===
            ```

            ## 8. EDGE CASES TO HANDLE:
            - If the modification might break responsive scaling, provide alternative approach
            - If adding features that might impact performance, include optimization options
            - If modification conflicts with existing code, explain the conflict and provide solution
            - If request is unclear, implement the most likely interpretation with comments

            IMPORTANT: Generate the COMPLETE modified game maintaining professional quality. The modification should enhance the game while preserving all existing functionality. Make sure the game remains fully playable and polished after modifications.

            Use the create_html_file tool with filename "index.html".
            """, 
            metadata.getTitle(),
            metadata.getDescription(),
            metadata.getGameType() != null ? metadata.getGameType() : "Phaser Game",
            userRequest,
            existingContent
        );
    }
}