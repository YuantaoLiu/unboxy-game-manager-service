package com.unboxy.gamemanagerservice.controller;

import com.unboxy.gamemanagerservice.model.GameGenerationMetadata;
import com.unboxy.gamemanagerservice.model.GameGenerationRequest;
import com.unboxy.gamemanagerservice.model.GameUpdateRequest;
import com.unboxy.gamemanagerservice.model.SearchCriteria;
import com.unboxy.gamemanagerservice.model.SearchResult;
import com.unboxy.gamemanagerservice.service.GameGenerationService;
import com.unboxy.gamemanagerservice.service.GameMetadataService;
import com.unboxy.gamemanagerservice.service.S3Service;
import com.unboxy.gamemanagerservice.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

@org.springframework.web.bind.annotation.RestController
@RequiredArgsConstructor
public class RestController {

    private final S3Service s3Service;

    private final GameGenerationService gameGenerationService;

    private final GameMetadataService gameMetadataService;

    private final SearchService searchService;

    private final OpenSearchAsyncClient openSearchAsyncClient;

    @GetMapping("/version")
    public Mono<String> version() {
        return Mono.fromCallable(() -> {
                    try {
                        return openSearchAsyncClient.cluster().health(HealthRequest.of(h -> h)).get();
                    } catch (Exception e) {
                        throw new RuntimeException("OpenSearch connection failed", e);
                    }
                })
                .map(response -> "0.1 - OpenSearch Status: " + response.status().jsonValue() + 
                     " - Cluster: " + response.clusterName())
                .onErrorReturn("0.1 - OpenSearch Error: Connection failed");
    }

    @PostMapping("/games/generate")
    public Mono<ResponseEntity<GameGenerationMetadata>> generateGame(@RequestBody GameGenerationRequest request) {
        return gameGenerationService.generateGame(request)
                .flatMap(gameMetadataService::createMetadata)
                .map(metadata -> ResponseEntity.ok().body(metadata));
    }

    @GetMapping("/games")
    public Mono<ResponseEntity<SearchResult<GameGenerationMetadata>>> getAllGames(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        return gameMetadataService.getAllGamesPublic(page, size)
                .map(games -> ResponseEntity.ok().body(games));
    }

    @GetMapping("/games/{id}")
    public Mono<ResponseEntity<GameGenerationMetadata>> getGameMetadata(@PathVariable String id) {
        return gameMetadataService.getGameMetadataPublic(id)
                .map(metadata -> ResponseEntity.ok().body(metadata));
    }

    @GetMapping("/games/{id}/play")
    public Mono<ResponseEntity<ByteBuffer>> playGame(@PathVariable String id) {
        return s3Service.getGameFile(id)
                .map(byteBuffer -> ResponseEntity.ok()
                        .header("Content-Type", "text/html")
                        .body(byteBuffer));
    }

    @GetMapping("/games/user/{userId}")
    public Mono<ResponseEntity<SearchResult<GameGenerationMetadata>>> getGamesByUserId(
            @PathVariable String userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        return gameMetadataService.getGamesByUserId(userId, page, size)
                .map(games -> ResponseEntity.ok().body(games));
    }

    @PutMapping("/games/{id}")
    public Mono<ResponseEntity<GameGenerationMetadata>> updateGameMetadata(@PathVariable String id, @RequestBody GameGenerationRequest request) {
        return gameMetadataService.updateMetadata(id, request)
                .map(updatedMetadata -> ResponseEntity.ok().body(updatedMetadata));
    }

    @PutMapping("/games/{id}/generate")
    public Mono<ResponseEntity<GameGenerationMetadata>> updateGame(@PathVariable String id, @RequestBody GameUpdateRequest updateRequest) {
        return gameMetadataService.getGameMetadata(id)
                .flatMap(existingMetadata -> 
                    gameGenerationService.updateGame(id, updateRequest, existingMetadata)
                            .flatMap(updatedMetadata -> gameMetadataService.updateMetadata(updatedMetadata)))
                .map(updatedMetadata -> ResponseEntity.ok().body(updatedMetadata));
    }

    @PostMapping("/games/search")
    public Mono<ResponseEntity<SearchResult>> searchGames(@RequestBody SearchCriteria searchCriteria) {
        return searchService.search(searchCriteria)
                .map(searchResult -> ResponseEntity.ok().body(searchResult));
    }

    @DeleteMapping("/games/{id}")
    public Mono<ResponseEntity<Void>> deleteGame(@PathVariable String id) {
        return gameMetadataService.getGameMetadata(id)
                .flatMap(metadata -> s3Service.deleteGameFile(metadata.getId()))
                .flatMap(success -> gameMetadataService.deleteGameMetadata(id))
                .map(success -> ResponseEntity.ok().build());
    }
}
