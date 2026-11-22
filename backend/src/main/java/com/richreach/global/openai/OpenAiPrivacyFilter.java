package com.richreach.global.openai;

import java.util.regex.Pattern;

/**
 * 사용자가 입력한 자유 문장을 모델에 보내기 전에 민감정보를 가린다.
 * 카드번호·전화번호·이메일만 가리며, 금액이나 업종 같은 일반 숫자는 그대로 둔다.
 */
public final class OpenAiPrivacyFilter {

    private static final Pattern CARD_GROUPED = Pattern.compile("\\b\\d{4}[- ]\\d{4}[- ]\\d{4}[- ]\\d{3,4}\\b");
    private static final Pattern CARD_PLAIN = Pattern.compile("\\b\\d{13,16}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b01[016789][- ]?\\d{3,4}[- ]?\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");

    private OpenAiPrivacyFilter() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        String masked = CARD_GROUPED.matcher(text).replaceAll("[카드번호]");
        masked = CARD_PLAIN.matcher(masked).replaceAll("[카드번호]");
        masked = PHONE.matcher(masked).replaceAll("[전화번호]");
        return EMAIL.matcher(masked).replaceAll("[이메일]");
    }
}
