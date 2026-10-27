package com.richreach.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.auth.dto.SignupRequest;
import com.richreach.auth.dto.TokenPair;
import com.richreach.auth.service.SignupService;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 입력 검증과 응답 형식만 확인하므로 보안 필터는 끄고 서비스는 가짜로 둔다. (공개 경로 여부는 SignupApiTest에서 확인한다.)
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    private static final String VALID_PASSWORD = "abcd1234";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupService signupService;

    private static String signupJson(String name, String phone, String loginId, String password, boolean terms) {
        return """
            {"name":"%s","phoneNumber":"%s","loginId":"%s","password":"%s","termsAgreed":%s}"""
            .formatted(name, phone, loginId, password, terms);
    }

    private ResultActions signup(String json) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertFieldRejected(String json, String field) throws Exception {
        signup(json)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"))
            .andExpect(jsonPath("$.data[?(@.field=='" + field + "')]").exists());
        verifyNoInteractions(signupService);
    }

    @Test
    @DisplayName("가입에 성공하면 201과 함께 토큰을 내려준다")
    void signupReturnsTokens() throws Exception {
        when(signupService.signup(any())).thenReturn(new TokenPair("access-value", "refresh-value", 1800));

        signup(signupJson("김리치", "01012345678", "rich@example.com", VALID_PASSWORD, true))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("access-value"))
            .andExpect(jsonPath("$.data.refreshToken").value("refresh-value"))
            .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800));
    }

    @Test
    @DisplayName("전화번호에 하이픈이 있어도 형식 검증을 통과한다")
    void hyphenatedPhoneIsAccepted() throws Exception {
        when(signupService.signup(any())).thenReturn(new TokenPair("a", "r", 1));

        signup(signupJson("김리치", "010-1234-5678", "rich@example.com", VALID_PASSWORD, true))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("비밀번호가 영문·숫자를 포함한 8자 이상이 아니면 password 필드 오류로 거부한다")
    void weakPasswordsAreRejected() throws Exception {
        for (String weak : new String[] {"abcdefgh", "12345678", "ab1", "비밀번호1234abcd", "abc 12345"}) {
            assertFieldRejected(signupJson("김리치", "01012345678", "rich@example.com", weak, true), "password");
        }
    }

    @Test
    @DisplayName("BCrypt가 처리하지 못하는 64자 초과 비밀번호는 거부한다")
    void tooLongPasswordIsRejected() throws Exception {
        assertFieldRejected(
            signupJson("김리치", "01012345678", "rich@example.com", "a1".repeat(33), true), "password");
    }

    @Test
    @DisplayName("이메일 형식이 아니면 loginId 필드 오류로 거부한다")
    void invalidEmailIsRejected() throws Exception {
        for (String invalid : new String[] {"plain", "a@b", "a b@c.com", "a@@c.com", "@c.com"}) {
            assertFieldRejected(signupJson("김리치", "01012345678", invalid, VALID_PASSWORD, true), "loginId");
        }
    }

    @Test
    @DisplayName("휴대전화 번호 형식이 아니면 phoneNumber 필드 오류로 거부한다")
    void invalidPhoneIsRejected() throws Exception {
        for (String invalid : new String[] {"02-123-4567", "0101234", "01012345678901", "abcdefghijk"}) {
            assertFieldRejected(signupJson("김리치", invalid, "rich@example.com", VALID_PASSWORD, true), "phoneNumber");
        }
    }

    @Test
    @DisplayName("이름이 비어 있으면 name 필드 오류로 거부한다")
    void blankNameIsRejected() throws Exception {
        assertFieldRejected(signupJson("  ", "01012345678", "rich@example.com", VALID_PASSWORD, true), "name");
    }

    @Test
    @DisplayName("여러 필드가 틀리면 필드별 오류를 한꺼번에 내려준다")
    void reportsEveryInvalidField() throws Exception {
        signup(signupJson("", "123", "plain", "x", true))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    @DisplayName("본문이 없거나 JSON이 아니면 400이다")
    void malformedBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
        signup("not json").andExpect(status().isBadRequest());
        verifyNoInteractions(signupService);
    }

    @Test
    @DisplayName("서비스가 던진 중복/약관 오류는 코드와 HTTP 상태로 구분해서 내려준다")
    void businessErrorsKeepTheirCodes() throws Exception {
        String json = signupJson("김리치", "01012345678", "rich@example.com", VALID_PASSWORD, true);

        // 이미 예외를 던지게 해 둔 메서드는 when(...)으로 다시 설정할 수 없어서 doThrow를 쓴다.
        doThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL)).when(signupService).signup(any());
        signup(json).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH-007"));

        doThrow(new BusinessException(ErrorCode.DUPLICATE_PHONE)).when(signupService).signup(any());
        signup(json).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH-012"));

        doThrow(new BusinessException(ErrorCode.TERMS_NOT_AGREED)).when(signupService).signup(any());
        signup(json).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-008"));
    }

    @Test
    @DisplayName("아이디 중복 확인은 사용 가능 여부를 available로 내려준다")
    void checkIdReturnsAvailability() throws Exception {
        when(signupService.isLoginIdAvailable("rich@example.com")).thenReturn(true);
        when(signupService.isLoginIdAvailable("taken@example.com")).thenReturn(false);

        mockMvc.perform(post("/api/auth/check-id").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"rich@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.available").value(true));
        mockMvc.perform(post("/api/auth/check-id").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"taken@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("아이디 중복 확인도 이메일 형식이 아니면 400이다")
    void checkIdValidatesFormat() throws Exception {
        mockMvc.perform(post("/api/auth/check-id").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"plain\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data[?(@.field=='loginId')]").exists());
        verifyNoInteractions(signupService);
    }

    @Test
    @DisplayName("요청 객체를 문자열로 찍어도 비밀번호와 전화번호는 나오지 않는다")
    void toStringHidesPassword() {
        SignupRequest request = new SignupRequest("김리치", "01012345678", "rich@example.com", VALID_PASSWORD, true);

        assertThat(request.toString()).doesNotContain(VALID_PASSWORD).doesNotContain("01012345678");
    }

}
