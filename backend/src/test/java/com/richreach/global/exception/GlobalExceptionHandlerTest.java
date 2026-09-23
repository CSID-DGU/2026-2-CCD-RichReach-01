package com.richreach.global.exception;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("정상 요청은 공통 성공 응답 포맷으로 내려간다")
    void ok() throws Exception {
        mockMvc.perform(get("/test/ok"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    @DisplayName("BusinessException은 ErrorCode의 HTTP 상태와 코드로 변환된다")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
            .andExpect(jsonPath("$.message").value("카드를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("@Valid 검증 실패는 400과 필드별 오류를 내려준다")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()))
            .andExpect(jsonPath("$.data[0].field").value("name"));
    }

    @Test
    @DisplayName("잘못된 JSON 본문은 400 공통 응답으로 내려간다")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405 공통 응답으로 내려간다")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/ok"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(ErrorCode.METHOD_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("존재하지 않는 경로는 404 공통 응답으로 내려간다")
    void notFound() throws Exception {
        mockMvc.perform(get("/test/unknown"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500이며 내부 정보(예외 메시지, 스택 트레이스)가 노출되지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/error"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_SERVER_ERROR.getCode()))
            .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
            .andExpect(jsonPath("$.message").value(not(containsString("secret internal detail"))))
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/ok")
        ApiResponse<String> ok() {
            return ApiResponse.ok("hello");
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "카드를 찾을 수 없습니다.");
        }

        @GetMapping("/error")
        void error() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/validate")
        ApiResponse<Void> validate(@Valid @RequestBody TestRequest request) {
            return ApiResponse.ok();
        }

    }

    record TestRequest(@NotBlank String name) {

    }

}
