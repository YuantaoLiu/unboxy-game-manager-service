package com.unboxy.gamemanagerservice.repository;

import com.unboxy.gamemanagerservice.model.*;
import com.unboxy.gamemanagerservice.utils.SearchQueryUtils;
import com.unboxy.gamemanagerservice.utils.UserUtils;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class TranscribeMetadataRepository extends AbstractElasticsearchMetadataRepository<TranscribeCommonMetadata> {
    protected static final String INDEX = "transcription";

    public TranscribeMetadataRepository(OpenSearchAsyncClient client, SearchQueryUtils searchQueryUtils) {
        super(client, searchQueryUtils);
    }

    public Mono<TranscribeCommonMetadata> save(String id, TranscribeCommonMetadata metadata) {
        return super.save(id, metadata);
    }

    @Override
    protected Optional<SeqNoPrimaryTerm> getSeqNoPrimaryTerm(TranscribeCommonMetadata metadata) {
        return Optional.empty();
    }

    @Override
    protected String getIndexCoordinates() {
        return INDEX;
    }

    @Override
    protected Class<TranscribeCommonMetadata> getMetadataClassType() {
        return TranscribeCommonMetadata.class;
    }

    @Override
    protected Map<String, Float> getTextSearchFields() {
        return Map.of("audioSegments.transcript", 1.0f);
    }

    public Mono<SearchResult> findByCriteria(SearchCriteria criteria) {
        return Mono.deferContextual(ctx -> {
            SearchRequest request = toSearchRequest(criteria, UserUtils.getUserId(ctx));

            return doSearch(request)
                    .map(result -> {
                        List<TranscribeCommonMetadata> resultHits = result.hits().hits().stream().map(Hit::source).toList();
                        long total = result.hits().total().value();
                        PageImpl<TranscribeCommonMetadata> page = new PageImpl<>(resultHits, toPagable(criteria), total);

                        return new SearchResult(criteria.getSearchText(),
                                resultHits,
                                new Pagination(page.getSize(), page.getNumber(), page.getTotalPages(), page.getTotalElements()));
                    });
        });
    }

    public Mono<SearchResult> searchAudioSegments(SearchCriteria criteria) {
        return Mono.deferContextual(ctx -> {
            SearchRequest request = toSearchRequest(criteria, UserUtils.getUserId(ctx));

            return doSearch(request)
                    .map(result -> {
                        List<TranscribeCommonMetadata> resultHits = result.hits().hits().stream().map(Hit::source).toList();
                        var innerHits = result.hits().hits().stream()
                                .collect(Collectors.toMap(
                                        hit -> hit.source().getId(),  // Key: transcriptId
                                        hit -> hit.innerHits().get("audioSegments").hits().hits().stream()
                                                .map(audioSegment -> audioSegment.source().to(AudioSegment.class))
                                                .sorted(Comparator.comparing(audioSegment -> Float.valueOf(audioSegment.startTime())))
                                                .toList()
                                ));

                        for (TranscribeCommonMetadata metadata : resultHits) {
                            if (innerHits.containsKey(metadata.getId())) {
                                metadata.setAudioSegments(innerHits.get(metadata.getId()));
                            } else {
                                metadata.setAudioSegments(List.of());
                            }
                        }

                        long total = result.hits().total().value();
                        PageImpl<TranscribeCommonMetadata> page = new PageImpl<>(resultHits, toPagable(criteria), total);

                        return new SearchResult(criteria.getSearchText(),
                                resultHits,
                                new Pagination(page.getSize(), page.getNumber(), page.getTotalPages(), page.getTotalElements()));
                    });
        });
    }
}
