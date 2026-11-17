package com.richreach;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트가 함께 쓰는 MySQL 컨테이너 하나와, 그 안에 테스트마다 따로 만드는 DB(스키마).
 *
 * <p>이전에는 Spring 컨텍스트마다 MySQL 컨테이너를 따로 띄웠다. 컨텍스트가 30개 가까이 되면서 컨테이너도 그만큼 동시에 떠 있어서
 * CI 러너의 자원이 부족해졌고, 마지막에 실행되는 마이그레이션 테스트의 컨테이너가 시작하지 못했다. 이제 컨테이너는 JVM당 하나이고
 * 컨텍스트(또는 테스트 클래스)마다 <b>자기만의 DB</b>를 새로 만들어 쓴다. 그래서 "테이블이 비어 있다"를 가정하는 테스트가 다른
 * 테스트의 데이터에 영향받지 않는 격리 수준은 이전과 같다.
 *
 * <p>컨테이너는 처음 필요할 때 한 번 시작하고 직접 멈추지 않는다. JVM이 끝나면 Testcontainers(Ryuk)가 정리한다.
 * DB 연결은 컨테이너의 root 계정으로 한다. 새 DB를 만드는 권한이 필요해서다. (테스트용 일회용 컨테이너라 안전하다)
 */
public final class SharedMysql {

    /** 운영 DB와 같은 메이저 버전으로 고정 (latest 사용 금지). */
    private static final DockerImageName IMAGE = DockerImageName.parse("mysql:8.4");

    /**
     * 컨텍스트와 테스트 클래스가 동시에 열어 두는 연결을 충분히 허용한다. 기본값(151)은 컨텍스트 수십 개가 각자 연결 풀을 열면
     * 모자란다.
     */
    private static final String MAX_CONNECTIONS = "--max-connections=1000";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static MySQLContainer container;

    private SharedMysql() {
    }

    /** 새로 만든 DB의 연결 정보. */
    public record Database(String name, String jdbcUrl, String username, String password) {

    }

    /** 이 JVM의 모든 테스트가 쓰는 컨테이너. 처음 부를 때 한 번만 시작한다. */
    private static synchronized MySQLContainer container() {
        if (container == null) {
            MySQLContainer started = new MySQLContainer(IMAGE).withCommand(MAX_CONNECTIONS);
            started.start();
            container = started;
        }
        return container;
    }

    /**
     * 공유 컨테이너 안에 비어 있는 새 DB를 만든다. 이름은 JVM 안에서 겹치지 않고, 만들 때마다 항상 새 DB다.
     * 호출한 쪽이 그 DB를 마음대로 지우고 다시 만들어도 다른 테스트에 영향이 없다.
     */
    public static Database newDatabase() {
        MySQLContainer shared = container();
        String name = "rr_test_" + SEQUENCE.incrementAndGet();
        // MySQLContainer의 root 비밀번호는 일반 계정의 비밀번호와 같다.
        String root = "root";
        String password = shared.getPassword();
        try (Connection connection = DriverManager.getConnection(shared.getJdbcUrl(), root, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        } catch (SQLException e) {
            throw new IllegalStateException("테스트용 DB를 만들지 못했습니다: " + name, e);
        }
        // 기본 DB 이름(test) 자리만 새 DB 이름으로 바꾼다. 뒤의 연결 옵션은 그대로 둔다.
        String url = shared.getJdbcUrl().replaceFirst("/" + shared.getDatabaseName() + "(?=$|\\?)", "/" + name);
        return new Database(name, url, root, password);
    }

}
