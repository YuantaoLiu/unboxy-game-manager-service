package com.unboxy.gamemanagerservice.service;

import com.unboxy.gamemanagerservice.model.GameGenerationMetadata;
import com.unboxy.gamemanagerservice.model.GameGenerationRequest;
import com.unboxy.gamemanagerservice.model.GameUpdateRequest;
import com.unboxy.gamemanagerservice.model.GameStatus;
import com.unboxy.gamemanagerservice.utils.UserUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameGenerationService {

    private final PhaserGameGenerationService phaserGameGenerationService;
    private final S3Service s3Service;

    public Mono<GameGenerationMetadata> generateGame(GameGenerationRequest request) {
        return generateGameProject(request);
    }

    public Mono<GameGenerationMetadata> updateGame(String gameId, GameUpdateRequest updateRequest, GameGenerationMetadata existingMetadata) {
        return UserUtils.getCurrentUserId()
                .flatMap(userId -> {
                    // Get existing game content from S3
                    return s3Service.getGameContent(gameId)
                            .flatMap(existingContent -> {
                                // Update metadata status
                                existingMetadata.setGameStatus(GameStatus.GENERATING.toString());
                                existingMetadata.setUpdatedAt(LocalDateTime.now());
                                
                                // Generate updated game using existing content and user request
                                return updateProjectFiles(existingMetadata, existingContent, updateRequest.getUserMessage())
                                        .flatMap(this::deployProjectToS3);
                            })
                            .onErrorResume(error -> {
                                // Check if it's an S3 retrieval error (file not found)
                                if (error.getMessage() != null && error.getMessage().contains("NoSuchKey")) {
                                    log.warn("Game file not found in S3 for {}, regenerating from scratch: {}", 
                                        gameId, error.getMessage());
                                    
                                    // If S3 file doesn't exist, regenerate the game from scratch
                                    existingMetadata.setGameStatus(GameStatus.GENERATING.toString());
                                    existingMetadata.setUpdatedAt(LocalDateTime.now());
                                    
                                    return generateProjectFiles(existingMetadata)
                                            .flatMap(this::deployProjectToS3);
                                } else {
                                    log.error("Failed to update game {}: {}", gameId, error.getMessage());
                                    // For other errors (including update generation errors), don't deploy anything
                                    return Mono.error(new RuntimeException("Failed to update game: " + error.getMessage(), error));
                                }
                            });
                });
    }

    public Mono<GameGenerationMetadata> generateGameProject(GameGenerationRequest request) {
        return UserUtils.getCurrentUserId()
                .flatMap(userId -> {
                    GameGenerationMetadata metadata = new GameGenerationMetadata();
                    // Use provided ID if available, otherwise generate new UUID
                    String gameId = (request.getId() != null && !request.getId().trim().isEmpty()) 
                        ? request.getId().trim() 
                        : UUID.randomUUID().toString();
                    metadata.setId(gameId);
                    metadata.setTitle(request.getTitle());
                    metadata.setDescription(request.getDescription());
                    metadata.setGameType(request.getGameType());
                    metadata.setTags(request.getTags());
                    metadata.setGameStatus(GameStatus.GENERATING.toString());
                    metadata.setUserId(userId);
                    metadata.setCreatedAt(LocalDateTime.now());
                    metadata.setUpdatedAt(LocalDateTime.now());

                    return generateProjectFiles(metadata)
                            .flatMap(this::deployProjectToS3);
                });
    }


    private Mono<ProjectMetadata> generateProjectFiles(GameGenerationMetadata metadata) {
        return phaserGameGenerationService.generatePhaserGameProjectWithResponse(metadata)
                .map(result -> {
                    ProjectMetadata projectMetadata = new ProjectMetadata();
                    // Store AI response in metadata
                    metadata.setAiResponse(result.getAiResponse());
                    projectMetadata.setMetadata(metadata);
                    projectMetadata.setProjectFiles(result.getFiles());
                    metadata.setGameStatus(GameStatus.DEPLOYING.toString());
                    metadata.setUpdatedAt(LocalDateTime.now());
                    return projectMetadata;
                });
    }

    private Mono<ProjectMetadata> updateProjectFiles(GameGenerationMetadata metadata, String existingContent, String userRequest) {
        return phaserGameGenerationService.updatePhaserGameWithResponse(metadata, existingContent, userRequest)
                .map(result -> {
                    ProjectMetadata projectMetadata = new ProjectMetadata();
                    // Store AI response in metadata
                    metadata.setAiResponse(result.getAiResponse());
                    projectMetadata.setMetadata(metadata);
                    projectMetadata.setProjectFiles(result.getFiles());
                    metadata.setGameStatus(GameStatus.DEPLOYING.toString());
                    metadata.setUpdatedAt(LocalDateTime.now());
                    return projectMetadata;
                });
    }

    private Mono<GameGenerationMetadata> deployProjectToS3(ProjectMetadata projectMetadata) {
        GameGenerationMetadata metadata = projectMetadata.getMetadata();
        Map<String, String> projectFiles = projectMetadata.getProjectFiles();
        
        log.info("Deploying project to S3 for game: {}", metadata.getId());
        
        return phaserGameGenerationService.deployPhaserProjectToS3(projectFiles, metadata.getId())
                .map(publicUrl -> {
                    log.info("S3 deployment completed for game: {}, URL: {}", metadata.getId(), publicUrl);
                    metadata.setPublicGameUrl(publicUrl);
                    metadata.setGameStatus(GameStatus.DEPLOYED.toString());
                    metadata.setUpdatedAt(LocalDateTime.now());
                    return metadata;
                })
                .doOnError(error -> log.error("Failed to deploy project to S3: {}", error.getMessage()));
    }

    // Helper class to hold project metadata and files
    private static class ProjectMetadata {
        private GameGenerationMetadata metadata;
        private Map<String, String> projectFiles;

        public GameGenerationMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(GameGenerationMetadata metadata) {
            this.metadata = metadata;
        }

        public Map<String, String> getProjectFiles() {
            return projectFiles;
        }

        public void setProjectFiles(Map<String, String> projectFiles) {
            this.projectFiles = projectFiles;
        }
    }
}