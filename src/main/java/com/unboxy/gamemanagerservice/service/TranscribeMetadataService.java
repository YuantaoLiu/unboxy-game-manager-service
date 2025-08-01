package com.unboxy.gamemanagerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboxy.gamemanagerservice.model.*;
import com.unboxy.gamemanagerservice.repository.TranscribeMetadataRepository;
import com.unboxy.gamemanagerservice.utils.MetadataUtils;
import com.unboxy.gamemanagerservice.utils.UserUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TranscribeMetadataService {
    private final TranscribeMetadataRepository transcribeMetadataRepository;
    private final MetadataUtils metadataUtils;

    @SneakyThrows
    public Mono<TranscribeCommonMetadata> updateMetadata(TranscribeCommonMetadata metadata, ByteBuffer byteBuffer) {
        System.out.println("update metadata");

        if (byteBuffer != null) {
            metadata.setTranscribeStatus(TranscribeStatus.COMPLETED.toString());

            ObjectMapper objectMapper = new ObjectMapper();

            // Convert ByteBuffer to byte array
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            var transcribeResult = objectMapper.readValue(bytes, TranscriptionResult.class);
            metadata.setTranscript(transcribeResult.results().transcripts().stream()
                    .map(Transcript::transcript)
                    .reduce("", (accumulator, current) -> accumulator + current));
            metadata.setAudioSegments(transcribeResult.results().audioSegments());
            // this is a temporary solution to get the summary
            // will add aws comprehend to get the summary
            metadata.setSummary(transcribeResult.results().audioSegments().get(0).transcript());
        } else {
            metadata.setTranscribeStatus(TranscribeStatus.FAILED.toString());
        }

        return transcribeMetadataRepository.update(metadata.getId(), metadata);
    }

    public Mono<TranscribeCommonMetadata> updateMetadata(String id, TranscribeCreateUpdateMetadata updateMetadata) {
        return transcribeMetadataRepository.findById(id)
                .map(metadata -> {
                    metadataUtils.copy(updateMetadata, metadata);
                    return metadata;
                })
                .flatMap(metadata -> transcribeMetadataRepository.update(metadata.getId(), metadata));
    }

    public Mono<TranscribeCommonMetadata> createMetadata(TranscribeCommonMetadata metadata) {
        return Mono.deferContextual(ctx -> {
            var userInfo = new UserInfo(UserUtils.getUserId(ctx), "", "", "");
            metadata.setId(metadata.getJobName());
            setCreationRelatedFields(metadata, userInfo);

            return transcribeMetadataRepository.save(metadata.getId(), metadata);
        });
    }

    private TranscribeCommonMetadata setCreationRelatedFields(TranscribeCommonMetadata metadata, UserInfo userInfo) {
        metadata.setCreationDateTime(OffsetDateTime.now());
        metadata.setCreationUserInfo(userInfo);
        return metadata;
    }

    public Mono<SearchResult> getAllUserTranscripts(int pageNumber, int pageSize) {
        return Mono.deferContextual(ctx -> {
            String userId = ctx.get("userId");

            System.out.println("userId: " + userId);

            var queryClause = new QueryClause("creationUserInfo.id", SearchOperatorType.EQ, userId);
            var query = new Query(LogicalOperatorType.AND, List.of(queryClause));
            SearchCriteria searchCriteria = new SearchCriteria();
            searchCriteria.setQueries(List.of(query));
            searchCriteria.setPageNumber(pageNumber);
            searchCriteria.setPageSize(pageSize);

            return transcribeMetadataRepository.findByCriteria(searchCriteria);
        });
    }

    public Mono<TranscribeCommonMetadata> getTranscriptMetadata(String id) {
        return transcribeMetadataRepository.findById(id);
    }

    public Mono<String> deleteTranscriptMetadata(String id) {
        return transcribeMetadataRepository.deleteById(id);
    }
}
