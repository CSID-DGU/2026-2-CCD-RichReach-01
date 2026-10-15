package com.richreach.codef.entity;

import com.richreach.codef.crypto.ConnectedIdConverter;
import com.richreach.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자의 CODEF 연동. 사용자당 1개. */
@Entity
@Table(name = "codef_connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodefConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** AES-GCM으로 암호화해서 저장 (DB 레벨 UNIQUE 없음 - ConnectedIdConverter 참고) */
    @Convert(converter = ConnectedIdConverter.class)
    @Column(name = "connected_id", nullable = false, length = 255)
    private String connectedId;

    /** SANDBOX, DEMO, PROD */
    @Column(nullable = false, length = 20)
    private String env;

    @Column(name = "card_info_agreed_at", nullable = false)
    private LocalDateTime cardInfoAgreedAt;

    @Column(name = "transaction_history_agreed_at", nullable = false)
    private LocalDateTime transactionHistoryAgreedAt;

    @Column(name = "privacy_agreed_at", nullable = false)
    private LocalDateTime privacyAgreedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CodefConnection(User user, String connectedId, String env, LocalDateTime cardInfoAgreedAt,
        LocalDateTime transactionHistoryAgreedAt, LocalDateTime privacyAgreedAt, LocalDateTime createdAt) {
        this.user = user;
        this.connectedId = connectedId;
        this.env = env;
        this.cardInfoAgreedAt = cardInfoAgreedAt;
        this.transactionHistoryAgreedAt = transactionHistoryAgreedAt;
        this.privacyAgreedAt = privacyAgreedAt;
        this.createdAt = createdAt;
    }

}
