package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.auth.config.TermsProperties;
import com.richreach.codef.dto.ConnectionAgreementRequest;
import com.richreach.codef.dto.ConnectionAgreementResponse;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.entity.TermType;
import com.richreach.user.entity.UserTermAgreement;
import com.richreach.user.repository.UserRepository;
import com.richreach.user.repository.UserTermAgreementRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class ConnectionAgreementServiceTest {

    private static final long USER_ID = 1L;
    private static final String VERSION = "2026-11-01";
    private static final LocalDateTime AGREED_AT = LocalDateTime.of(2026, 11, 2, 9, 0, 0);

    private final UserRepository users = mock(UserRepository.class);
    private final UserTermAgreementRepository agreements = mock(UserTermAgreementRepository.class);
    // 가입 약관 버전과 다른 값으로 두어서, 카드 연동 버전을 쓰는지 확인한다.
    private final TermsProperties terms = new TermsProperties("2026-10-01", VERSION);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final ConnectionAgreementService service = new ConnectionAgreementService(users, agreements, terms, clock);

    private static ConnectionAgreementRequest request(TermType... types) {
        return new ConnectionAgreementRequest(List.of(types));
    }

    private static UserTermAgreement row(TermType type) {
        return new UserTermAgreement(USER_ID, type, VERSION, AGREED_AT);
    }

    private static List<UserTermAgreement> allRows() {
        return List.of(row(TermType.PRIVACY), row(TermType.CARD_INFO), row(TermType.TRANSACTION));
    }

    private void givenUserExists() {
        when(users.existsById(USER_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("처음 동의하면 3개를 카드 연동 약관 버전과 현재 시각으로 한 번에 저장한다")
    void savesAllThreeWithTheCardConnectionVersion() {
        givenUserExists();
        when(agreements.findByUserIdAndTermVersionAndTermTypeIn(eq(USER_ID), eq(VERSION), anyCollection()))
            .thenReturn(List.of()).thenReturn(allRows());

        ConnectionAgreementResponse response = service.agree(USER_ID,
            request(TermType.CARD_INFO, TermType.TRANSACTION, TermType.PRIVACY));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserTermAgreement>> saved = ArgumentCaptor.forClass(List.class);
        verify(agreements).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(3)
            .allSatisfy(agreement -> {
                assertThat(agreement.getTermVersion()).isEqualTo(VERSION);
                assertThat(agreement.getAgreedAt()).isEqualTo(AGREED_AT);
                assertThat(agreement.getUserId()).isEqualTo(USER_ID);
            });
        assertThat(saved.getValue()).extracting(UserTermAgreement::getTermType)
            .containsExactlyInAnyOrder("CARD_INFO", "TRANSACTION", "PRIVACY");
        assertThat(response.termsVersion()).isEqualTo(VERSION);
        assertThat(response.types()).containsExactly(TermType.CARD_INFO, TermType.TRANSACTION, TermType.PRIVACY);
        assertThat(response.agreedAt().toLocalDateTime()).isEqualTo(AGREED_AT);
        assertThat(response.agreedAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
    }

    @Test
    @DisplayName("같은 버전에 이미 동의했으면 아무것도 저장하지 않고 기존 결과를 돌려준다")
    void alreadyAgreedIsIdempotent() {
        givenUserExists();
        when(agreements.findByUserIdAndTermVersionAndTermTypeIn(eq(USER_ID), eq(VERSION), anyCollection()))
            .thenReturn(allRows());

        ConnectionAgreementResponse response = service.agree(USER_ID,
            request(TermType.CARD_INFO, TermType.TRANSACTION, TermType.PRIVACY));

        verify(agreements, never()).saveAll(anyList());
        assertThat(response.agreedAt().toLocalDateTime()).isEqualTo(AGREED_AT);
    }

    @Test
    @DisplayName("일부만 기록돼 있으면 빠진 항목만 저장한다")
    void savesOnlyMissingTypes() {
        givenUserExists();
        when(agreements.findByUserIdAndTermVersionAndTermTypeIn(eq(USER_ID), eq(VERSION), anyCollection()))
            .thenReturn(List.of(row(TermType.CARD_INFO))).thenReturn(allRows());

        service.agree(USER_ID, request(TermType.CARD_INFO, TermType.TRANSACTION, TermType.PRIVACY));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserTermAgreement>> saved = ArgumentCaptor.forClass(List.class);
        verify(agreements).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(UserTermAgreement::getTermType)
            .containsExactlyInAnyOrder("TRANSACTION", "PRIVACY");
    }

    @Test
    @DisplayName("같은 요청이 동시에 와서 저장이 유일 제약에 걸려도 저장된 결과를 읽어 성공으로 답한다")
    void concurrentDuplicateIsTreatedAsSuccess() {
        givenUserExists();
        when(agreements.findByUserIdAndTermVersionAndTermTypeIn(eq(USER_ID), eq(VERSION), anyCollection()))
            .thenReturn(List.of()).thenReturn(allRows());
        when(agreements.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("duplicate"));

        ConnectionAgreementResponse response = service.agree(USER_ID,
            request(TermType.CARD_INFO, TermType.TRANSACTION, TermType.PRIVACY));

        assertThat(response.types()).hasSize(3);
    }

    @Test
    @DisplayName("요청에 같은 항목이 여러 번 들어 있어도 한 번씩만 처리한다")
    void duplicatedTypesInRequestAreCollapsed() {
        givenUserExists();
        when(agreements.findByUserIdAndTermVersionAndTermTypeIn(eq(USER_ID), eq(VERSION), anyCollection()))
            .thenReturn(List.of()).thenReturn(allRows());

        service.agree(USER_ID, request(TermType.CARD_INFO, TermType.CARD_INFO, TermType.TRANSACTION,
            TermType.PRIVACY, TermType.PRIVACY));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserTermAgreement>> saved = ArgumentCaptor.forClass(List.class);
        verify(agreements).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(3);
    }

    @Test
    @DisplayName("필수 항목이 하나라도 빠지면 AUTH-008이고 아무것도 저장하지 않는다")
    void missingRequiredTypeIsRejected() {
        assertThatThrownBy(() -> service.agree(USER_ID, request(TermType.CARD_INFO, TermType.TRANSACTION)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TERMS_NOT_AGREED));

        verify(agreements, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("카드 연동 동의가 아닌 종류(가입 약관)가 섞여 있으면 400이다")
    void foreignTypeIsRejected() {
        assertThatThrownBy(() -> service.agree(USER_ID, request(TermType.CARD_INFO, TermType.TRANSACTION,
            TermType.PRIVACY, TermType.SERVICE_AND_PRIVACY)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(agreements, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("토큰은 유효하지만 사용자가 삭제된 경우 AUTH-005이고 저장하지 않는다")
    void deletedUserIsRejected() {
        when(users.existsById(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.agree(USER_ID,
            request(TermType.CARD_INFO, TermType.TRANSACTION, TermType.PRIVACY)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));

        verify(agreements, never()).saveAll(any());
    }

}
