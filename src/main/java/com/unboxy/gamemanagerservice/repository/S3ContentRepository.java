package com.unboxy.gamemanagerservice.repository;

import com.unboxy.gamemanagerservice.utils.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Repository
@RequiredArgsConstructor
public class S3ContentRepository {
    private final S3AsyncClient s3Client;

    @Value("${aws.bucketName}")
    private String bucketName;

    @Value("${aws.outputPath}")
    private String outputPath;

    public Mono<String> upload(FilePart file) {
//        return Mono.just("test_file_url");
        return Mono.deferContextual(ctx -> {
            String keyName = UserUtils.getUserS3Path(ctx, outputPath) + file.filename();

            return file.content()
                    .map(dataBuffer -> {
                        ByteBuffer byteBuffer = dataBuffer.asByteBuffer();
                        DataBufferUtils.release(dataBuffer);  // Release dataBuffer to prevent memory leak
                        return byteBuffer;
                    })
                    .collectList()  // Collect all ByteBuffer into a list to compute total size and create a single ByteBuffer
                    .flatMap(byteBuffers -> {
                        int totalSize = byteBuffers.stream().mapToInt(ByteBuffer::remaining).sum();
                        ByteBuffer combined = ByteBuffer.allocate(totalSize);
                        byteBuffers.forEach(combined::put);
                        combined.flip();

                        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(keyName)
                                .contentLength((long) totalSize)
                                .build();

                        return Mono.fromFuture(() ->
                                        s3Client.putObject(putObjectRequest, AsyncRequestBody.fromByteBuffer(combined))
                                ).map(PutObjectResponse::eTag)
                                .map(etag -> String.format("https://%s.s3.amazonaws.com/%s", bucketName, keyName));
                    });
        });
    }

    public Mono<ByteBuffer> readFile(String fileName) {
        return Mono.deferContextual(ctx -> {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(UserUtils.getUserS3Path(ctx, outputPath) + fileName)
                    .build();

            return Mono.fromFuture(() ->
                    s3Client.getObject(getObjectRequest, AsyncResponseTransformer.toBytes())
            ).flatMap(getObjectResponse -> {
                ByteBuffer byteBuffer = getObjectResponse.asByteBuffer();
                return Mono.just(byteBuffer);
            });
        });
    }

    public Mono<ByteBuffer> readGameFile(String fileName) {
        String keyName = "games/" + fileName;
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        return Mono.fromFuture(() ->
                s3Client.getObject(getObjectRequest, AsyncResponseTransformer.toBytes())
        ).flatMap(getObjectResponse -> {
            ByteBuffer byteBuffer = getObjectResponse.asByteBuffer();
            return Mono.just(byteBuffer);
        });
    }

    public Mono<Boolean> deleteFile(String fileName) {
        return Mono.deferContextual(ctx -> {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(UserUtils.getUserS3Path(ctx, outputPath) + fileName)
                    .build();

            return Mono.fromFuture(() ->
                    s3Client.deleteObject(deleteObjectRequest)
            ).map(deleteObjectResponse -> {
                System.out.println("audio file deleted");
                return true;
            });
        });
    }

    public Mono<String> uploadGameContent(String fileName, String htmlContent) {
        return uploadGameFile(fileName, htmlContent, "text/html");
    }

    public Mono<String> uploadGameFile(String fileName, String content, String contentType) {
        return Mono.deferContextual(ctx -> {
            String keyName = "games/" + fileName;
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .contentType(contentType)
                    .contentLength((long) contentBytes.length)
                    .build();

            return Mono.fromFuture(() ->
                            s3Client.putObject(putObjectRequest, AsyncRequestBody.fromBytes(contentBytes))
                    ).map(PutObjectResponse::eTag)
                    .map(etag -> String.format("https://%s.s3.amazonaws.com/%s", bucketName, keyName));
        });
    }

    public Mono<Boolean> deleteGameFile(String fileName) {
        String keyName = "games/" + fileName;
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        return Mono.fromFuture(() ->
                s3Client.deleteObject(deleteObjectRequest)
        ).map(deleteObjectResponse -> true);
    }

    public String getPublicUrl(String fileName) {
        String keyName = "games/" + fileName;
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, keyName);
    }
}
