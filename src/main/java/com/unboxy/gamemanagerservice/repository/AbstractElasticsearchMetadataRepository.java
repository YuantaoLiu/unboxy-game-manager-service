package com.unboxy.gamemanagerservice.repository;

import com.unboxy.gamemanagerservice.model.SearchCriteria;
import com.unboxy.gamemanagerservice.utils.SearchQueryUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.*;
import org.opensearch.client.opensearch._types.OpType;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.get.GetResult;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractElasticsearchMetadataRepository<T> implements MetadataRepository<T> {
    private final static int DEFAULT_PAGE_NUMBER = 0;
    private final static int DEFAULT_PAGE_SIZE = 20;
    protected final static int DEFAULT_MAX_RESULT = 10_000;
    protected final static String ES_AGGREGATE_TOTAL = "total";
    protected final static String ES_CREATION_DATE_TIME = "creationDateTime";

    protected final OpenSearchAsyncClient client;
    protected final SearchQueryUtils searchQueryUtils;

    @Override
    public Mono<T> save(String id, T metadata) {
        return sendIndexRequest(id, metadata, true);
    }

    @Override
    public Mono<T> update(String id, T metadata) {
        return sendIndexRequest(id, metadata, false);
    }

    @Override
    public Flux<T> findByFields(Map<String, String> fieldValues, int page, int size, Map<String, SortOrder> sortFields) {
        return Flux.deferContextual(ctx -> {
            SearchRequest.Builder requestBuilder = newSearchRequestBuilder()
                    .query(buildQueryByFields(fieldValues, ctx))
                    .from(page)
                    .size(size);

            if (sortFields != null && !sortFields.isEmpty()) {
                sortFields.forEach((field, order) -> {
                    SortOptions options = SortOptions.of(sortOptionBuilder -> sortOptionBuilder.field(FieldSort.of(fieldSortBuilder -> fieldSortBuilder.field(field).order(order))));
                    requestBuilder.sort(options);
                });
            }

            SearchRequest request = requestBuilder.build();

            return doSearch(request)
                    .flatMapIterable(searchResult -> searchResult.hits().hits().stream()
                            .map(Hit::source)
                            .toList());
        });
    }

    @Override
    public Mono<String> deleteById(String id) {
        return doDelete(id)
                .thenReturn(id);
    }

    @SneakyThrows
    private Mono<DeleteResponse> doDelete(String id) {
        return Mono.fromFuture(client.delete(DeleteRequest.of(d -> d.id(id).index(getIndexCoordinates()))));
    }

    protected Mono<T> sendIndexRequest(String id, T metadata, boolean isCreate) {
        var indexRequestBuilder = newIndexRequestBuilder();
        indexRequestBuilder.opType(isCreate ? OpType.Create : OpType.Index);

        setSeqNoPrimaryTermToIndexRequest(indexRequestBuilder, metadata);

        indexRequestBuilder.document(metadata)
                .id(id);

        return doIndex(indexRequestBuilder.build())
                .thenReturn(metadata)
                .onErrorMap(throwable -> {
                    OpenSearchException exception = (OpenSearchException) throwable;
                    if (exception.status() == HttpStatus.CONFLICT.value()) {
                        throw new ConcurrencyFailureException("Artifact ID " + id + " already exist", exception);
                    }
                    return throwable;
                });

    }

    IndexRequest.Builder<T> newIndexRequestBuilder() {
        return new IndexRequest.Builder<T>()
                .index(getIndexCoordinates());
    }

    @SneakyThrows
    protected Mono<IndexResponse> doIndex(IndexRequest<T> indexRequest) {
        return Mono.fromFuture(client.index(indexRequest));
    }

    private void setSeqNoPrimaryTermToIndexRequest(IndexRequest.Builder<T> indexRequestBuilder, T metadata) {
        getSeqNoPrimaryTerm(metadata).ifPresent(seqNoPrimaryTerm -> {
            indexRequestBuilder.ifPrimaryTerm(seqNoPrimaryTerm.primaryTerm());
            indexRequestBuilder.ifSeqNo(seqNoPrimaryTerm.sequenceNumber());
        });
    }

    protected abstract Optional<SeqNoPrimaryTerm> getSeqNoPrimaryTerm(T metadata);

    protected abstract String getIndexCoordinates();

    SearchRequest.Builder newSearchRequestBuilder() {
        return new SearchRequest.Builder()
                .index(getIndexCoordinates());
    }

    private Query buildQueryByFields(Map<String, String> fieldValues, ContextView ctx) {
        var valuesAsMap = fieldValues.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Collections.singletonList(entry.getValue())));

        var baseQuery = getBaseMatchQuery(ctx, valuesAsMap, Collections.emptyMap());

        return baseQuery.build().toQuery();
    }

    protected BoolQuery.Builder getBaseMatchQuery(ContextView ctx,
                                                  Map<String, List<String>> mustFieldsToQuery,
                                                  Map<String, List<String>> mustNotFieldsToQuery) {
        return searchQueryUtils.addMustAndMustNotFields(mustFieldsToQuery, mustNotFieldsToQuery);
    }

    @SneakyThrows
    protected Mono<SearchResponse<T>> doSearch(SearchRequest request) {
        return Mono.fromFuture(client.search(request, getMetadataClassType()));
    }

    protected abstract Class<T> getMetadataClassType();

    protected abstract Map<String, Float> getTextSearchFields();

    protected SearchRequest toSearchRequest(SearchCriteria criteria, String userId) {
        BoolQuery.Builder boolBuilder = QueryBuilders.bool();

        boolBuilder = searchQueryUtils.addTextSearchQuery(boolBuilder, criteria, getTextSearchFields());
        boolBuilder = searchQueryUtils.addSearchCriteriaFilters(boolBuilder, criteria);
        boolBuilder = searchQueryUtils.addUserIdFilter(boolBuilder, userId);

        Pageable pageable = toPagable(criteria);

        return newSearchRequestBuilder()
                .sort(toSortOptions(getSortFields()))
                .query(boolBuilder.build().toQuery())
                .size(pageable.getPageSize())
                .from(Math.toIntExact(pageable.getOffset()))
                .build();
    }

    protected Pageable toPagable(SearchCriteria criteria) {
        return Pageable.ofSize(criteria.getPageSize() == null ? DEFAULT_PAGE_SIZE : criteria.getPageSize())
                .withPage(criteria.getPageNumber() == null ? DEFAULT_PAGE_NUMBER : criteria.getPageNumber());
    }

    protected List<SortOptions> toSortOptions(List<Pair<String, SortOrder>> sorts) {
        return sorts
                .stream()
                .map(entry -> SortOptions.of(so -> so.field(SortOptionsBuilders.field()
                        .field(entry.getKey())
                        .order(entry.getValue())
                        .build())))
                .collect(Collectors.toList());
    }

    protected List<Pair<String, SortOrder>> getSortFields() {
        return List.of(Pair.of(ES_CREATION_DATE_TIME, SortOrder.Desc));
    }

    @Override
    public Mono<T> findById(String id) {
        return Mono.deferContextual(ctx -> doGet(GetRequest.of(builder -> builder.id(id).index(getIndexCoordinates())))
                .switchIfEmpty(Mono.error(new RuntimeException("The requested resource does not exist: " + getMetadataClassType().getName() + " : " + id))));
    }

    @SneakyThrows
    private Mono<T> doGet(GetRequest getRequest) {
        return Mono.fromFuture(client.get(getRequest, getMetadataClassType()))
                .filter(response -> response.found() && response.source() != null)
                .onErrorResume(e -> {
                    // When using ApacheHttpClient5Transport (local mode) not-found will return a GetResponse with found = false
                    // When using AwsSdk2Transport (deployed) not-found will generate an exception.  We should treat it the same as found = false
                    if(e instanceof OpenSearchException && ((OpenSearchException) e).status() == HttpStatus.NOT_FOUND.value()) {
                        return Mono.empty();
                    }
                    return Mono.error(e);
                })
                .mapNotNull(GetResult::source);
    }
}
