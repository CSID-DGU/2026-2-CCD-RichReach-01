package com.richreach.codef.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardCompanyTest {

    @Test
    @DisplayName("카드사는 화면 순서대로 7개이고 코드가 프론트와 같다")
    void sevenCompaniesInScreenOrder() {
        assertThat(Arrays.stream(CardCompany.values()).map(Enum::name))
            .containsExactly("KB", "HYUNDAI", "NH", "CITI", "WOORI", "BC", "SHINHAN");
    }

    @Test
    @DisplayName("화면에 보여줄 이름이 정해져 있다")
    void displayNames() {
        assertThat(CardCompany.KB.displayName()).isEqualTo("KB국민카드");
        assertThat(CardCompany.HYUNDAI.displayName()).isEqualTo("현대카드");
        assertThat(CardCompany.NH.displayName()).isEqualTo("NH농협카드");
        assertThat(CardCompany.CITI.displayName()).isEqualTo("씨티카드");
        assertThat(CardCompany.WOORI.displayName()).isEqualTo("우리카드");
        assertThat(CardCompany.BC.displayName()).isEqualTo("BC카드");
        assertThat(CardCompany.SHINHAN.displayName()).isEqualTo("신한카드");
    }

    @Test
    @DisplayName("지금은 7개 모두 연결을 지원한다")
    void allSupportedForNow() {
        assertThat(CardCompany.values()).allMatch(CardCompany::supported);
    }

}
