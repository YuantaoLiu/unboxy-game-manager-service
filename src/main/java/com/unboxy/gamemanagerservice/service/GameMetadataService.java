package com.unboxy.gamemanagerservice.service;

import com.unboxy.gamemanagerservice.model.GameGenerationMetadata;
import com.unboxy.gamemanagerservice.model.GameGenerationRequest;
import com.unboxy.gamemanagerservice.model.SearchResult;
import com.unboxy.gamemanagerservice.repository.GameMetadataRepository;
import com.unboxy.gamemanagerservice.utils.UserUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.SortOrder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameMetadataService {

    private final GameMetadataRepository gameMetadataRepository;

    public Mono<GameGenerationMetadata> createMetadata(GameGenerationMetadata metadata) {
        return gameMetadataRepository.save(metadata.getId(), metadata);
    }

    public Mono<GameGenerationMetadata> updateMetadata(String id, GameGenerationRequest request) {
        return UserUtils.getCurrentUserId()
                .flatMap(userId -> gameMetadataRepository.findByIdAndUserId(id, userId))
                .flatMap(existingMetadata -> {
                    existingMetadata.setTitle(request.getTitle());
                    existingMetadata.setDescription(request.getDescription());
                    existingMetadata.setGameType(request.getGameType());
                    existingMetadata.setTags(request.getTags());
                    return gameMetadataRepository.update(existingMetadata.getId(), existingMetadata);
                });
    }

    public Mono<GameGenerationMetadata> updateMetadata(GameGenerationMetadata metadata) {
        return gameMetadataRepository.update(metadata.getId(), metadata);
    }

    public Mono<GameGenerationMetadata> getGameMetadata(String id) {
        return UserUtils.getCurrentUserId()
                .flatMap(userId -> gameMetadataRepository.findByIdAndUserId(id, userId));
    }

    public Mono<SearchResult<GameGenerationMetadata>> getAllUserGames(int page, int size) {
        return UserUtils.getCurrentUserId()
                .flatMap(userId -> gameMetadataRepository.findAllByUserId(userId, page, size));
    }

    public Mono<Boolean> deleteGameMetadata(String id) {
        return UserUtils.getCurrentUserId()
                .flatMap(userId -> gameMetadataRepository.findByIdAndUserId(id, userId))
                .flatMap(metadata -> gameMetadataRepository.deleteById(id))
                .map(result -> true);
    }

    public Mono<SearchResult<GameGenerationMetadata>> getAllGamesPublic(int page, int size) {
        return gameMetadataRepository.findByFields(Map.of(), page, size, Map.of("createdAt", SortOrder.Desc))
                .collectList()
                .map(games -> new SearchResult<>("", games, null));
    }

    public Mono<GameGenerationMetadata> getGameMetadataPublic(String id) {
        return gameMetadataRepository.findById(id);
    }

    public Mono<SearchResult<GameGenerationMetadata>> getGamesByUserId(String userId, int page, int size) {
        return gameMetadataRepository.findAllByUserId(userId, page, size);
    }
}