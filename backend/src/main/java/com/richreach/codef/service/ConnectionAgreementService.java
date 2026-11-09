package com.richreach.codef.service;

import com.richreach.auth.config.TermsProperties;
import com.richreach.codef.dto.ConnectionAgreementRequest;
import com.richreach.codef.dto.ConnectionAgreementResponse;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.util.Times;
import com.richreach.user.entity.TermType;
import com.richreach.user.entity.UserTermAgreement;
import com.richreach.user.repository.UserRepository;
import com.richreach.user.repository.UserTermAgreementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 카드 연동 동의(카드정보 조회, 거래내역 조회, 개인정보 수집 및 이용)를 저장한다.
 * 가입 때 쓰는 {@code user_term_agreements}에 종류만 달리해서 남기고, 약관 버전은 서버가 정한 현재 버전을 기록한다.
 * 이후 카드사 연결을 만들 때 이 기록이 있어야 한다.
 */
@Service
@RequiredArgsConstructor
public class ConnectionAgreementService {

    /** 모두 동의해야 카드를 연결할 수 있다. */
    static final Set<TermType> REQUIRED = EnumSet.of(TermType.CARD_INFO, TermType.TRANSACTION, TermType.PRIVACY);

    private final UserRepository users;
    private final UserTermAgreementRepository agreements;
    private final TermsProperties terms;
    private final Clock clock;

    /**
     * 동의를 기록한다. 같은 약관 버전에 이미 동의한 항목은 다시 만들지 않으므로 여러 번 호출해도 결과가 같다.
     *
     * <p>일부러 트랜잭션을 걸지 않았다. 같은 요청이 동시에 두 번 오면(버튼 중복 클릭) 한쪽의 저장이 유일 제약에
     * 걸리는데, 하나의 트랜잭션 안이면 그 실패가 전체를 롤백 상태로 만들어 이미 저장된 결과를 읽을 수 없다.
     * 저장({@code saveAll})은 3개를 한 번에 커밋하므로 일부만 저장되는 일은 없다.
     */
    public ConnectionAgreementResponse agree(long userId, ConnectionAgreementRequest request) {
        Set<TermType> requested = EnumSet.noneOf(TermType.class);
        requested.addAll(request.types());
        if (!REQUIRED.containsAll(requested)) {
            // 카드 연동 동의가 아닌 종류(예: 가입 약관)를 보낸 경우
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!requested.containsAll(REQUIRED)) {
            throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
        }
        if (!users.existsById(userId)) {
            // 토큰은 유효하지만 사용자가 이미 삭제된 경우
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String version = terms.cardConnectionVersion();
        List<UserTermAgreement> recorded = findRecorded(userId, version);
        Set<TermType> missing = EnumSet.copyOf(REQUIRED);
        recorded.forEach(agreement -> missing.remove(TermType.valueOf(agreement.getTermType())));

        if (!missing.isEmpty()) {
            // DB의 DATETIME은 초 단위라서, 응답과 저장된 값이 달라지지 않도록 초 단위로 맞춘다.
            LocalDateTime now = Times.nowSeconds(clock);
            try {
                agreements.saveAll(missing.stream()
                    .map(type -> new UserTermAgreement(userId, type, version, now))
                    .toList());
            } catch (DataIntegrityViolationException e) {
                // 같은 요청이 동시에 와서 다른 요청이 먼저 저장한 경우다. 저장된 결과를 다시 읽어서 돌려준다.
            }
            recorded = findRecorded(userId, version);
        }
        return toResponse(recorded, version);
    }

    private List<UserTermAgreement> findRecorded(long userId, String version) {
        return agreements.findByUserIdAndTermVersionAndTermTypeIn(userId, version,
            REQUIRED.stream().map(Enum::name).collect(Collectors.toSet()));
    }

    private ConnectionAgreementResponse toResponse(List<UserTermAgreement> recorded, String version) {
        if (recorded.size() != REQUIRED.size()) {
            // 사용자가 중간에 삭제되는 등으로 저장을 확인하지 못한 경우
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        List<TermType> types = recorded.stream()
            .map(agreement -> TermType.valueOf(agreement.getTermType()))
            .sorted(Comparator.comparing(Enum::ordinal))
            .toList();
        LocalDateTime agreedAt = recorded.stream()
            .map(UserTermAgreement::getAgreedAt)
            .min(Comparator.naturalOrder())
            .orElseThrow();
        OffsetDateTime offsetAgreedAt = agreedAt.atZone(clock.getZone()).toOffsetDateTime();
        return new ConnectionAgreementResponse(types, version, offsetAgreedAt);
    }

}
