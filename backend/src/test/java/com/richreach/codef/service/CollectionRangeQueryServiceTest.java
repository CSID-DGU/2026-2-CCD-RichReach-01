package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 사용자의 카드사 계정별 수집 기록을 하나의 수집 시작일로 합치는 규칙을 DB 없이 확인한다. */
class CollectionRangeQueryServiceTest {

    private CodefOrgAccountRepository repository;
    private CollectionRangeQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(CodefOrgAccountRepository.class);
        service = new CollectionRangeQueryService(repository);
    }

    private static CodefOrgAccount account(LocalDate collectedFrom) {
        CodefOrgAccount account = new CodefOrgAccount(null, null, "0301", "CD", "P", "1", "ACTIVE",
            LocalDateTime.of(2026, 11, 1, 0, 0));
        if (collectedFrom != null) {
            account.recordCollectedRange(collectedFrom, LocalDate.of(2026, 11, 15), "1");
        }
        return account;
    }

    @Test
    @DisplayName("연결한 카드사가 없으면 기록이 없는 것으로 본다")
    void noAccounts() {
        when(repository.findActiveByUserId(1L)).thenReturn(List.of());

        assertThat(service.collectedFrom(1L)).isEmpty();
    }

    @Test
    @DisplayName("모든 카드사에 기록이 있으면 가장 늦은 시작일을 돌려준다")
    void latestStartAmongAccounts() {
        when(repository.findActiveByUserId(1L)).thenReturn(List.of(account(LocalDate.of(2026, 6, 1)),
            account(LocalDate.of(2026, 8, 1))));

        assertThat(service.collectedFrom(1L)).contains(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("기록이 없는 카드사가 하나라도 있으면 전체를 확신할 수 없어 기록이 없는 것으로 본다")
    void anyAccountWithoutRecord() {
        when(repository.findActiveByUserId(1L)).thenReturn(List.of(account(LocalDate.of(2026, 6, 1)), account(null)));

        assertThat(service.collectedFrom(1L)).isEmpty();
    }

}
