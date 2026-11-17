package com.richreach.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/** 시연 데이터 생성기가 local 프로파일에서만 만들어지는지 확인한다. dev(공용 서버)와 운영에서는 아예 만들어지지 않는다. */
class DemoSeedProfileTest {

    @Test
    @DisplayName("DemoSeedRunner는 local 프로파일에서만 켜진다")
    void runnerIsLocalOnly() {
        Profile profile = DemoSeedRunner.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("local");
    }

}
