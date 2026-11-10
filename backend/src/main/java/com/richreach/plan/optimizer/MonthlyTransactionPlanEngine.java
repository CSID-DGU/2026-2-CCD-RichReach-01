package com.richreach.plan.optimizer;

import com.richreach.benefit.dto.BenefitTransaction;
import com.richreach.benefit.dto.TransactionBenefitRule;
import com.richreach.benefit.service.TransactionBenefitCalculator;
import com.richreach.global.category.SpendingCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A transaction-level monthly preview. It reads no files or database and never changes a recorded payment.
 * The caller supplies one completed month's history, this month's committed payments, confirmed budget,
 * owned-card IDs and structured benefit rules. Only forecast payments are allocated by a strategy.
 */
public final class MonthlyTransactionPlanEngine {
    public record Observed(BenefitTransaction transaction, long userCardId) {}
    public record Card(long userCardId, long cardProductId, List<TransactionBenefitRule> rules,
                       Long previousEligibleSpend, Map<String, String> facts) {
        public Card {
            rules = List.copyOf(rules);
            facts = facts == null ? Map.of() : Map.copyOf(facts);
        }
    }
    public record Input(YearMonth month, LocalDateTime asOf, List<Observed> history,
                        List<Observed> committed, List<Card> cards,
                        Map<SpendingCategory, Long> confirmedBudget, Duration timeLimit,
                        boolean allowUnverified) {
        public Input {
            history = List.copyOf(history);
            committed = List.copyOf(committed);
            cards = List.copyOf(cards);
            confirmedBudget = Map.copyOf(confirmedBudget);
        }
    }
    public record Forecast(BenefitTransaction transaction, long habitualUserCardId, String sourceId) {}
    public record Assignment(String transactionId, LocalDateTime approvedAt, SpendingCategory category,
                             long amount, long userCardId) {}
    public record Allocation(long userCardId, SpendingCategory category, long amount, long benefit) {}
    public record StrategyResult(String strategy, long totalBenefit, List<Allocation> allocations,
                                 List<Assignment> forecastAssignments, int optimal, int feasible, int fallback) {}
    public record Result(YearMonth month, LocalDateTime asOf, YearMonth sourceMonth,
                         Map<SpendingCategory, Long> historySpend,
                         Map<SpendingCategory, Long> requestedBudget,
                         Map<SpendingCategory, Long> effectiveBudget,
                         List<Forecast> forecast, Map<String, StrategyResult> strategies,
                         String selectedStrategy, List<String> notes) {}

    private final TransactionBenefitCalculator calculator;
    private final TransactionCardOptimizer optimizer;

    public MonthlyTransactionPlanEngine(TransactionBenefitCalculator calculator) {
        this.calculator = calculator;
        this.optimizer = new TransactionCardOptimizer(calculator);
    }

    public Result create(Input input) {
        validate(input);
        Map<SpendingCategory, Long> historySpend = totals(input.history());
        Map<SpendingCategory, Long> alreadySpent = totals(input.committed());
        Map<SpendingCategory, Long> effective = new EnumMap<>(SpendingCategory.class);
        List<String> notes = new ArrayList<>();
        for (SpendingCategory category : SpendingCategory.values()) {
            long requested = input.confirmedBudget().getOrDefault(category, 0L);
            long committed = alreadySpent.getOrDefault(category, 0L);
            effective.put(category, Math.max(requested, committed));
            if (committed > requested) notes.add(category + " already exceeds its confirmed budget; recorded payments remain fixed.");
        }
        List<Forecast> forecast = forecast(input, effective, alreadySpent);
        Map<String, StrategyResult> strategies = new LinkedHashMap<>();
        for (String strategy : List.of("HABIT", "IMMEDIATE", "CP_SAT"))
            strategies.put(strategy, evaluate(input, forecast, strategy));
        String selected = strategies.values().stream()
            .max(Comparator.comparingLong(StrategyResult::totalBenefit)
                .thenComparingInt(result -> switch (result.strategy()) {
                    case "CP_SAT" -> 2;
                    case "IMMEDIATE" -> 1;
                    default -> 0;
                })).orElseThrow().strategy();
        notes.add("The forecast repeats the latest completed month, then scales each category to the confirmed remaining budget.");
        notes.add("Unknown merchant or exclusion facts produce unvalued benefits; future spending and next-month performance are uncertain.");
        return new Result(input.month(), input.asOf(), input.month().minusMonths(1),
            Map.copyOf(historySpend), Map.copyOf(input.confirmedBudget()), Map.copyOf(effective),
            List.copyOf(forecast), Map.copyOf(strategies), selected, List.copyOf(notes));
    }

    private static void validate(Input input) {
        if (input.month() == null || input.asOf() == null || !YearMonth.from(input.asOf()).equals(input.month()))
            throw new IllegalArgumentException("asOf must be inside the target month");
        if (input.cards().isEmpty() || input.cards().size() > 10 || input.timeLimit() == null
            || input.timeLimit().isNegative() || input.timeLimit().isZero())
            throw new IllegalArgumentException("1..10 cards and a positive time limit are required");
        Set<Long> cardIds = new HashSet<>();
        for (Card card : input.cards()) {
            if (card.userCardId() <= 0 || card.cardProductId() <= 0 || !cardIds.add(card.userCardId())
                || card.rules().isEmpty() || card.previousEligibleSpend() != null && card.previousEligibleSpend() < 0)
                throw new IllegalArgumentException("Cards need distinct positive IDs and structured rules");
        }
        input.confirmedBudget().forEach((category, value) -> {
            if (category == null || value == null || value < 0)
                throw new IllegalArgumentException("Invalid confirmed budget");
        });
        if (input.history().isEmpty()) throw new IllegalArgumentException("A completed source month is required");
        Set<String> transactionIds = new HashSet<>();
        for (Observed item : input.history()) {
            if (!cardIds.contains(item.userCardId())
                || !YearMonth.from(item.transaction().approvedAt()).equals(input.month().minusMonths(1))
                || !transactionIds.add(item.transaction().id()))
                throw new IllegalArgumentException("History must belong to the previous month and selected cards");
            category(item.transaction());
        }
        for (Observed item : input.committed()) {
            if (!cardIds.contains(item.userCardId())
                || !YearMonth.from(item.transaction().approvedAt()).equals(input.month())
                || !item.transaction().approvedAt().isBefore(input.asOf())
                || !transactionIds.add(item.transaction().id()))
                throw new IllegalArgumentException("Committed payments must precede asOf in the target month");
            category(item.transaction());
        }
    }

    private static SpendingCategory category(BenefitTransaction transaction) {
        return SpendingCategory.fromCode(transaction.category()).orElse(SpendingCategory.ETC);
    }

    private static Map<SpendingCategory, Long> totals(List<Observed> items) {
        Map<SpendingCategory, Long> totals = new EnumMap<>(SpendingCategory.class);
        for (Observed item : items)
            totals.merge(category(item.transaction()), item.transaction().amount(), Math::addExact);
        return totals;
    }

    private static List<Forecast> forecast(Input input, Map<SpendingCategory, Long> effective,
                                            Map<SpendingCategory, Long> alreadySpent) {
        List<Forecast> output = new ArrayList<>();
        for (SpendingCategory category : SpendingCategory.values()) {
            long remaining = effective.get(category) - alreadySpent.getOrDefault(category, 0L);
            if (remaining == 0) continue;
            List<Observed> template = input.history().stream().filter(item -> category(item.transaction()) == category)
                .filter(item -> !projectedTime(item.transaction(), input.month()).isBefore(input.asOf()))
                .sorted(Comparator.comparing((Observed item) -> projectedTime(item.transaction(), input.month()))
                    .thenComparing(item -> item.transaction().id())).toList();
            long total = template.stream().mapToLong(item -> item.transaction().amount()).sum();
            if (total == 0) {
                LocalDateTime time = input.asOf().plusSeconds(1);
                if (!YearMonth.from(time).equals(input.month())) time = input.asOf();
                Map<String, String> facts = Map.of("forecast", "true");
                BenefitTransaction generic = new BenefitTransaction("plan:" + input.month() + ":" + category,
                    time, null, remaining, "", category.name(), "PURCHASE", facts);
                output.add(new Forecast(generic, input.cards().getFirst().userCardId(), ""));
                continue;
            }
            long allocated = 0;
            for (int i = 0; i < template.size(); i++) {
                Observed source = template.get(i);
                long amount = i == template.size() - 1 ? remaining - allocated
                    : BigDecimal.valueOf(remaining).multiply(BigDecimal.valueOf(source.transaction().amount()))
                        .divide(BigDecimal.valueOf(total), 0, RoundingMode.DOWN).longValueExact();
                allocated = Math.addExact(allocated, amount);
                if (amount == 0) continue;
                Map<String, String> facts = new HashMap<>(source.transaction().facts());
                facts.put("forecast", "true");
                facts.put("forecast_source_id", source.transaction().id());
                BenefitTransaction predicted = new BenefitTransaction(
                    "plan:" + input.month() + ":" + source.transaction().id(),
                    projectedTime(source.transaction(), input.month()), null, amount,
                    source.transaction().merchant(), category.name(), source.transaction().event(), facts);
                output.add(new Forecast(predicted, source.userCardId(), source.transaction().id()));
            }
        }
        output.sort(Comparator.comparing((Forecast item) -> item.transaction().approvedAt())
            .thenComparing(item -> item.transaction().id()));
        return List.copyOf(output);
    }

    private static LocalDateTime projectedTime(BenefitTransaction transaction, YearMonth target) {
        return target.atDay(Math.min(transaction.approvedAt().getDayOfMonth(), target.lengthOfMonth()))
            .atTime(transaction.approvedAt().toLocalTime());
    }

    private StrategyResult evaluate(Input input, List<Forecast> forecast, String strategy) {
        Map<Long, Card> cards = new LinkedHashMap<>();
        Map<Long, List<BenefitTransaction>> state = new LinkedHashMap<>();
        input.cards().stream().sorted(Comparator.comparingLong(Card::userCardId)).forEach(card -> {
            cards.put(card.userCardId(), card);
            state.put(card.userCardId(), new ArrayList<>());
        });
        for (Observed item : input.committed()) state.get(item.userCardId()).add(item.transaction());
        List<Assignment> assignments = new ArrayList<>();
        int optimal = 0, feasible = 0, fallback = 0;
        long deadline = System.nanoTime() + input.timeLimit().toNanos();
        for (int i = 0; i < forecast.size(); i++) {
            Forecast item = forecast.get(i);
            long chosen;
            if (strategy.equals("HABIT")) chosen = item.habitualUserCardId();
            else {
                List<TransactionCardOptimizer.Card> candidates = cards.values().stream()
                    .map(card -> new TransactionCardOptimizer.Card(card.userCardId(), card.rules(),
                        state.get(card.userCardId()), card.previousEligibleSpend(), card.facts())).toList();
                if (strategy.equals("IMMEDIATE") || System.nanoTime() >= deadline) {
                    chosen = optimizer.immediate(item.transaction(), candidates, input.allowUnverified()).cardId();
                    if (strategy.equals("CP_SAT")) fallback++;
                } else {
                    List<BenefitTransaction> lookahead = new ArrayList<>();
                    for (int j = i + 1; j < forecast.size() && lookahead.size() < 2; j++)
                        if (forecast.get(j).transaction().approvedAt().isAfter(item.transaction().approvedAt()))
                            lookahead.add(forecast.get(j).transaction());
                    double seconds = Math.max(0.001, Math.min(0.1, (deadline - System.nanoTime()) / 1e9));
                    TransactionCardOptimizer.Choice choice = optimizer.cpSat(item.transaction(), lookahead,
                        candidates, input.allowUnverified(), seconds);
                    chosen = choice.cardId();
                    if (choice.status().equals("OPTIMAL")) optimal++;
                    else if (choice.status().equals("FEASIBLE")) feasible++;
                    else fallback++;
                }
            }
            state.get(chosen).add(item.transaction());
            assignments.add(new Assignment(item.transaction().id(), item.transaction().approvedAt(),
                category(item.transaction()), item.transaction().amount(), chosen));
        }
        Map<String, Long> amounts = new HashMap<>();
        Map<String, Long> benefits = new HashMap<>();
        long totalBenefit = 0;
        for (Card card : cards.values()) {
            List<BenefitTransaction> transactions = state.get(card.userCardId());
            Map<String, SpendingCategory> categories = new HashMap<>();
            for (BenefitTransaction transaction : transactions) {
                SpendingCategory category = category(transaction);
                categories.put(transaction.id(), category);
                amounts.merge(card.userCardId() + ":" + category, transaction.amount(), Math::addExact);
            }
            TransactionBenefitCalculator.Result calculated = calculator.calculate(card.rules(), transactions,
                card.previousEligibleSpend(), card.facts(), input.allowUnverified());
            totalBenefit = Math.addExact(totalBenefit, calculated.total());
            for (TransactionBenefitCalculator.Line line : calculated.lines()) {
                if (line.benefit() > 0) {
                    SpendingCategory category = categories.get(line.transactionId());
                    benefits.merge(card.userCardId() + ":" + category, line.benefit(), Math::addExact);
                }
            }
        }
        List<Allocation> allocations = new ArrayList<>();
        for (Card card : cards.values()) for (SpendingCategory category : SpendingCategory.values()) {
            String key = card.userCardId() + ":" + category;
            long amount = amounts.getOrDefault(key, 0L);
            if (amount > 0) allocations.add(new Allocation(card.userCardId(), category, amount,
                benefits.getOrDefault(key, 0L)));
        }
        if (allocations.stream().mapToLong(Allocation::benefit).sum() != totalBenefit)
            throw new IllegalStateException("Benefit attribution does not equal the rule calculator");
        return new StrategyResult(strategy, totalBenefit, List.copyOf(allocations),
            List.copyOf(assignments), optimal, feasible, fallback);
    }
}
