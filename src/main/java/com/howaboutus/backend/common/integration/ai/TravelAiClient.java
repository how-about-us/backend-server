package com.howaboutus.backend.common.integration.ai;

import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.ai.dto.AiChatPlanRequest;
import com.howaboutus.backend.common.integration.ai.dto.AiChatPlanResponse;
import com.howaboutus.backend.common.integration.ai.dto.AiSummaryUpdateRequest;
import com.howaboutus.backend.common.integration.ai.dto.AiSummaryUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class TravelAiClient {

    private final RestClient travelAiRestClient;

    public AiChatPlanResponse chatPlan(AiChatPlanRequest request) {
        try {
            return travelAiRestClient.post()
                    .uri("/v1/ai/chat/plan")
                    .body(request)
                    .retrieve()
                    .body(AiChatPlanResponse.class);
        } catch (RestClientException exception) {
            throw new ExternalApiException(exception);
        }
    }

    public AiSummaryUpdateResponse updateSummary(AiSummaryUpdateRequest request) {
        try {
            return travelAiRestClient.post()
                    .uri("/v1/ai/context/summaries")
                    .body(request)
                    .retrieve()
                    .body(AiSummaryUpdateResponse.class);
        } catch (RestClientException exception) {
            throw new ExternalApiException(exception);
        }
    }
}
