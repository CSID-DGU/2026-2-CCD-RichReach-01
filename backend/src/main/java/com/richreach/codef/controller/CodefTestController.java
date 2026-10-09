package com.richreach.codef.controller;

import com.richreach.codef.client.CodefTokenProvider;
import com.richreach.codef.client.CodefTokenProvider.IssuedToken;
import com.richreach.codef.dto.CodefApprovalRequest;
import com.richreach.codef.dto.CodefBillingRequest;
import com.richreach.codef.dto.CodefCardListRequest;
import com.richreach.codef.dto.CodefConnectRequest;
import com.richreach.codef.dto.CodefResultCheckRequest;
import com.richreach.codef.dto.CodefTokenStatusResponse;
import com.richreach.codef.service.CodefTestService;
import com.richreach.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [임시] Swagger에서 CODEF 연동을 확인하기 위한 개발용 API.
 * 인증이 없고 개인정보(카드사 로그인 정보, 거래 내역)를 다루므로 local 프로파일에서만 동작한다.
 * 확인이 끝나면 삭제한다.
 */
@Profile("local")
@Tag(name = "CODEF 테스트 (임시)", description = "로컬 개발 전용 임시 API. 확인이 끝나면 삭제한다. 응답에 개인정보가 포함될 수 있으니 화면을 공유하지 않는다.")
@RestController
@RequestMapping("/api/v1/codef-test")
public class CodefTestController {

    private static final DateTimeFormatter EXPIRES_AT_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));

    private final CodefTokenProvider tokenProvider;
    private final CodefTestService testService;
    private final Clock clock;

    public CodefTestController(CodefTokenProvider tokenProvider, CodefTestService testService, Clock clock) {
        this.tokenProvider = tokenProvider;
        this.testService = testService;
        this.clock = clock;
    }

    @Operation(
        summary = "1. 액세스 토큰 발급/확인",
        description = "캐시된 토큰이 있으면 재사용하고(fromCache=true), 없거나 만료가 임박했으면 CODEF에서 새로 발급한다. "
            + "토큰 전체 값은 노출하지 않는다.")
    @GetMapping("/token")
    public ApiResponse<CodefTokenStatusResponse> token() {
        IssuedToken token = tokenProvider.getToken();
        long remainingSeconds = Duration.between(clock.instant(), token.expiresAt()).toSeconds();

        return ApiResponse.ok(new CodefTokenStatusResponse(
            token.fromCache(),
            token.tokenType(),
            mask(token.value()),
            EXPIRES_AT_FORMAT.format(token.expiresAt()),
            Math.max(remainingSeconds, 0)));
    }

    @Operation(
        summary = "2. 카드사 계정 등록 (connectedId 발급)",
        description = "본인 카드사 웹사이트 아이디/비밀번호를 등록하고 connectedId를 받는다. "
            + "비밀번호는 서버에서 RSA 암호화해서 CODEF에 전달한다. 응답의 data.data.connectedId를 다음 API에 사용한다. "
            + "(CODEF 응답을 가공 없이 그대로 돌려준다. result.code가 CF-00000이면 성공)")
    @PostMapping("/connected-id")
    public ApiResponse<Object> connect(@Valid @RequestBody CodefConnectRequest request) {
        return ApiResponse.ok(testService.createConnectedId(request));
    }

    @Operation(
        summary = "3. 보유카드 조회",
        description = "연동한 카드사 계정의 보유 카드 목록을 조회한다. 승인내역을 카드별로 조회할 때 쓰는 카드명이 여기에 나온다.")
    @PostMapping("/cards")
    public ApiResponse<Object> cards(@Valid @RequestBody CodefCardListRequest request) {
        return ApiResponse.ok(testService.cardList(request));
    }

    @Operation(
        summary = "4. 개인카드 승인내역 조회",
        description = "기간을 지정해 승인내역(가맹점, 금액, 결제방법, 취소 여부 등)을 조회한다. "
            + "memberStoreInfoType을 1로 하면 가맹점 업종 정보가 오는지 카드사별로 확인할 수 있다. "
            + "호출 횟수(하루 100회)와 카드사의 IP 차단 위험이 있으니 짧은 기간으로 필요한 만큼만 호출한다.")
    @PostMapping("/approvals")
    public ApiResponse<Object> approvals(@Valid @RequestBody CodefApprovalRequest request) {
        return ApiResponse.ok(testService.approvals(request));
    }

    @Operation(
        summary = "5. 카드 실적 및 혜택 충족 현황 조회 (실적조회)",
        description = "카드별 실적 충족 여부(resMeetPerformanceYN), 현재 이용금액(resCurrentUseAmt), 필요 실적 금액"
            + "(resRequiredPerformanceAmt), 이용금액 반영 기간(commStartDate/commEndDate)을 확인한다. "
            + "신한카드는 connectedId와 organization만 입력하고 나머지는 비워둔다. "
            + "응답의 실적이 어느 기간 기준인지 반영 기간으로 확인하고, 승인내역 합계와 비교해 본다.")
    @PostMapping("/result-check")
    public ApiResponse<Object> resultCheck(@Valid @RequestBody CodefResultCheckRequest request) {
        return ApiResponse.ok(testService.resultCheck(request));
    }

    @Operation(
        summary = "6. 청구내역 조회 (결제예정금액)",
        description = "이번 명세서의 결제예정일(resPaymentDueDate), 합계금액(resTotalAmount), 일시불/할부 금액, "
            + "건별 내역(resChargeHistoryList)을 조회한다. startDate(YYYYMM)를 비우면 가장 최근 명세서를 돌려준다. "
            + "현대카드는 아이디 로그인 시 cardNo/cardPassword가 필수이며, 미입력 시 CF-12401 오류가 나고 "
            + "카드 비밀번호를 3회 틀리면 계정이 잠긴다(CF-12834). KB카드는 카드소지확인이 필요할 때만 넣는다. "
            + "resChargeHistoryList는 5000건 단위로 과금되니 과도한 호출을 피한다.")
    @PostMapping("/billing")
    public ApiResponse<Object> billing(@Valid @RequestBody CodefBillingRequest request) {
        return ApiResponse.ok(testService.billing(request));
    }

    private String mask(String token) {
        int visible = Math.min(4, token.length());
        return token.substring(0, visible) + "****(" + token.length() + "자)";
    }

}
