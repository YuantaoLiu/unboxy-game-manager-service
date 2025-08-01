package com.unboxy.gamemanagerservice.service;

import com.unboxy.gamemanagerservice.model.SearchCriteria;
import com.unboxy.gamemanagerservice.model.SearchResult;
import com.unboxy.gamemanagerservice.repository.TranscribeMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final TranscribeMetadataRepository transcribeMetadataRepository;

    public Mono<SearchResult> search(SearchCriteria criteria) {
        return transcribeMetadataRepository.searchAudioSegments(criteria);
    }
}
