package com.richreach.codef.controller;

import com.richreach.codef.dto.ApprovalProbeResponse;
import com.richreach.codef.service.CodefApprovalProbeService;
import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [임시] 승인내역 응답 구조를 확인하기 위한 개발용 API. 로그인이 필요하고 {@code local} 프로파일에서만 동작한다.
 * 응답에는 건수와 결과 코드만 있다. 확인이 끝나면 삭제한다.
 */
@Profile("local")
@Tag(name = "CODEF 승인내역 확인 (임시)", description = "로컬 개발 전용. 건수와 결과 코드만 돌려주며, 확인이 끝나면 삭제한다.")
@RestController
@RequestMapping("/api/dev/codef")
@RequiredArgsConstructor
public class CodefApprovalProbeController {

    private final CodefApprovalProbeService probeService;

    @Operation(
        summary = "승인내역 응답 확인 (건수만)",
        description = "내 카드사 연결로 승인내역을 한 번 조회하고 건수와 결과 코드만 돌려준다. "
            + "응답 구조(필드 이름과 형식)는 CODEF_DEBUG_LOGSHAPE=true로 서버 로그에서 확인한다. "
            + "기간은 오늘까지 최대 31일이고, 비우면 최근 7일이다. 호출 횟수 제한이 있으니 필요한 만큼만 호출한다.")
    @PostMapping("/approvals/probe")
    public ApiResponse<ApprovalProbeResponse> probe(
        @AuthenticationPrincipal AuthenticatedUser user,
        @RequestParam(required = false) Long accountId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(probeService.probe(user.userId(), accountId, from, to));
    }

}
