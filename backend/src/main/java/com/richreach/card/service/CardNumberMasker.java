package com.richreach.card.service;

/**
 * 보유카드로 저장하는 카드 번호를 가린다. 카드사가 이미 가려서 주더라도, 전체 번호나 앞자리가 보이는 번호가 오는
 * 경우를 대비해 저장하기 전에 항상 한 번 더 거른다. 마지막 4자리 외의 숫자는 저장하거나 로그에 남기지 않는다.
 */
public final class CardNumberMasker {

    private static final int VISIBLE_DIGITS = 4;
    private static final int MAX_LENGTH = 30;

    private CardNumberMasker() {
    }

    /**
     * <ul>
     *   <li>비어 있거나 null이면 빈 문자열</li>
     *   <li>마지막 4자리 숫자만 남기고 나머지 숫자는 별표로 바꾼다. 이미 일부가 가려져 있어도(예: {@code 1234-****-****-5678})
     *       앞에 보이는 숫자까지 가린다. 하이픈 같은 구분자와 기존 별표는 그대로 둔다.</li>
     *   <li>숫자가 4자리 이하이면 그대로</li>
     * </ul>
     */
    public static String mask(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            return "";
        }
        String masked = maskDigits(value);
        return masked.length() > MAX_LENGTH ? masked.substring(masked.length() - MAX_LENGTH) : masked;
    }

    private static String maskDigits(String value) {
        StringBuilder result = new StringBuilder(value.length());
        int digitsFromEnd = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                result.append(digitsFromEnd < VISIBLE_DIGITS ? c : '*');
                digitsFromEnd++;
            } else {
                result.append(c);
            }
        }
        return result.reverse().toString();
    }

}
