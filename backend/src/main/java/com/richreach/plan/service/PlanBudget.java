package com.richreach.plan.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.plan.dto.PlanCreateRequest;
import com.richreach.plan.dto.PlanCreateRequest.CategoryBudget;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 검증을 마친 월 예산. DB 없이 요청 값만으로 검증하고 계산하므로 따로 확인할 수 있다.
 *
 * <p>검증 규칙 (어기면 월은 400 {@code COMMON-400}, 예산은 400 {@code PLAN-003})
 * <ul>
 *   <li>월은 {@code yyyy-MM} 형식이다.</li>
 *   <li>총예산은 0보다 크고 {@link #MAX_TOTAL} 이하다.</li>
 *   <li>업종은 11개 업종 코드이고(대소문자 무시) 한 번씩만 나오며, 업종 예산은 0 이상이다. 업종 예산이 0인 업종은 뺀다.</li>
 *   <li>업종 예산의 합이 총예산과 같다. (다르면 어느 쪽이 맞는지 알 수 없어서 서버가 임의로 보정하지 않고 거부한다.)</li>
 * </ul>
 * 금액은 소비 정보라서 로그에 남기지 않는다.
 *
 * @param month      플랜 대상 월
 * @param total      총예산(원)
 * @param byCategory 예산이 있는 업종의 예산(원). 업종 순서대로 들어 있고 모두 0보다 크다.
 */
record PlanBudget(YearMonth month, long total, Map<SpendingCategory, Long> byCategory) {

    /** 총예산 상한(원). 비정상적으로 큰 값이 계산을 넘치게 하지 않게 막는다. */
    static final long MAX_TOTAL = 100_000_000_000L;

    /** 네 자리 연도와 두 자리 월. (plan_jobs.plan_month 같은 CHAR(7) 컬럼에 그대로 들어가는 형식이다.) */
    private static final Pattern MONTH_FORMAT = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    static PlanBudget parse(PlanCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "요청 본문이 필요합니다.");
        }
        YearMonth month = parseMonth(request.month());
        PlanCreateRequest.Budget budget = request.budget();
        if (budget == null || budget.total() == null || budget.byCategory() == null || budget.byCategory().isEmpty()) {
            throw invalid("총예산과 업종별 예산이 필요합니다.");
        }
        long total = budget.total();
        if (total <= 0 || total > MAX_TOTAL) {
            throw invalid("총예산은 0보다 크고 " + MAX_TOTAL + "원 이하여야 합니다.");
        }

        Map<SpendingCategory, Long> byCategory = new EnumMap<>(SpendingCategory.class);
        Set<SpendingCategory> seen = EnumSet.noneOf(SpendingCategory.class);
        long sum = 0;
        for (CategoryBudget item : budget.byCategory()) {
            if (item == null || item.category() == null || item.amount() == null) {
                throw invalid("업종과 금액이 모두 필요합니다.");
            }
            SpendingCategory category = SpendingCategory.fromCode(item.category())
                .orElseThrow(() -> invalid("지원하지 않는 업종입니다."));
            if (item.amount() < 0) {
                throw invalid("업종 예산은 0 이상이어야 합니다.");
            }
            if (!seen.add(category)) {
                throw invalid("같은 업종이 두 번 들어 있습니다.");
            }
            if (item.amount() > MAX_TOTAL) {
                throw invalid("업종 예산이 너무 큽니다.");
            }
            sum += item.amount();
            if (item.amount() > 0) {
                byCategory.put(category, item.amount());
            }
        }
        if (sum != total) {
            throw invalid("업종별 예산의 합이 총예산과 같아야 합니다.");
        }
        return new PlanBudget(month, total, byCategory);
    }


    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "월은 yyyy-MM 형식이어야 합니다.");
        }
        // YearMonth.parse는 +10000-01처럼 연도가 네 자리가 아닌 값도 받는다. 그런 값은 CHAR(7) 컬럼에 들어가지 않으므로
        // 먼저 형식(네 자리 연도, 01~12월)을 확인한다.
        String normalized = month.strip();
        if (!MONTH_FORMAT.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "월은 yyyy-MM 형식이어야 합니다.");
        }
        try {
            return YearMonth.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "월은 yyyy-MM 형식이어야 합니다.");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_BUDGET, message);
    }

    /** 저장용 문자열. "업종:금액"을 쉼표로 잇는다. */
    String encode() {
        return byCategory.entrySet().stream().map(entry -> entry.getKey().name() + ":" + entry.getValue())
            .collect(Collectors.joining(","));
    }

    /** {@link #encode()}로 만든 문자열에서 되살린다. 저장된 값이 깨져 있으면 {@link IllegalStateException}이다. */
    static PlanBudget decode(YearMonth month, long total, String encoded) {
        Map<SpendingCategory, Long> byCategory = new EnumMap<>(SpendingCategory.class);
        for (String part : encoded.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            String[] pair = part.split(":");
            SpendingCategory category = pair.length == 2 ? SpendingCategory.fromCode(pair[0]).orElse(null) : null;
            if (category == null) {
                throw new IllegalStateException("저장된 예산 형식이 올바르지 않습니다.");
            }
            byCategory.put(category, Long.parseLong(pair[1]));
        }
        return new PlanBudget(month, total, byCategory);
    }

    /** 같은 요청인지 가리는 지문. 월, 총예산, 업종별 예산이 같으면 같다. (업종 순서와 0원 항목은 영향을 주지 않는다.) */
    String fingerprint() {
        String canonical = month + "|" + total + "|" + encode();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

}
