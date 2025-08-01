package com.unboxy.gamemanagerservice.repository;

import org.opensearch.client.opensearch._types.SortOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface MetadataRepository<T> {
    Mono<T> save(String id, T metadata);

    Mono<T> update(String id, T metadata);

    Flux<T> findByFields(Map<String, String> fields, int page, int size, Map<String, SortOrder> sortFields);

    /**
     * Find metadata by id
     * @param id id
     * @return metadata
     */
    Mono<T> findById(String id);

    Mono<String> deleteById(String id);
}
