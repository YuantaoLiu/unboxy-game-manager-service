package com.unboxy.gamemanagerservice.repository;

import com.unboxy.gamemanagerservice.model.GameGenerationMetadata;
import com.unboxy.gamemanagerservice.model.SearchResult;
import com.unboxy.gamemanagerservice.utils.SearchQueryUtils;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.SortOrder;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

@Repository
public class GameMetadataRepository extends AbstractElasticsearchMetadataRepository<GameGenerationMetadata> {
    protected static final String INDEX = "games";

    public GameMetadataRepository(OpenSearchAsyncClient client, SearchQueryUtils searchQueryUtils) {
        super(client, searchQueryUtils);
    }

    @Override
    protected Optional<SeqNoPrimaryTerm> getSeqNoPrimaryTerm(GameGenerationMetadata metadata) {
        return Optional.empty();
    }

    @Override
    protected String getIndexCoordinates() {
        return INDEX;
    }

    @Override
    protected Class<GameGenerationMetadata> getMetadataClassType() {
        return GameGenerationMetadata.class;
    }

    @Override
    protected Map<String, Float> getTextSearchFields() {
        return Map.of("title", 2.0f, "description", 1.0f, "tags", 1.5f);
    }

    public Mono<GameGenerationMetadata> findByIdAndUserId(String id, String userId) {
        return findByFields(Map.of("id", id, "userId", userId), 0, 1, Map.of("createdAt", SortOrder.Desc))
                .next();
    }

    public Mono<SearchResult<GameGenerationMetadata>> findAllByUserId(String userId, int page, int size) {
        return Mono.deferContextual(ctx -> {
            return findByFields(Map.of("userId", userId), page, size, Map.of("createdAt", SortOrder.Desc))
                    .collectList()
                    .map(games -> new SearchResult<>("", games, null));
        });
    }
}