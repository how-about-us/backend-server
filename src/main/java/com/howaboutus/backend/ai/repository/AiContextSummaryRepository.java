package com.howaboutus.backend.ai.repository;

import com.howaboutus.backend.ai.document.AiContextSummary;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiContextSummaryRepository extends MongoRepository<AiContextSummary, UUID> {
}
