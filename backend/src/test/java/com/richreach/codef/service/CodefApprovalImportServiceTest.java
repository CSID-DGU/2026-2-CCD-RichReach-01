package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.codef.client.CodefApprovalClient;
import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.client.CodefApprovalClient.CodefApproval;
import com.richreach.codef.dto.ApprovalImportResponse;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.service.CodefApprovalTargets.Period;
import com.richreach.codef.service.CodefApprovalTargets.Target;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.transaction.service.TransactionStoreService.SaveResult;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 승인내역 가져오기의 카드 매칭과 저장 호출을 DB 없이 확인한다. */
class CodefApprovalImportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final String ORGANIZATION = "0305"; // BC카드

    private final CodefApprovalTargets targets = mock(CodefApprovalTargets.class);
    private final CodefApprovalClient approvals = mock(CodefApprovalClient.class);
    private final OwnedCardQueryService ownedCards = mock(OwnedCardQueryService.class);
    private final TransactionStoreService store = mock(TransactionStoreService.class);
    private final CodefApprovalImportService service =
        new CodefApprovalImportService(targets, approvals, ownedCards, store);

    private static CodefApproval approval(String approvalNo, String cardNumber) {
        return new CodefApproval(approvalNo, TODAY, null, "테스트 상점", 1_000, 0, 0, "", cardNumber, "0", "1", "1", "KRW");
    }

    private static OwnedCardResponse card(long id, CardCompany company, String maskedNumber) {
        return new OwnedCardResponse(id, 100L + id, company, "테스트 카드" + id, maskedNumber,
            OwnedCardResponse.CardKind.CHECK, OwnedCardResponse.AnalysisStatus.AVAILABLE, true, null);
    }

    private void givenTarget() {
        when(targets.period(any(), any())).thenReturn(new Period(TODAY.minusDays(6), TODAY));
        when(targets.find(eq(1L), any())).thenReturn(new Target(3L, "test-connected-id", ORGANIZATION));
    }

    @Test
    @DisplayName("카드 번호의 마지막 4자리 숫자가 같은 보유카드가 하나이면 그 카드의 거래로 저장한다")
    @SuppressWarnings("unchecked")
    void savesToMatchedCard() {
        givenTarget();
        when(ownedCards.list(1L)).thenReturn(List.of(card(7L, CardCompany.BC, "*******0****754*"),
            card(8L, CardCompany.BC, "************9999")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(new ApprovalListResult(true, 2,
            "CF-00000", List.of(approval("1", "46195400****754*"), approval("2", "46195400****754*"))));
        when(store.saveCollected(anyLong(), anyLong(), any())).thenReturn(new SaveResult(2, 0, 0, 0));

        ApprovalImportResponse response = service.importApprovals(1L, null, null, null);

        ArgumentCaptor<List<CollectedTransaction>> saved = ArgumentCaptor.forClass(List.class);
        verify(store).saveCollected(eq(1L), eq(7L), saved.capture());
        assertThat(saved.getValue()).hasSize(2);
        assertThat(response.matchedCount()).isEqualTo(2);
        assertThat(response.unmatchedCount()).isZero();
        assertThat(response.created()).isEqualTo(2);
    }

    @Test
    @DisplayName("맞는 카드가 없거나 둘 이상이면 저장하지 않고 못 찾은 건수로 센다")
    void unmatchedWhenNoneOrAmbiguous() {
        givenTarget();
        when(ownedCards.list(1L)).thenReturn(List.of(card(7L, CardCompany.BC, "************0754"),
            card(8L, CardCompany.BC, "************0754"), card(9L, CardCompany.BC, "************1111")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(new ApprovalListResult(true, 2,
            "CF-00000", List.of(approval("1", "46195400****754*"), approval("2", "11112222****555*"))));

        ApprovalImportResponse response = service.importApprovals(1L, null, null, null);

        verify(store, never()).saveCollected(anyLong(), anyLong(), any());
        assertThat(response.matchedCount()).isZero();
        assertThat(response.unmatchedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 카드사의 보유카드는 후보가 아니다")
    void ignoresOtherCardCompany() {
        givenTarget();
        when(ownedCards.list(1L)).thenReturn(List.of(card(7L, CardCompany.KB, "************0754")));
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(new ApprovalListResult(true, 1,
            "CF-00000", List.of(approval("1", "46195400****754*"))));

        ApprovalImportResponse response = service.importApprovals(1L, null, null, null);

        verify(store, never()).saveCollected(anyLong(), anyLong(), any());
        assertThat(response.unmatchedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("카드사 호출이 실패하면 저장하지 않고 결과 코드만 돌려준다")
    void failureStoresNothing() {
        givenTarget();
        when(approvals.fetchApprovals(any(), any(), any(), any())).thenReturn(ApprovalListResult.failure("CF-12401"));

        ApprovalImportResponse response = service.importApprovals(1L, null, null, null);

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("CF-12401");
        verify(store, never()).saveCollected(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("카드 번호의 마지막 4자리는 별표와 구분자를 무시한 숫자 기준이다")
    void lastFourIgnoresMasksAndSeparators() {
        assertThat(CodefApprovalImportService.lastFour("46195400****754*")).isEqualTo("0754");
        assertThat(CodefApprovalImportService.lastFour("1234-****-****-5678")).isEqualTo("5678");
        assertThat(CodefApprovalImportService.lastFour("12")).isEqualTo("12");
        assertThat(CodefApprovalImportService.lastFour(null)).isEmpty();
    }

}
