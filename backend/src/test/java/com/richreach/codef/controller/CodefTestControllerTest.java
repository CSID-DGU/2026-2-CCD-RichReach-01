package com.richreach.codef.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.codef.client.CodefTokenProvider;
import com.richreach.codef.client.CodefTokenProvider.IssuedToken;
import com.richreach.codef.dto.CodefApprovalRequest;
import com.richreach.codef.dto.CodefBillingRequest;
import com.richreach.codef.dto.CodefCardListRequest;
import com.richreach.codef.dto.CodefConnectRequest;
import com.richreach.codef.dto.CodefResultCheckRequest;
import com.richreach.codef.service.CodefTestService;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.exception.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("local")
@WebMvcTest(CodefTestController.class)
@Import({GlobalExceptionHandler.class, CodefTestControllerTest.FixedClockConfig.class})
class CodefTestControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String FULL_TOKEN = "eyJhbGciOiJSUzI1NiJ9.very-secret-payload.signature";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CodefTokenProvider tokenProvider;

    @MockitoBean
    private CodefTestService testService;

    @Test
    @DisplayName("토큰 확인 API는 토큰 전체 값을 노출하지 않고 마스킹해서 내려준다")
    void tokenIsMasked() throws Exception {
        when(tokenProvider.getToken())
            .thenReturn(new IssuedToken(FULL_TOKEN, "bearer", NOW.plusSeconds(3600), false));

        mockMvc.perform(get("/api/v1/codef-test/token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fromCache").value(false))
            .andExpect(jsonPath("$.data.tokenType").value("bearer"))
            .andExpect(jsonPath("$.data.tokenPreview").value("eyJh****(" + FULL_TOKEN.length() + "자)"))
            .andExpect(jsonPath("$.data.remainingSeconds").value(3600))
            .andExpect(jsonPath("$.data.tokenPreview").value(not(containsString("very-secret-payload"))));
    }

    @Test
    @DisplayName("토큰 발급 실패는 공통 에러 응답으로 내려간다")
    void tokenIssueFailure() throws Exception {
        when(tokenProvider.getToken())
            .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "CODEF 토큰 발급에 실패했습니다. (HTTP 401)"));

        mockMvc.perform(get("/api/v1/codef-test/token"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(ErrorCode.EXTERNAL_API_ERROR.getCode()));
    }

    @Test
    @DisplayName("계정 등록: 필수 값이 비어 있으면 400과 필드별 오류를 내려준다")
    void connectValidation() throws Exception {
        mockMvc.perform(post("/api/v1/codef-test/connected-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organization\":\"\",\"loginId\":\"\",\"loginPassword\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    @DisplayName("계정 등록: CODEF 응답을 가공하지 않고 data에 담아 돌려주며, 응답에 비밀번호가 없다")
    void connectSuccess() throws Exception {
        when(testService.createConnectedId(any(CodefConnectRequest.class)))
            .thenReturn(JSON_MAPPER.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"connectedId\":\"cid-1\"}}"));

        mockMvc.perform(post("/api/v1/codef-test/connected-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organization\":\"0301\",\"loginId\":\"my-id\",\"loginPassword\":\"my-plain-password\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.result.code").value("CF-00000"))
            .andExpect(jsonPath("$.data.data.connectedId").value("cid-1"))
            .andExpect(content -> {
                String body = content.getResponse().getContentAsString();
                org.assertj.core.api.Assertions.assertThat(body).doesNotContain("my-plain-password");
            });
    }

    @Test
    @DisplayName("보유카드 조회: connectedId가 없으면 400")
    void cardsValidation() throws Exception {
        mockMvc.perform(post("/api/v1/codef-test/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organization\":\"0301\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("보유카드 조회: 성공하면 CODEF 응답을 그대로 돌려준다")
    void cardsSuccess() throws Exception {
        when(testService.cardList(any(CodefCardListRequest.class)))
            .thenReturn(JSON_MAPPER.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[{\"resCardName\":\"카드A\"}]}"));

        mockMvc.perform(post("/api/v1/codef-test/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectedId\":\"cid-1\",\"organization\":\"0301\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[0].resCardName").value("카드A"));
    }

    @Test
    @DisplayName("승인내역 조회: 날짜가 YYYYMMDD 형식이 아니면 400과 필드별 오류를 내려준다")
    void approvalsDateValidation() throws Exception {
        mockMvc.perform(post("/api/v1/codef-test/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectedId\":\"cid-1\",\"organization\":\"0301\","
                    + "\"startDate\":\"2026-08-01\",\"endDate\":\"20260926\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()))
            .andExpect(jsonPath("$.data[0].field").value("startDate"));
    }

    @Test
    @DisplayName("승인내역 조회: 성공하면 CODEF 응답을 그대로 돌려준다")
    void approvalsSuccess() throws Exception {
        when(testService.approvals(any(CodefApprovalRequest.class)))
            .thenReturn(JSON_MAPPER.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[{\"resUsedAmount\":\"22000\"}]}"));

        mockMvc.perform(post("/api/v1/codef-test/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectedId\":\"cid-1\",\"organization\":\"0301\","
                    + "\"startDate\":\"20260801\",\"endDate\":\"20260926\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[0].resUsedAmount").value("22000"));
    }

    @Test
    @DisplayName("실적조회: 조회 기간이 YYYYMM 형식이 아니면 400과 필드별 오류를 내려준다")
    void resultCheckDateValidation() throws Exception {
        mockMvc.perform(post("/api/v1/codef-test/result-check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectedId\":\"cid-1\",\"organization\":\"0306\",\"startDate\":\"string\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data[0].field").value("startDate"));
    }

    @Test
    @DisplayName("실적조회: 조회 기간을 비워도 통과하고 CODEF 응답을 그대로 돌려준다")
    void resultCheckSuccess() throws Exception {
        when(testService.resultCheck(any(CodefResultCheckRequest.class)))
            .thenReturn(JSON_MAPPER.readTree(
                "{\"result\":{\"code\":\"CF-00000\"},\"data\":[{\"resCardBenefitList\":[]}]}"));

        mockMvc.perform(post("/api/v1/codef-test/result-check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectedId\":\"cid-1\",\"organization\":\"0306\",\"startDate\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.result.code").value("CF-00000"));
    }

    @Test
    @DisplayName("청구내역 조회: 청구년월이 YYYYMM 형식이 아니면 400과 필드별 오류를 내려준다")
    void billingDateValidation() throws Exception {
        mockMvc.perform(post("/api/v1/codef-test/billing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectedId\":\"cid-1\",\"organization\":\"0301\",\"startDate\":\"string\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data[0].field").value("startDate"));
    }

    @Test
    @DisplayName("청구내역 조회: 성공하면 CODEF 응답을 그대로 돌려준다")
    void billingSuccess() throws Exception {
        when(testService.billing(any(CodefBillingRequest.class)))
            .thenReturn(JSON_MAPPER.readTree(
                "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resTotalAmount\":\"696051\"}}"));

        mockMvc.perform(post("/api/v1/codef-test/billing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectedId\":\"cid-1\",\"organization\":\"0301\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data.resTotalAmount").value("696051"));
    }

    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

    }

}
