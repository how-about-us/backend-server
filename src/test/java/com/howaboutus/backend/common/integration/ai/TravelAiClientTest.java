package com.howaboutus.backend.common.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.howaboutus.backend.common.error.ExternalApiException;
import com.howaboutus.backend.common.integration.ai.dto.AiChatContext;
import com.howaboutus.backend.common.integration.ai.dto.AiChatMessage;
import com.howaboutus.backend.common.integration.ai.dto.AiChatPlanRequest;
import com.howaboutus.backend.common.integration.ai.dto.AiChatPlanResponse;
import com.howaboutus.backend.common.integration.ai.dto.AiRoomContext;
import com.howaboutus.backend.common.integration.ai.dto.AiStructuredSummary;
import com.howaboutus.backend.common.integration.ai.dto.AiSummaryUpdateRequest;
import com.howaboutus.backend.common.integration.ai.dto.AiSummaryUpdateResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TravelAiClientTest {

    private MockRestServiceServer server;
    private TravelAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TravelAiClient(builder.build());
    }

    @Test
    @DisplayName("AI 채팅 계획 요청을 snake_case JSON으로 전송하고 응답을 역직렬화한다")
    void chatPlanPostsSnakeCaseRequestAndParsesResponse() {
        AiChatPlanRequest request = new AiChatPlanRequest(
                "room-1",
                "room-1",
                new AiChatMessage("msg-3", "user-1", "민수", "2026-04-30T01:00:00Z", "카페 추천해줘"),
                new AiRoomContext("제주 애월", null, 3, List.of(), List.of()),
                new AiChatContext(
                        new AiStructuredSummary("이전 요약", List.of(), List.of(), List.of(), List.of(), List.of(), "msg-2"),
                        List.of(new AiChatMessage("msg-3", "user-1", "민수", null, "카페 추천해줘")),
                        List.of(new AiChatMessage("msg-3", "user-1", "민수", null, "카페 추천해줘"))
                )
        );

        server.expect(requestTo("http://localhost:8000/v1/ai/chat/plan"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "team_id": "room-1",
                          "room_id": "room-1",
                          "request_message": {
                            "message_id": "msg-3",
                            "sender_id": "user-1",
                            "sender_name": "민수",
                            "sent_at": "2026-04-30T01:00:00Z",
                            "text": "카페 추천해줘"
                          },
                          "room_context": {
                            "destination": "제주 애월",
                            "participants_count": 3,
                            "bookmarked_places": [],
                            "candidate_places": []
                          },
                          "chat_context": {
                            "summary": {
                              "summary_text": "이전 요약",
                              "agreed_points": [],
                              "open_questions": [],
                              "preferences": [],
                              "constraints": [],
                              "mentioned_places": [],
                              "last_message_id": "msg-2"
                            },
                            "messages_since_last_summary": [
                              {
                                "message_id": "msg-3",
                                "sender_id": "user-1",
                                "sender_name": "민수",
                                "text": "카페 추천해줘"
                              }
                            ],
                            "recent_messages": [
                              {
                                "message_id": "msg-3",
                                "sender_id": "user-1",
                                "sender_name": "민수",
                                "text": "카페 추천해줘"
                              }
                            ]
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "intent": "place_recommendation",
                          "answer_text": "애월의 조용한 카페를 추천할게요.",
                          "recommended_places": [
                            {
                              "place_id": "google-place-1",
                              "name": "카페 봄날",
                              "reason": "바다 전망이 좋아요"
                            }
                          ],
                          "updated_summary": {
                            "summary_text": "카페 추천을 요청했다.",
                            "agreed_points": [],
                            "open_questions": [],
                            "preferences": ["조용한 카페"],
                            "constraints": [],
                            "mentioned_places": [],
                            "last_message_id": "msg-3"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiChatPlanResponse response = client.chatPlan(request);

        assertThat(response.intent()).isEqualTo("place_recommendation");
        assertThat(response.answerText()).isEqualTo("애월의 조용한 카페를 추천할게요.");
        assertThat(response.recommendedPlaces()).hasSize(1);
        assertThat(response.updatedSummary().lastMessageId()).isEqualTo("msg-3");
        server.verify();
    }

    @Test
    @DisplayName("자동 요약 요청을 전송하고 최신 요약을 반환한다")
    void updateSummaryPostsMessagesAndParsesSummary() {
        AiSummaryUpdateRequest request = new AiSummaryUpdateRequest(
                "room-1",
                "room-1",
                List.of(new AiChatMessage("msg-1", "user-1", "민수", null, "애월로 가자")),
                null
        );

        server.expect(requestTo("http://localhost:8000/v1/ai/context/summaries"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "team_id": "room-1",
                          "room_id": "room-1",
                          "messages_since_last_summary": [
                            {
                              "message_id": "msg-1",
                              "sender_id": "user-1",
                              "sender_name": "민수",
                              "text": "애월로 가자"
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "room_id": "room-1",
                          "summary": {
                            "summary_text": "애월 여행을 논의했다.",
                            "agreed_points": ["애월"],
                            "open_questions": [],
                            "preferences": [],
                            "constraints": [],
                            "mentioned_places": [],
                            "last_message_id": "msg-1"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiSummaryUpdateResponse response = client.updateSummary(request);

        assertThat(response.roomId()).isEqualTo("room-1");
        assertThat(response.summary().summaryText()).isEqualTo("애월 여행을 논의했다.");
        server.verify();
    }

    @Test
    @DisplayName("AI 서버 오류는 ExternalApiException으로 감싼다")
    void wrapsAiServerErrors() {
        AiSummaryUpdateRequest request = new AiSummaryUpdateRequest(
                "room-1",
                "room-1",
                List.of(new AiChatMessage("msg-1", "user-1", "민수", null, "애월로 가자")),
                null
        );
        server.expect(requestTo("http://localhost:8000/v1/ai/context/summaries"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.updateSummary(request))
                .isInstanceOf(ExternalApiException.class);
    }
}
