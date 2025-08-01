package com.unboxy.gamemanagerservice.model;

public enum TranscribeStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED;

    public TranscribeStatus fromString(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> IN_PROGRESS;
            case "COMPLETED" -> COMPLETED;
            case "FAILED" -> FAILED;
            default -> throw new IllegalArgumentException("Invalid status: " + status);
        };
    }

    public String toString(TranscribeStatus status) {
        return switch (status) {
            case IN_PROGRESS -> "IN_PROGRESS";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        };
    }
}
