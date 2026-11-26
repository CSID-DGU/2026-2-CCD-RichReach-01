package com.richreach.plan.optimizer;

import com.richreach.benefit.dto.BenefitPurchase;
import com.richreach.global.category.SpendingCategory;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Turns last month's individual purchases into budget-preserving forecast units. */
public final class ForecastPurchaseBuilder {
    private ForecastPurchaseBuilder() {
    }

    private record Share(BenefitPurchase source, long base, BigInteger remainder) {
    }

    public static List<BenefitPurchase> build(YearMonth targetMonth, Map<SpendingCategory, Long> budget,
                                               List<BenefitPurchase> historical, LocalDateTime earliest) {
        Map<SpendingCategory, List<BenefitPurchase>> byCategory = new EnumMap<>(SpendingCategory.class);
        historical.stream().filter(purchase -> purchase.amount() > 0)
            .forEach(purchase -> byCategory.computeIfAbsent(purchase.category(), ignored -> new ArrayList<>())
                .add(purchase));
        List<BenefitPurchase> result = new ArrayList<>();
        long nextId = -1;
        for (SpendingCategory category : SpendingCategory.values()) {
            long amount = budget.getOrDefault(category, 0L);
            if (amount <= 0) continue;
            List<BenefitPurchase> sources = byCategory.getOrDefault(category, List.of());
            if (sources.isEmpty()) {
                result.add(new BenefitPurchase(nextId--, earliest, category, amount, false, true));
                continue;
            }
            long sourceTotal = sources.stream().mapToLong(BenefitPurchase::amount).sum();
            List<Share> shares = new ArrayList<>();
            long assigned = 0;
            for (BenefitPurchase source : sources) {
                BigInteger[] divided = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(source.amount()))
                    .divideAndRemainder(BigInteger.valueOf(sourceTotal));
                long base = divided[0].longValueExact();
                assigned = Math.addExact(assigned, base);
                shares.add(new Share(source, base, divided[1]));
            }
            Map<Long, Long> extra = new java.util.HashMap<>();
            shares.stream().sorted(Comparator.comparing(Share::remainder).reversed()
                .thenComparing(share -> share.source().approvedAt())
                .thenComparingLong(share -> share.source().id()))
                .limit(amount - assigned).forEach(share -> extra.put(share.source().id(), 1L));
            for (Share share : shares) {
                long scaled = share.base() + extra.getOrDefault(share.source().id(), 0L);
                if (scaled == 0) continue;
                LocalDateTime projected = targetMonth.atDay(Math.min(share.source().approvedAt().getDayOfMonth(),
                    targetMonth.lengthOfMonth())).atTime(share.source().approvedAt().toLocalTime());
                result.add(new BenefitPurchase(nextId--, projected.isBefore(earliest) ? earliest : projected,
                    category, scaled, share.source().merchantIdentified(), true));
            }
        }
        return List.copyOf(result);
    }
}
