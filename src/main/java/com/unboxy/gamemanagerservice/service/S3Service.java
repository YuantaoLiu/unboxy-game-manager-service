package com.unboxy.gamemanagerservice.service;

import com.unboxy.gamemanagerservice.model.TranscribeCommonMetadata;
import com.unboxy.gamemanagerservice.repository.S3ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3ContentRepository s3ContentRepository;

    public Mono<String> uploadFileToS3(FilePart file) {
        return s3ContentRepository.upload(file);
    }

    public Mono<ByteBuffer> getTranscribeFile(TranscribeCommonMetadata metadata) {
        System.out.println("getTranscribeFile");
        return s3ContentRepository.readFile(metadata.getJobName()+".json");
    }

    public Mono<ByteBuffer> getTranscribeFile(String id) {
        return s3ContentRepository.readFile(id+".json");
    }

    public Mono<ByteBuffer> getTranscribeAudio(String fileName) {
        return s3ContentRepository.readFile(fileName);
    }

    public Mono<Boolean> deleteTranscribeFile(TranscribeCommonMetadata metadata) {
        return s3ContentRepository.deleteFile(metadata.getId()+".json")
                .flatMap(r -> {
                    System.out.println("json file deleted");
                    return s3ContentRepository.deleteFile(metadata.getContentFileName());
                });
    }

    public Mono<String> deployGameToS3(String gameId, String htmlContent) {
        String fileName = gameId + "/index.html";
        return s3ContentRepository.uploadGameContent(fileName, htmlContent);
    }

    public Mono<ByteBuffer> getGameFile(String gameId) {
        return s3ContentRepository.readGameFile(gameId + "/index.html");
    }

    public Mono<String> getGameContent(String gameId) {
        return s3ContentRepository.readGameFile(gameId + "/index.html")
                .map(byteBuffer -> {
                    byte[] bytes = new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);
                    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                });
    }

    public Mono<Boolean> deleteGameFile(String gameId) {
        return s3ContentRepository.deleteGameFile(gameId + "/index.html");
    }

    public Mono<String> uploadContent(String fileName, String content, String contentType) {
        return s3ContentRepository.uploadGameFile(fileName, content, contentType);
    }

    public String getPublicUrl(String fileName) {
        return s3ContentRepository.getPublicUrl(fileName);
    }
}
