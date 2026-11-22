package com.richreach.global.openai;

import java.util.regex.Pattern;

/**
 * 사용자가 입력한 자유 문장을 모델에 보내기 전에 민감정보를 가린다.
 * 카드번호·전화번호·이메일만 가리며, 금액이나 업종 같은 일반 숫자는 그대로 둔다.
 *
 * <ul>
 *   <li>카드번호: 4-4-4-4(Visa·Mastercard 등), 4-6-5(American Express 15자리), 4-6-4(Diners 14자리)를 공백이나 하이픈으로
 *       나눈 표기와, 구분 없이 이어진 13~16자리 숫자.</li>
 *   <li>전화번호: 휴대전화(010 등), 서울(02)과 지역번호(031 등)·인터넷전화(070)가 있는 번호. 구분은 하이픈, 공백, 없음 모두
 *       받는다. 금액이나 날짜가 가려지지 않도록 앞자리를 0으로 시작하는 국번만 본다.</li>
 *   <li>이메일.</li>
 * </ul>
 * 모든 번호 형식을 가리지는 못한다. (예: 1588-1234 같은 대표번호) 사용자 거래·금액 정보는 이 필터와 별개로 모델에 보내지 않는다.
 */
public final class OpenAiPrivacyFilter {

    /** 4-4-4-4 (끝 3~4자리). */
    private static final Pattern CARD_GROUPED = Pattern.compile("\\b\\d{4}[- ]\\d{4}[- ]\\d{4}[- ]\\d{3,4}\\b");
    /** 4-6-5(American Express), 4-6-4(Diners). */
    private static final Pattern CARD_GROUPED_LONG = Pattern.compile("\\b\\d{4}[- ]\\d{6}[- ]\\d{4,5}\\b");
    private static final Pattern CARD_PLAIN = Pattern.compile("\\b\\d{13,16}\\b");
    /** 휴대전화: 010·011·016·017·018·019. */
    private static final Pattern PHONE_MOBILE = Pattern.compile("\\b01[016789][- ]?\\d{3,4}[- ]?\\d{4}\\b");
    /** 서울(02), 지역번호(031~064 등), 인터넷전화(070). 국번 3~4자리, 끝 4자리. */
    private static final Pattern PHONE_LANDLINE =
        Pattern.compile("\\b0(?:2|[3-6][1-5]|70)[- ]?\\d{3,4}[- ]?\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");

    private OpenAiPrivacyFilter() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        String masked = CARD_GROUPED.matcher(text).replaceAll("[카드번호]");
        masked = CARD_GROUPED_LONG.matcher(masked).replaceAll("[카드번호]");
        masked = CARD_PLAIN.matcher(masked).replaceAll("[카드번호]");
        masked = PHONE_MOBILE.matcher(masked).replaceAll("[전화번호]");
        masked = PHONE_LANDLINE.matcher(masked).replaceAll("[전화번호]");
        return EMAIL.matcher(masked).replaceAll("[이메일]");
    }
}
