package com.richreach.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class RequestLoggingFilterTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
        .addFilters(new RequestLoggingFilter())
        .build();

    @Test
    @DisplayName("필터를 거쳐도 컨트롤러가 요청 본문을 그대로 읽을 수 있다")
    void bodyStillReadableByController() throws Exception {
        mockMvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"secret\",\"name\":\"kim\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string("{\"password\":\"secret\",\"name\":\"kim\"}"));
    }

    @Test
    @DisplayName("actuator 경로는 로깅 대상에서 제외한다")
    void skipsActuator() {
        org.springframework.mock.web.MockHttpServletRequest request =
            new org.springframework.mock.web.MockHttpServletRequest("GET", "/actuator/health");

        assertThat(new RequestLoggingFilter().shouldNotFilter(request)).isTrue();
        request.setRequestURI("/api/home");
        assertThat(new RequestLoggingFilter().shouldNotFilter(request)).isFalse();
    }

    @RestController
    static class EchoController {

        @PostMapping("/echo")
        String echo(@RequestBody String body) {
            return body;
        }

    }

}
