package com.unboxy.gamemanagerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboxy.gamemanagerservice.model.TranscribeEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.unboxy.gamemanagerservice.utils.UserUtils.USER_ID;

@Service
@RequiredArgsConstructor
public class SqsMessageService {

    private final TranscribeMetadataService transcribeMetadataService;

    private final S3Service s3Service;

    @SqsListener("snowcat-transcribe-dev")
    @SneakyThrows
    public void listen(String message) {
        System.out.println("Received SQS message: " + message);

        ObjectMapper objectMapper = new ObjectMapper();
        var transcribeEvent = objectMapper.readValue(message, TranscribeEvent.class);

        transcribeMetadataService.getTranscriptMetadata(transcribeEvent.getDetail().getTranscriptionJobName())
                .flatMap(metadata -> {
                    if (transcribeEvent.getDetail().getTranscriptionJobStatus().equals("FAILED")) {
                        return transcribeMetadataService.updateMetadata(metadata, null);
                    }

                    return Mono.deferContextual(ctx -> {
                        // FlatMap to chain the async operation correctly
                        return s3Service.getTranscribeFile(metadata)
                                .flatMap(byteBuffer -> transcribeMetadataService.updateMetadata(metadata, byteBuffer));
                    }).contextWrite(ctx -> ctx.put(USER_ID, metadata.getCreationUserInfo().id()));
                })
                .subscribe(
                        success -> System.out.println("Successfully updated metadata " + transcribeEvent.getDetail().getTranscriptionJobName()),
                        error -> System.out.println("Error updating metadata: " + error.getMessage())
                );
    }
}
