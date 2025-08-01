package com.unboxy.gamemanagerservice.service;

import com.unboxy.gamemanagerservice.model.TranscribeCommonMetadata;
import com.unboxy.gamemanagerservice.utils.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.transcribe.TranscribeAsyncClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TranscribeService {
    private final TranscribeAsyncClient transcribeClient;

    private final static String TRANSCRIBE_JOB_NAME_PREFIX = "TJ-";

    @Value("${aws.bucketName}")
    private String bucketName;

    @Value("${aws.outputPath}")
    private String outputPath;

    public Mono<TranscribeCommonMetadata> transcribeFile(String mediaUri, TranscribeCommonMetadata metadata) {
//        System.out.println("transcribe file: " + mediaUri);
//        metadata.setJobName("TJ-960761eb-a898-42b7-b396-27bb03037159");
//        return Mono.just(metadata);

        return Mono.deferContextual(ctx -> {
            String transcriptionJobName = TRANSCRIBE_JOB_NAME_PREFIX + UUID.randomUUID();

            StartTranscriptionJobRequest startRequest = StartTranscriptionJobRequest.builder()
                    .transcriptionJobName(transcriptionJobName)
                    .media(Media.builder().mediaFileUri(mediaUri).build())
                    .languageCode(LanguageCode.EN_US)
                    .mediaFormat(MediaFormat.M4_A)
                    .outputBucketName(bucketName)
                    .outputKey(UserUtils.getUserS3Path(ctx, outputPath))
                    .build();

            return Mono.fromFuture(() -> transcribeClient.startTranscriptionJob(startRequest))
                    .flatMap(response -> {
                        metadata.setJobName(transcriptionJobName);
                        return Mono.just(metadata);
                    })
                    .onErrorMap(ex -> new RuntimeException("Failed to process transcription job", ex));
        });
    }
}
