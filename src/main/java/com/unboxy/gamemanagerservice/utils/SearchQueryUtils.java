package com.unboxy.gamemanagerservice.utils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.unboxy.gamemanagerservice.model.LogicalOperatorType;
import com.unboxy.gamemanagerservice.model.QueryClause;
import com.unboxy.gamemanagerservice.model.SearchCriteria;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.search.InnerHits;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.util.StringUtils.hasText;

@Component
public class SearchQueryUtils {
    private final String WILDCARD = "*";
    private final String BOOST_OPERATOR = "^";
    public final static String EQALL_MIN_SHOULD_MATCH_SCRIPT = "eqall-min-should-match-script";

    private static final Set<String> nestedScopes = Set.of("audioSegments");

    public BoolQuery.Builder addMustAndMustNotFields(Map<String, List<String>> mustFieldsToQuery,
                                                     Map<String, List<String>> mustNotFieldsToQuery) {
        BoolQuery.Builder baseQueryBuilder = QueryBuilders.bool();

        appendToBuilderByFields(baseQueryBuilder, mustFieldsToQuery, true);
        appendToBuilderByFields(baseQueryBuilder, mustNotFieldsToQuery, false);
        return baseQueryBuilder;
    }

    private void appendToBuilderByFields(BoolQuery.Builder builder, Map<String, List<String>> fields, boolean isMust) {
        if (CollectionUtils.isEmpty(fields)) {
            return;
        }

        fields.forEach((key, value) -> {
            Query query;

            if (getNestedScopeIfExists(key).isPresent()) { // TODO RS-2512 -> I'm pretty sure we need to group queries for the same nested path, otherwise they will be evaluated independently which may return false hits.
                query = QueryBuilders.nested()
                        .path(StringUtils.substringBefore(key, "."))
                        .query(toTermsQuery(key, value).build().toQuery())
                        .scoreMode(ChildScoreMode.Sum)
                        .build()
                        .toQuery();
            } else {
                query = toTermsQuery(key, value).build().toQuery();
            }

            if (isMust) {
                builder.must(query);
            } else {
                builder.mustNot(query);
            }
        });
    }

    private Optional<String> getNestedScopeIfExists(String queryLhs) {
        if(queryLhs.contains(".")) {
            var lhsScope = StringUtils.substringBefore(queryLhs, ".");
            if(nestedScopes.contains(lhsScope)) {
                return Optional.of(lhsScope);
            }
        }
        return Optional.empty();
    }

    public TermsQuery.Builder toTermsQuery(String field, List<String> values) {
        List<FieldValue> fieldValues = values.stream().map(FieldValue::of).toList();

        return QueryBuilders.terms()
                .field(field)
                .terms(TermsQueryField.of(builder -> builder.value(fieldValues)));

    }

    public BoolQuery.Builder addUserIdFilter(@NonNull BoolQuery.Builder boolBuilder, @NonNull String userId) {
        boolBuilder.must(QueryBuilders.term()
                .field("creationUserInfo.id")
                .value(FieldValue.of(userId))
                .build()
                .toQuery());
        return boolBuilder;
    }

    public BoolQuery.Builder addTextSearchQuery(@NonNull BoolQuery.Builder  boolBuilder, @NonNull SearchCriteria criteria, @NonNull Map<String, Float> textSearchFields) {
        if (criteria.getSearchText() != null && criteria.getSearchText().isEmpty()) {
            boolBuilder.must(QueryBuilders.matchAll().build().toQuery());
        } else if (hasText(criteria.getSearchText())) {
            boolBuilder.must(buildQueryString(criteria.getSearchText(), textSearchFields));
        }

        return boolBuilder;
    }

    public BoolQuery.Builder addSearchCriteriaFilters(@NonNull BoolQuery.Builder boolBuilder, @NonNull SearchCriteria criteria) {
        if(!CollectionUtils.isEmpty(criteria.getQueries())) {
            var allQueries = QueryBuilders.bool();
            for(com.unboxy.gamemanagerservice.model.Query query: criteria.getQueries()) {
                Multimap<String, QueryClause> rangeClauses = ArrayListMultimap.create();
                Multimap<String, Query> nestedQueries = ArrayListMultimap.create(); //need to add all nested queries to the same sub query
                BoolQuery.Builder innerQueryBuilder = QueryBuilders.bool();
                for (QueryClause clause : query.clauses()) {
                    buildQuery(clause, innerQueryBuilder, rangeClauses, nestedQueries, query.operator());
                }

                handleAllRangeClausesForQuery(query, innerQueryBuilder, rangeClauses, nestedQueries);
                handleAllNestedClausesForQuery(query, innerQueryBuilder, nestedQueries);

                if(criteria.getQueryOperator() == LogicalOperatorType.AND) {
                    allQueries.must(innerQueryBuilder.build().toQuery());
                } else {
                    allQueries.should(innerQueryBuilder.build().toQuery());
                }
            }

            boolBuilder.must(allQueries.build().toQuery());
        }

        return boolBuilder;
    }

    private void handleAllNestedClausesForQuery(com.unboxy.gamemanagerservice.model.Query query, BoolQuery.Builder innerQueryBuilder, Multimap<String, Query> nestedQueries) {
        //Group all clauses for the same nested path into the same nested query
        nestedQueries.asMap().forEach((nestedScope, queries) -> {
            var nestedBool = QueryBuilders.bool();
            queries.forEach(nestedBuilder -> {
                if (query.operator() == LogicalOperatorType.AND) {
                    nestedBool.must(nestedBuilder);
                } else {
                    nestedBool.should(nestedBuilder);
                }
            });
            var nestedQuery = QueryBuilders.nested()
                    .path(nestedScope)
                    .query(nestedBool.build().toQuery())
                    .scoreMode(ChildScoreMode.Sum)
                    .build()
                    .toQuery();
            if (query.operator() == LogicalOperatorType.AND) {
                innerQueryBuilder.must(nestedQuery);
            } else {
                innerQueryBuilder.should(nestedQuery);
            }
        });
    }

    private void buildQuery(QueryClause queryClause, BoolQuery.Builder boolBuilder, Multimap<String, QueryClause> rangeOperation, Multimap<String, Query> nestedQueries, LogicalOperatorType op) {

        switch (queryClause.operator()) {
            case EQ:
                appendEQOperator(queryClause, boolBuilder, op, nestedQueries);
                break;
            case NOTEQ:
                appendNOTEQOperator(queryClause, boolBuilder, op, nestedQueries);
                break;
            case EQALL:
                appendEQALLOperator(queryClause, boolBuilder, op, nestedQueries);
                break;
            case IN:
                appendINOperator(queryClause, boolBuilder, op, nestedQueries);
                break;
            case EQIGNORECASE:
                appendEQIgnoreCaseOperator(queryClause, boolBuilder, op, nestedQueries);
                break;
            case GT:
            case GTE:
            case LT:
            case LTE:
                rangeOperation.put(queryClause.lhs(), queryClause);
                break;
            default:
                throw new IllegalArgumentException("Unknown operator - " + queryClause.operator());
        }
    }

    private void appendEQOperator(QueryClause queryClause, BoolQuery.Builder boolBuilder, LogicalOperatorType op, Multimap<String, Query> nestedQueries) {
        if (queryClause.rhs() == null) {
            appendExistsQuery(queryClause, boolBuilder, op, nestedQueries);
        } else if (WILDCARD.equals(queryClause.rhs())) {
            appendAnyMatchQuery(queryClause, boolBuilder, op, nestedQueries);
        } else {
            Query termQuery = QueryBuilders.term()
                    .field(queryClause.lhs())
                    .value(FieldValue.of(queryClause.rhs()))
                    .build()
                    .toQuery();
            appendToBuilder(boolBuilder, termQuery, op, queryClause, nestedQueries);
        }
    }

    private void appendToBuilder(BoolQuery.Builder builder, Query toAddBuilder, LogicalOperatorType op, QueryClause originalClause, Multimap<String, Query> nestedQueries) {
        getNestedScopeIfExists(originalClause).ifPresentOrElse(nestedScope -> nestedQueries.put(nestedScope, toAddBuilder),
                () -> {
                    switch (op) {
                        case AND:
                            builder.must(toAddBuilder);
                            break;
                        case OR:
                            builder.should(toAddBuilder);
                            break;
                        default:
                            throw new UnsupportedOperationException("Unknown op - " + op.name());
                    }
                });
    }

    private Optional<String> getNestedScopeIfExists(QueryClause queryClause) {
        return queryClause == null ? Optional.empty() : getNestedScopeIfExists(queryClause.lhs());
    }

    private void appendEQIgnoreCaseOperator(QueryClause queryClause, BoolQuery.Builder boolBuilder, LogicalOperatorType op, Multimap<String, Query> nestedQueries) {
        if (queryClause.rhs() == null) {
            appendExistsQuery(queryClause, boolBuilder, op, nestedQueries);
        } else {
            appendToBuilder(boolBuilder, toTermQuery(queryClause.lhs() + ".lowercase", queryClause.rhs()).build().toQuery(), op, queryClause, nestedQueries);
        }
    }

    public TermQuery.Builder toTermQuery(String field, String value) {
        return QueryBuilders.term()
                .field(field)
                .value(FieldValue.of(value));
    }

    private void appendNOTEQOperator(QueryClause queryClause, BoolQuery.Builder boolBuilder, LogicalOperatorType op, Multimap<String, Query> nestedQueries) {
        if (queryClause.rhs() == null) {
            Query exists = QueryBuilders.exists().field(queryClause.lhs()).build().toQuery();
            appendToBuilder(boolBuilder, exists, op, queryClause, nestedQueries);
        } else {
            BoolQuery.Builder innerBoolBuilder = QueryBuilders.bool(); // need an inner bool to act as a "not" operator
            innerBoolBuilder.mustNot(toTermQuery(queryClause.lhs(), queryClause.rhs()).build().toQuery());
            appendToBuilder(boolBuilder, innerBoolBuilder.build().toQuery(), op, queryClause, nestedQueries);
        }
    }

    private void appendINOperator(QueryClause queryClause, BoolQuery.Builder boolBuilder, LogicalOperatorType op, Multimap<String, Query> nestedQueries) {
        List<String> values =
                Stream.of(queryClause.rhs().split(","))
                        .collect(Collectors.toList());
        // Append * to match wildcard values
        values.add(WILDCARD);
        appendToBuilder(boolBuilder, toTermsQuery(queryClause.lhs(), values).build().toQuery(), op, queryClause, nestedQueries);
    }

    private void appendExistsQuery(QueryClause clause, BoolQuery.Builder boolBuilder, LogicalOperatorType op, Multimap<String, Query> nestedQueries) {
        BoolQuery.Builder innerBoolBuilder = QueryBuilders.bool();
        Query exists = QueryBuilders.exists()
                .field(clause.lhs())
                .build()
                .toQuery();
        innerBoolBuilder.mustNot(exists);
        appendToBuilder(boolBuilder, innerBoolBuilder.build().toQuery(), op, clause, nestedQueries);
    }

    private void appendAnyMatchQuery(QueryClause clause, BoolQuery.Builder boolBuilder, LogicalOperatorType op, Multimap<String, Query> nestedQueries) {
        var fieldNotExit = QueryBuilders.bool().mustNot(QueryBuilders.exists().field(clause.lhs()).build().toQuery()).build().toQuery();
        var filedEqualStar = toTermQuery(clause.lhs(), WILDCARD).build().toQuery();
        var anyMatch = QueryBuilders.bool();
        appendToBuilder(anyMatch, fieldNotExit, LogicalOperatorType.OR, clause, nestedQueries);
        appendToBuilder(anyMatch, filedEqualStar, LogicalOperatorType.OR, clause, nestedQueries);
        appendToBuilder(boolBuilder, anyMatch.build().toQuery(), op, clause, nestedQueries);
    }

    private void appendEQALLOperator(QueryClause queryClause, BoolQuery.Builder boolBuilder, LogicalOperatorType op, Multimap<String, Query> nestedQueries) {
        List<String> values = Stream.of(queryClause.rhs().split(","))
                .collect(Collectors.toList());
        TermsSetQuery.Builder termsSetQuery = new TermsSetQuery.Builder().field(queryClause.lhs()).terms(values);
        termsSetQuery.minimumShouldMatchScript(Script.of(scriptBuilder -> scriptBuilder
                .stored(storedBuilder -> storedBuilder
                        .id(EQALL_MIN_SHOULD_MATCH_SCRIPT)
                        .params(Map.of("field", JsonData.of(queryClause.lhs()))))));
        appendToBuilder(boolBuilder, termsSetQuery.build().toQuery(), op, queryClause, nestedQueries);
    }

    private void handleAllRangeClausesForQuery(com.unboxy.gamemanagerservice.model.Query query, BoolQuery.Builder innerQueryBuilder, Multimap<String, QueryClause> rangeClauses, Multimap<String, Query> nestedQueries) {
        // If this query is type AND, can combine all range operations into a single Range Query
        // Otherwise if type is OR, all range operations should be evaluated separately
        if (query.operator() == LogicalOperatorType.AND) {
            rangeClauses.asMap().forEach((lhs, clauses) -> {
                var combinedRangeQuery = toRangeQueryBuilder(lhs, clauses).toQuery();
                getNestedScopeIfExists(lhs).ifPresentOrElse(nestedScope -> nestedQueries.put(nestedScope, combinedRangeQuery), () -> innerQueryBuilder.must(combinedRangeQuery));
            });
        } else {
            rangeClauses.forEach((lhs, queryClause) -> {
                var singleRangeQuery = toRangeQueryBuilder(lhs, List.of(queryClause)).toQuery();
                getNestedScopeIfExists(lhs).ifPresentOrElse(nestedScope -> nestedQueries.put(nestedScope, singleRangeQuery), () -> innerQueryBuilder.should(singleRangeQuery));
            });
        }
    }

    private RangeQuery toRangeQueryBuilder(@NonNull String field, @NonNull Collection<QueryClause> operations) {
        RangeQuery.Builder rangeQuery = QueryBuilders.range().field(field);

        for (QueryClause operation : operations) {
            switch (operation.operator()) {
                case GT:
                    rangeQuery.gt(JsonData.of(operation.rhs()));
                    break;
                case LT:
                    rangeQuery.lt(JsonData.of(operation.rhs()));
                    break;
                case GTE:
                    rangeQuery.gte(JsonData.of(operation.rhs()));
                    break;
                case LTE:
                    rangeQuery.lte(JsonData.of(operation.rhs()));
                    break;
            }
        }

        return rangeQuery.build();
    }

    private Query buildQueryString(@NonNull String queryString, @NonNull Map<String, Float> textSearchFields) {
        Map<String, Float> nonNestedTextSearchFields = new HashMap<>();
        Map<String, Float> nestedTextSearchFields = new HashMap<>();

        textSearchFields.forEach((key, value) -> {
            if (getNestedScopeIfExists(key).isEmpty()) {
                nonNestedTextSearchFields.put(key, value);
            } else {
                nestedTextSearchFields.put(key, value);
            }
        });

        if (!nestedTextSearchFields.isEmpty()) {
            BoolQuery.Builder boolBuilder = QueryBuilders.bool();
            nestedTextSearchFields.forEach((key, value) -> {
                var stringQuery = QueryStringQuery.of(queryStringBuilder -> queryStringBuilder
                                .query(queryString)
                                .fields(convertToBoostedFieldList(Map.of(key, value))))
                        .toQuery();

                var nestedQuery = QueryBuilders.nested()
                        .path(StringUtils.substringBefore(key, "."))
                        .query(stringQuery)
                        .innerHits(new InnerHits.Builder().build())
                        .scoreMode(ChildScoreMode.Sum)
                        .build()
                        .toQuery();
                boolBuilder.should(nestedQuery);
            });



            boolBuilder.should(QueryStringQuery.of(queryStringBuilder -> queryStringBuilder
                            .query(queryString)
                            .fields(convertToBoostedFieldList(nonNestedTextSearchFields)))
                    .toQuery());

            return boolBuilder.build().toQuery();
        }

        return QueryStringQuery.of(queryStringBuilder -> queryStringBuilder
                        .query(queryString)
                        .fields(convertToBoostedFieldList(nonNestedTextSearchFields)))
                .toQuery();

    }

    private List<String> convertToBoostedFieldList(Map<String, Float> fields) {
        return fields.entrySet().stream()
                .map(entry -> entry.getKey() + BOOST_OPERATOR + entry.getValue().toString()).toList();
    }
}
