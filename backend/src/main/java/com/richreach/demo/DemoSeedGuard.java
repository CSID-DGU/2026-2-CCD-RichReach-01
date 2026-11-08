package com.richreach.demo;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 시연 데이터를 만들어도 되는 DB인지 확인한다. 로컬 DB(내 PC, 또는 Compose 안의 mysql)가 아니면 만들지 않는다.
 * 공용 DB나 운영 DB에 가짜 카드와 거래가 섞이는 사고를 막으려는 안전장치라서, 접속 대상을 알 수 없을 때도 막는다.
 * 오류 메시지에는 접속 대상(주소)을 적지 않는다.
 */
final class DemoSeedGuard {

    /** 시연 데이터를 만들어도 되는 접속 대상. 내 PC와 Compose 안의 MySQL 서비스 이름이다. */
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "mysql");

    /** jdbc:mysql://호스트[:포트]/... 에서 호스트를 꺼낸다. 사용자 정보(user@)가 붙어도 호스트만 본다. */
    private static final Pattern HOST = Pattern.compile("^jdbc:mysql://(?:[^/@]*@)?(\\[[^\\]]+\\]|[^/:?]+)");

    private DemoSeedGuard() {
    }

    /** 로컬 DB가 아니거나 접속 대상을 알 수 없으면 예외를 던진다. */
    static void requireLocalDatabase(String jdbcUrl) {
        if (!isLocal(jdbcUrl)) {
            throw new IllegalStateException(
                "시연 데이터는 로컬 DB(localhost, 127.0.0.1, mysql)에만 만들 수 있습니다. 로컬이 아닌 DB이거나 접속 대상을 알 수 없어 중단합니다.");
        }
    }

    static boolean isLocal(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        Matcher matcher = HOST.matcher(jdbcUrl.strip().toLowerCase());
        return matcher.find() && LOCAL_HOSTS.contains(matcher.group(1));
    }

}
