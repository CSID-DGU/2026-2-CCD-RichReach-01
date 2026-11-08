package com.richreach.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 앱을 켤 때 시연 데이터를 한 번 만든다. 평소에는 꺼져 있고, 만들 때만 환경변수로 켠다.
 * <pre>
 * DEMO_SEED_ON_STARTUP=true
 * DEMO_SEED_USER_EMAIL=내가 가입한 계정 이메일
 * DEMO_SEED_RESET=false   (true이면 이 계정의 시연 데이터를 지우고 다시 만든다)
 * </pre>
 * 프로파일이 {@code local}일 때만 실행되고, 로컬 DB가 아니면 앱 시작을 중단한다. 공용 DB에 가짜 데이터가 섞이는 것을
 * 막으려는 안전장치다. 카드 상품(MVP 카드)이 승격되어 있어야 분석할 수 있는 카드로 만들어지므로, 승격을 먼저 하세요.
 * 다 만들었으면 DEMO_SEED_ON_STARTUP을 다시 끈다.
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "richreach.demo-seed.enabled", havingValue = "true")
class DemoSeedRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;
    private final String userEmail;
    private final boolean reset;

    DemoSeedRunner(DemoDataSeeder seeder, @Value("${richreach.demo-seed.user-email:}") String userEmail,
                   @Value("${richreach.demo-seed.reset:false}") boolean reset) {
        this.seeder = seeder;
        this.userEmail = userEmail;
        this.reset = reset;
    }

    @Override
    public void run(ApplicationArguments args) {
        seeder.seed(userEmail, reset);
    }

}
