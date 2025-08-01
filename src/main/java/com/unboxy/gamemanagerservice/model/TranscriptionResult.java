package com.unboxy.gamemanagerservice.model;

public record TranscriptionResult(
        String jobName,
        String accountId,
        String status,
        Results results
) {}
