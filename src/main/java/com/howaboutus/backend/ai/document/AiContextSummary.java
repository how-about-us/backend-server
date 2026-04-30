package com.howaboutus.backend.ai.document;

import com.howaboutus.backend.common.integration.ai.dto.AiStructuredSummary;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ai_context_summaries")
public record AiContextSummary(
        @Id
        UUID roomId,
        AiStructuredSummary summary,
        String lastMessageId,
        AiSummaryStatus summaryStatus,
        String summarizingFromMessageId,
        String summarizingUntilMessageId,
        Instant summaryStartedAt,
        Instant updatedAt
) {

    public static AiContextSummary init(UUID roomId) {
        return idle(roomId, null, null);
    }

    public static AiContextSummary idle(UUID roomId, AiStructuredSummary summary, String lastMessageId) {
        return new AiContextSummary(
                roomId,
                summary,
                lastMessageId,
                AiSummaryStatus.IDLE,
                null,
                null,
                null,
                Instant.now()
        );
    }

    public AiContextSummary running(String fromMessageId, String untilMessageId) {
        return new AiContextSummary(
                roomId,
                summary,
                lastMessageId,
                AiSummaryStatus.RUNNING,
                fromMessageId,
                untilMessageId,
                Instant.now(),
                updatedAt
        );
    }
}
