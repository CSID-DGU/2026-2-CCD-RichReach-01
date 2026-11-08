package com.richreach.demo;

import com.richreach.transaction.dto.CollectedTransaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 시연용 거래 시나리오. 최근 7개 달(이번 달 포함)의 거래를 만들어서, 최근 1·3·6개월 조회가 모두 채워지게 한다.
 * DB 없이 거래 목록만 만드는 순수 계산이다.
 *
 * <ul>
 *   <li><b>항상 같은 결과</b>: 달마다 고정된 값으로 난수를 만들어서, 같은 달의 데이터는 언제 실행해도 똑같다.
 *       그래서 다시 실행해도 같은 거래가 중복으로 생기지 않는다. (이번 달은 어제까지의 거래만 있다.)</li>
 *   <li>업종은 지정하지 않는다. 가맹점 이름을 실제 상호로 만들어서 가맹점 분류 규칙이 업종을 정하게 한다.</li>
 *   <li>취소·환불(전액, 일부), 할부, 해외 결제가 섞여 있다.</li>
 *   <li>카드마다 소비 성향이 다르다. 외식·카페가 많은 카드, 교통·기타가 많은 카드, 쇼핑·편의점이 많은 카드로 나뉘어
 *       "어느 카드가 어느 업종에서 유리한지" 보여줄 수 있다.</li>
 * </ul>
 * 가짜 데이터라서 승인번호는 {@code DEMO-}로 시작한다.
 */
final class DemoScenario {

    /** 거래를 만드는 달의 수 (이번 달 포함). 6개월 조회가 부분 데이터로 표시되지 않게 하나 더 둔다. */
    static final int MONTHS = 7;

    static final String APPROVAL_PREFIX = "DEMO-";

    private static final long SEED = 20261109L;

    /** 업종 하나의 소비 성향. 한 달에 {@code perMonth}건, 금액은 {@code minAmount}~{@code maxAmount}원. */
    private record Pattern(String[] merchants, int perMonth, int minAmount, int maxAmount, int primaryCard) {

    }

    private static final List<Pattern> PATTERNS = List.of(
        new Pattern(new String[] {"맥도날드 강남점", "교촌치킨 역삼점", "김밥천국 서초점", "배달의민족", "본죽 선릉점", "명륜진사갈비",
            "서브웨이 강남점", "홍길동 곱창"}, 10, 8_000, 45_000, 0),
        new Pattern(new String[] {"스타벅스 강남점", "투썸플레이스 역삼점", "이디야커피", "메가MGC커피", "파리바게뜨 선릉점"},
            18, 3_000, 9_000, 0),
        new Pattern(new String[] {"쿠팡", "올리브영 명동점", "무신사", "11번가", "이마트 은평점", "다이소 강남점"},
            6, 10_000, 120_000, 2),
        new Pattern(new String[] {"티머니 지하철", "카카오T 택시", "GS칼텍스 강남주유소", "SK에너지 역삼주유소", "코레일"},
            22, 1_400, 60_000, 1),
        new Pattern(new String[] {"GS25 역삼점", "CU 강남점", "세븐일레븐 선릉점", "이마트24 서초점"}, 12, 2_000, 15_000, 2),
        new Pattern(new String[] {"CGV 용산", "대한항공", "넷플릭스", "알 수 없는 상점"}, 4, 5_000, 200_000, 1));

    private static final String[] SHOPPING_FOR_SPECIAL = {"쿠팡", "무신사", "올리브영 명동점", "이마트 은평점"};

    private DemoScenario() {
    }

    /**
     * 시연용 거래를 만든다.
     *
     * @param today     오늘. 오늘 이후의 거래는 만들지 않는다. (이번 달은 어제까지)
     * @param cardCount 보유카드 수(1 이상). 거래는 이 수만큼의 카드에 나누어 담는다.
     * @return 카드 순서대로의 거래 목록. 항상 {@code cardCount}개이다.
     */
    static List<List<CollectedTransaction>> generate(LocalDate today, int cardCount) {
        if (cardCount < 1) {
            throw new IllegalArgumentException("보유카드는 한 장 이상이어야 합니다.");
        }
        List<List<CollectedTransaction>> byCard = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            byCard.add(new ArrayList<>());
        }

        YearMonth current = YearMonth.from(today);
        for (int back = MONTHS - 1; back >= 0; back--) {
            YearMonth month = current.minusMonths(back);
            // 달마다 독립된 난수라서, 달력이 넘어가도 이미 만든 달의 데이터는 바뀌지 않는다.
            Random random = new Random(SEED ^ (month.getYear() * 100L + month.getMonthValue()));
            List<Draft> drafts = new ArrayList<>();

            for (Pattern pattern : PATTERNS) {
                for (int i = 0; i < pattern.perMonth(); i++) {
                    LocalDateTime at = month.atDay(1 + random.nextInt(month.lengthOfMonth()))
                        .atTime(8 + random.nextInt(15), random.nextInt(60));
                    String merchant = pattern.merchants()[random.nextInt(pattern.merchants().length)];
                    long amount = amount(random, pattern.minAmount(), pattern.maxAmount());
                    int card = random.nextInt(10) < 7 ? pattern.primaryCard() : random.nextInt(cardCount);
                    drafts.add(new Draft(at, merchant, amount, 0, 0, false, card));
                }
            }

            int shoppingCard = PATTERNS.get(2).primaryCard();
            // 전액 취소, 일부 취소, 3개월 할부, 해외 결제를 한 달에 한 건씩(할부는 격월, 해외는 짝수 달) 섞는다.
            long fullCancel = amount(random, 30_000, 80_000);
            drafts.add(special(random, month, shoppingCard, fullCancel, fullCancel, 0, false));
            drafts.add(special(random, month, shoppingCard, 50_000, 20_000, 0, false));
            if (month.getMonthValue() % 2 == 1) {
                drafts.add(special(random, month, shoppingCard, amount(random, 120_000, 400_000), 0, 3, false));
            }
            if (month.getMonthValue() % 2 == 0) {
                drafts.add(new Draft(month.atDay(1 + random.nextInt(month.lengthOfMonth())).atTime(14, 0),
                    "AMAZON.COM", amount(random, 20_000, 90_000), 0, 0, true, 1));
            }

            // 가장 오래된 달의 1일에 거래를 하나 둔다. 6개월 조회의 시작일보다 앞서 수집되었다고 보이게 한다.
            // 목록의 맨 끝에 더해서, 같은 달이 나중에 가장 오래된 달이 아니게 되어도 다른 거래의 승인번호 순번이 바뀌지 않는다.
            if (back == MONTHS - 1) {
                drafts.add(new Draft(month.atDay(1).atTime(9, 0), "스타벅스 강남점", 4_500, 0, 0, false, 0));
            }

            for (int index = 0; index < drafts.size(); index++) {
                Draft draft = drafts.get(index);
                // 오늘 이후(오늘 포함)의 거래는 만들지 않는다. 시각을 바꾸지 않으므로 다음 날 다시 실행해도 같은 거래다.
                if (!draft.at().toLocalDate().isBefore(today)) {
                    continue;
                }
                // 승인번호의 순번은 걸러내기 전 목록 기준이라, 나중에 다시 실행해도 같은 거래의 번호가 바뀌지 않는다.
                byCard.get(draft.card() % cardCount).add(new CollectedTransaction(
                    "%s%d%02d-%03d".formatted(APPROVAL_PREFIX, month.getYear(), month.getMonthValue(), index + 1),
                    draft.at(), draft.merchant(), draft.amount(), draft.canceledAmount(), draft.installmentMonths(),
                    draft.overseas(), null));
            }
        }
        return byCard;
    }

    /** 만들 거래 하나. 이번 달 날짜 필터를 거친 뒤 승인번호를 붙여 {@link CollectedTransaction}이 된다. */
    private record Draft(LocalDateTime at, String merchant, long amount, long canceledAmount, int installmentMonths,
                         boolean overseas, int card) {

    }

    private static Draft special(Random random, YearMonth month, int card, long amount, long canceled, int installment,
                                 boolean overseas) {
        LocalDateTime at = month.atDay(1 + random.nextInt(month.lengthOfMonth())).atTime(13, random.nextInt(60));
        String merchant = SHOPPING_FOR_SPECIAL[random.nextInt(SHOPPING_FOR_SPECIAL.length)];
        return new Draft(at, merchant, amount, canceled, installment, overseas, card);
    }

    /** 100원 단위로 맞춘 금액. */
    private static long amount(Random random, int min, int max) {
        return (min + random.nextInt(max - min + 1)) / 100 * 100L;
    }

}
