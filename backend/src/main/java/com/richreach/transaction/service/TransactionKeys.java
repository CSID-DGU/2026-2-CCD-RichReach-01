package com.richreach.transaction.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 같은 거래를 구분하는 키. 재동기화 때 이미 저장된 거래를 찾는 데 쓴다.
 *
 * <p>키는 DB의 문자 비교 규칙(대소문자·악센트 무시)에 기대지 않으려고 애플리케이션에서 만든 해시 값이다.
 * <ul>
 *   <li>승인번호가 있으면 승인번호와 승인일시로 구분한다. 금액은 넣지 않는다. 취소·환불로 금액 표기가 바뀌어도
 *       같은 거래로 보고 갱신하기 위해서다.</li>
 *   <li>승인번호가 없으면 승인일시, 금액, 가맹점명으로 구분한다.</li>
 *   <li>같은 입력 안에서 위 기준이 같은 거래가 여러 건이면(같은 시각에 같은 금액을 두 번 결제 등) 입력 순서대로
 *       순번을 붙여 서로 다른 거래로 본다. 카드사가 같은 순서로 돌려준다고 가정한다.</li>
 * </ul>
 */
final class TransactionKeys {

    private TransactionKeys() {
    }

    /** 같은 거래로 볼 입력을 묶는 기준. 순번은 들어 있지 않다. 승인번호와 가맹점명은 이미 다듬은 값을 받는다. */
    static String base(String approvalNo, LocalDateTime approvedAt, long amount, String merchantName) {
        if (!approvalNo.isEmpty()) {
            return "A|" + approvalNo + "|" + approvedAt;
        }
        return "N|" + approvedAt + "|" + amount + "|" + normalizeMerchant(merchantName);
    }

    /** 기준과 순번으로 만든 키(SHA-256 16진수 64자). */
    static String key(String base, int occurrence) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((base + "|" + occurrence).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    /** 표기만 다른 같은 가맹점을 같게 보도록 전각·반각, 공백, 대소문자 차이를 없앤다. */
    private static String normalizeMerchant(String merchantName) {
        return Normalizer.normalize(merchantName, Normalizer.Form.NFKC)
            .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

}
