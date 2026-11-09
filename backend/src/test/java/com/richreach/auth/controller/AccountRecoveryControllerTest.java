package com.richreach.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.auth.dto.FindEmailRequest;
import com.richreach.auth.dto.FindEmailResponse;
import com.richreach.auth.dto.PasswordCodeResponse;
import com.richreach.auth.dto.PasswordResetRequest;
import com.richreach.auth.dto.PasswordVerifyRequest;
import com.richreach.auth.dto.PasswordVerifyResponse;
import com.richreach.auth.service.FindEmailService;
import com.richreach.auth.service.PasswordResetService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 입력 검증과 응답 형식만 확인하므로 보안 필터는 끄고 서비스는 가짜로 둔다. (공개 경로 여부는 AccountRecoveryApiTest에서 확인한다.)
@WebMvcTest(AccountRecoveryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AccountRecoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindEmailService findEmailService;
    @MockitoBean
    private PasswordResetService passwordResetService;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertFieldRejected(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"))
            .andExpect(jsonPath("$.data[?(@.field=='" + field + "')]").exists());
    }

    @Test
    @DisplayName("이메일 찾기에 성공하면 가려진 이메일을 내려준다")
    void findEmailReturnsMaskedEmail() throws Exception {
        when(findEmailService.findMaskedEmail(any())).thenReturn(new FindEmailResponse("ri***@example.com"));

        send(post("/api/auth/find-email"), "{\"name\":\"김리치\",\"phoneNumber\":\"010-1234-5678\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.maskedEmail").value("ri***@example.com"));
    }

    @Test
    @DisplayName("계정이 없으면 404와 AUTH-013을 내려준다")
    void findEmailAccountNotFound() throws Exception {
        doThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND)).when(findEmailService).findMaskedEmail(any());

        send(post("/api/auth/find-email"), "{\"name\":\"김리치\",\"phoneNumber\":\"01012345678\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("AUTH-013"));
    }

    @Test
    @DisplayName("이메일 찾기는 이름이 비었거나 전화번호 형식이 틀리면 400이다")
    void findEmailValidation() throws Exception {
        assertFieldRejected(send(post("/api/auth/find-email"), "{\"name\":\" \",\"phoneNumber\":\"01012345678\"}"),
            "name");
        assertFieldRejected(send(post("/api/auth/find-email"), "{\"name\":\"김리치\",\"phoneNumber\":\"02-123-4567\"}"),
            "phoneNumber");
        verifyNoInteractions(findEmailService);
    }

    @Test
    @DisplayName("인증번호 발송 결과로 유효 시간과 재전송 대기 시간을 내려주고, 개발용 인증번호는 있을 때만 담는다")
    void sendCodeResponse() throws Exception {
        when(passwordResetService.sendCode(any()))
            .thenReturn(new PasswordCodeResponse(180, 60, "123456"))
            .thenReturn(new PasswordCodeResponse(180, 60, null));

        send(post("/api/auth/password/code"), "{\"loginId\":\"rich@example.com\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.expiresIn").value(180))
            .andExpect(jsonPath("$.data.resendIn").value(60))
            .andExpect(jsonPath("$.data.devCode").value("123456"));
        send(post("/api/auth/password/code"), "{\"loginId\":\"rich@example.com\"}")
            .andExpect(jsonPath("$.data.devCode").doesNotExist());
    }

    @Test
    @DisplayName("인증번호 발송은 이메일 형식이 아니면 400이고, 재전송 대기 중이면 429(AUTH-011)다")
    void sendCodeValidationAndTooMany() throws Exception {
        assertFieldRejected(send(post("/api/auth/password/code"), "{\"loginId\":\"plain\"}"), "loginId");
        verifyNoInteractions(passwordResetService);

        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS)).when(passwordResetService).sendCode(any());
        send(post("/api/auth/password/code"), "{\"loginId\":\"rich@example.com\"}")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("AUTH-011"));
    }

    @Test
    @DisplayName("인증번호 확인에 성공하면 재설정 토큰을 내려준다")
    void verifyReturnsResetToken() throws Exception {
        when(passwordResetService.verifyCode(any())).thenReturn(new PasswordVerifyResponse("reset-token-value"));

        send(post("/api/auth/password/verify"), "{\"loginId\":\"rich@example.com\",\"code\":\"123456\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resetToken").value("reset-token-value"));
    }

    @Test
    @DisplayName("인증번호는 숫자 6자리가 아니면 400이다")
    void verifyCodeFormat() throws Exception {
        for (String invalid : new String[] {"12345", "1234567", "12345a", ""}) {
            assertFieldRejected(send(post("/api/auth/password/verify"),
                "{\"loginId\":\"rich@example.com\",\"code\":\"" + invalid + "\"}"), "code");
        }
        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("인증번호 틀림, 만료, 시도 초과는 코드와 HTTP 상태로 구분해서 내려준다")
    void verifyErrorsKeepTheirCodes() throws Exception {
        String json = "{\"loginId\":\"rich@example.com\",\"code\":\"123456\"}";

        doThrow(new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID)).when(passwordResetService).verifyCode(any());
        send(post("/api/auth/password/verify"), json)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-009"));

        doThrow(new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED)).when(passwordResetService).verifyCode(any());
        send(post("/api/auth/password/verify"), json)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH-010"));

        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS)).when(passwordResetService).verifyCode(any());
        send(post("/api/auth/password/verify"), json)
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AUTH-011"));
    }

    @Test
    @DisplayName("새 비밀번호 설정에 성공하면 200이다")
    void resetPasswordSucceeds() throws Exception {
        send(patch("/api/auth/password"), "{\"resetToken\":\"token\",\"newPassword\":\"newPass123\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        org.mockito.Mockito.verify(passwordResetService).resetPassword(any());
    }

    @Test
    @DisplayName("새 비밀번호는 가입과 같은 규칙이고, 재설정 토큰이 없으면 400이다")
    void resetPasswordValidation() throws Exception {
        assertFieldRejected(send(patch("/api/auth/password"), "{\"resetToken\":\"token\",\"newPassword\":\"short1\"}"),
            "newPassword");
        assertFieldRejected(send(patch("/api/auth/password"), "{\"resetToken\":\"token\",\"newPassword\":\"한글비밀번호1234\"}"),
            "newPassword");
        assertFieldRejected(send(patch("/api/auth/password"), "{\"resetToken\":\"\",\"newPassword\":\"newPass123\"}"),
            "resetToken");
        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("잘못되거나 이미 쓴 재설정 토큰은 401(AUTH-005)이다")
    void resetPasswordRejectsBadToken() throws Exception {
        doThrow(new BusinessException(ErrorCode.INVALID_TOKEN)).when(passwordResetService).resetPassword(any());

        send(patch("/api/auth/password"), "{\"resetToken\":\"used\",\"newPassword\":\"newPass123\"}")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-005"));
    }

    @Test
    @DisplayName("요청 객체를 문자열로 찍어도 인증번호, 토큰, 비밀번호, 이름, 전화번호는 나오지 않는다")
    void toStringHidesSecrets() {
        assertThat(new FindEmailRequest("김리치", "01012345678").toString())
            .doesNotContain("김리치").doesNotContain("01012345678");
        assertThat(new PasswordVerifyRequest("rich@example.com", "482913").toString()).doesNotContain("482913");
        assertThat(new PasswordResetRequest("secret-token", "newPass123").toString())
            .doesNotContain("secret-token").doesNotContain("newPass123");
        assertThat(new PasswordVerifyResponse("secret-token").toString()).doesNotContain("secret-token");
    }

}
