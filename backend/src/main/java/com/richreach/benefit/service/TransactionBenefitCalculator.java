package com.richreach.benefit.service;

import com.richreach.benefit.dto.BenefitTransaction;
import com.richreach.benefit.dto.TransactionBenefitRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** One evaluator shared by recorded usage, immediate selection and CP-SAT objectives. */
public final class TransactionBenefitCalculator {
    public enum Match { YES, NO, UNKNOWN }
    public record Line(String transactionId, String ruleId, String status, long benefit, String reason) {}
    public record Result(long total, List<Line> lines, Map<String, Long> byRule) {}

    private static String normalize(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-Z가-힣]", "");
    }
    private static Match and(Match a, Match b) {
        return a == Match.NO || b == Match.NO ? Match.NO : a == Match.UNKNOWN || b == Match.UNKNOWN ? Match.UNKNOWN : Match.YES;
    }
    private static Match facts(JsonNode wanted, BenefitTransaction tx) {
        Match result = Match.YES;
        if (!wanted.isObject()) return result;
        for (var property : wanted.properties()) {
            String actual = tx.facts().get(property.getKey());
            result = and(result, actual == null ? Match.UNKNOWN : actual.equals(property.getValue().asText()) ? Match.YES : Match.NO);
        }
        return result;
    }
    private static Match clause(JsonNode c, BenefitTransaction tx) {
        Match result = facts(c.path("facts"), tx);
        if (c.has("category")) result = and(result, tx.category() == null ? Match.UNKNOWN : c.path("category").asText().equals(tx.category()) ? Match.YES : Match.NO);
        if (c.has("merchants")) {
            String merchant = normalize(tx.merchant());
            boolean found = false;
            for (JsonNode name : c.path("merchants")) if (merchant.contains(normalize(name.asText()))) found = true;
            result = and(result, merchant.isEmpty() ? Match.UNKNOWN : found ? Match.YES : Match.NO);
        }
        return result;
    }
    private static Match any(JsonNode alternatives, BenefitTransaction tx) {
        if (!alternatives.isArray()) return Match.YES;
        boolean unknown = false;
        for (JsonNode c : alternatives) {
            Match m = clause(c, tx);
            if (m == Match.YES) return Match.YES;
            unknown |= m == Match.UNKNOWN;
        }
        return unknown ? Match.UNKNOWN : Match.NO;
    }
    private static Match eligibility(TransactionBenefitRule r, BenefitTransaction tx, Long previous,
                                     Map<String,String> cardFacts) {
        JsonNode s = r.spec();
        if (!s.path("event").asText("PURCHASE").equals(tx.event())) return Match.NO;
        if (tx.amount() < s.path("minimum_amount").asLong(0)) return Match.NO;
        Match result = and(facts(s.path("facts"),tx), any(s.path("any"),tx));
        if (s.has("exclusions")) {
            String tag = tx.facts().get("exclusion_class");
            if (tag == null) result = and(result, Match.UNKNOWN);
            else for (JsonNode excluded : s.path("exclusions")) if (excluded.asText().equals(tag)) return Match.NO;
        }
        if (r.previousMonthMin() != null && (previous == null || previous < r.previousMonthMin())) {
            if (s.path("new_card_grace").asBoolean(false)) {
                String grace = cardFacts.get("grace_active");
                if (!"true".equals(grace)) result = and(result, grace == null || previous == null ? Match.UNKNOWN : Match.NO);
            } else result = and(result, previous == null ? Match.UNKNOWN : Match.NO);
        }
        if (s.has("windows")) {
            LocalDateTime time = tx.approvedAt();
            if ("true".equals(tx.facts().get("needs_posted_time"))) time = tx.postedAt();
            if (time == null) return and(result, Match.UNKNOWN);
            int minute = time.getHour()*60 + time.getMinute();
            boolean found = false;
            for (JsonNode window : s.path("windows")) if (minute >= window.get(0).asInt() && minute < window.get(1).asInt()) found = true;
            if (!found) return Match.NO;
        }
        if (s.path("posted_basis_for_special").asBoolean(false)
            && "true".equals(tx.facts().get("needs_posted_time"))) {
            if(tx.postedAt()==null || !java.time.YearMonth.from(tx.postedAt()).equals(java.time.YearMonth.from(tx.approvedAt())))
                result=and(result,Match.UNKNOWN);
        }
        return result;
    }
    private static int area(JsonNode areas, BenefitTransaction tx) {
        for (int i=0;i<areas.size();i++) if(any(areas.get(i),tx)==Match.YES) return i;
        return -1;
    }
    private static long sharedCap(TransactionBenefitRule r, Long previous, Map<String,String> cardFacts) {
        long cap = r.sharedMonthlyLimit() == null ? Long.MAX_VALUE : r.sharedMonthlyLimit();
        if(r.spec().has("cap_tiers")) {
            long spend = previous == null ? -1 : previous;
            if("true".equals(cardFacts.get("grace_active"))) spend=Math.max(spend,r.spec().path("grace_spend").asLong(0));
            cap=0;
            for(JsonNode tier:r.spec().path("cap_tiers")) if(spend>=tier.get(0).asLong()) cap=tier.get(1).asLong();
        }
        return cap;
    }
    private static Long rawBenefit(TransactionBenefitRule r, BenefitTransaction tx) {
        String kind=r.spec().path("kind").asText("MONEY");
        if(kind.equals("ENTITLEMENT")) return null;
        if(kind.equals("FEE_WAIVER") || kind.equals("FX_FEE_DISCOUNT")) {
            String fee=tx.facts().get(kind.equals("FEE_WAIVER")?"fee_krw":"fx_fee_krw");
            if(fee==null) return null;
            long amount=Long.parseLong(fee);
            return kind.equals("FEE_WAIVER")?amount:BigDecimal.valueOf(amount).multiply(r.ratePercent()).divide(BigDecimal.valueOf(100),0,RoundingMode.FLOOR).longValueExact();
        }
        if("USD".equals(r.rewardUnit())) {
            String fx=tx.facts().get("usd_to_krw");
            return fx==null?null:BigDecimal.valueOf(r.amount()).multiply(new BigDecimal(fx)).setScale(0,RoundingMode.FLOOR).longValueExact();
        }
        if(r.amount()!=null) return r.amount();
        if(r.ratePercent()!=null) return BigDecimal.valueOf(tx.amount()).multiply(r.ratePercent()).divide(BigDecimal.valueOf(100),0,RoundingMode.FLOOR).longValueExact();
        return null;
    }

    /** Re-evaluates one calendar month, so rank changes and caps use the same rules for all strategies. */
    public Result calculate(List<TransactionBenefitRule> rules, List<BenefitTransaction> events,
                            Long previousSpend, Map<String,String> cardFacts, boolean allowUnverified) {
        if(events.stream().map(t->t.approvedAt().toLocalDate().withDayOfMonth(1)).distinct().count()>1)
            throw new IllegalArgumentException("One calendar month per evaluation");
        List<BenefitTransaction> sorted=events.stream().sorted(Comparator.comparing(BenefitTransaction::approvedAt).thenComparing(BenefitTransaction::id)).toList();
        Map<String,Long> paid=new HashMap<>(), dailyPaid=new HashMap<>(), groupPaid=new HashMap<>();
        Map<String,Integer> counts=new HashMap<>(), dailyCounts=new HashMap<>();
        List<Line> lines=new ArrayList<>(); long total=0;
        for(BenefitTransaction tx:sorted) {
            Map<String,TransactionBenefitRule> exclusive=new HashMap<>();
            List<TransactionBenefitRule> eligible=new ArrayList<>();
            for(TransactionBenefitRule r:rules) {
                if(!r.ready() && !allowUnverified) {lines.add(new Line(tx.id(),r.id(),"UNVERIFIED",0,"Rule not approved"));continue;}
                Match m=eligibility(r,tx,previousSpend,cardFacts);
                if(m==Match.NO) {lines.add(new Line(tx.id(),r.id(),"NOT_APPLICABLE",0,"Event or condition not met"));continue;}
                if(m==Match.UNKNOWN) {lines.add(new Line(tx.id(),r.id(),"UNKNOWN",0,"Required fact missing"));continue;}
                if(r.spec().has("rank")) {
                    JsonNode areas=r.spec().path("areas");
                    long[] spends=new long[areas.size()];
                    boolean rankUncertain=false;
                    for(BenefitTransaction x:sorted) if("PURCHASE".equals(x.event())) {
                        Match matched=eligibility(r,x,previousSpend,cardFacts);
                        if(matched==Match.NO)continue;
                        int a=area(areas,x);
                        if(matched==Match.YES && a>=0) spends[a]+=x.amount();
                        else if(matched==Match.UNKNOWN || a<0) {
                            for(JsonNode alternatives:areas)if(any(alternatives,x)==Match.UNKNOWN)rankUncertain=true;
                        }
                    }
                    if(rankUncertain){lines.add(new Line(tx.id(),r.id(),"UNKNOWN",0,"Area ranking depends on missing facts"));continue;}
                    int current=area(areas,tx);
                    List<Integer> ranked=new ArrayList<>();
                    for(int a=0;a<spends.length;a++) if(spends[a]>0) ranked.add(a);
                    ranked.sort(Comparator.<Integer>comparingLong(a->-spends[a]).thenComparingInt(a->a));
                    if(current<0 || ranked.indexOf(current)+1!=r.spec().path("rank").asInt()) {
                        lines.add(new Line(tx.id(),r.id(),"NOT_APPLICABLE",0,"Outside ranked area"));continue;
                    }
                }
                String group=r.spec().path("exclusive_group").asText("");
                if(group.isBlank()) eligible.add(r);
                else {
                    TransactionBenefitRule prior=exclusive.get(group);
                    if(prior==null || r.spec().path("priority").asInt()>prior.spec().path("priority").asInt()) {
                        if(prior!=null) lines.add(new Line(tx.id(),prior.id(),"SUPERSEDED",0,"Exclusive rule priority"));
                        exclusive.put(group,r);
                    } else lines.add(new Line(tx.id(),r.id(),"SUPERSEDED",0,"Exclusive rule priority"));
                }
            }
            eligible.addAll(exclusive.values());
            eligible.sort(Comparator.comparing(TransactionBenefitRule::id));
            for(TransactionBenefitRule r:eligible) {
                String day=tx.approvedAt().toLocalDate()+":"+r.id();
                if(r.annualCountLimit()!=null && !cardFacts.containsKey("annual_count:"+r.id())) {
                    lines.add(new Line(tx.id(),r.id(),"UNKNOWN",0,"Annual usage history required"));continue;
                }
                int yearly=Integer.parseInt(cardFacts.getOrDefault("annual_count:"+r.id(),"0"));
                if((r.monthlyCountLimit()!=null && counts.getOrDefault(r.id(),0)>=r.monthlyCountLimit())
                    ||(r.dailyCountLimit()!=null && dailyCounts.getOrDefault(day,0)>=r.dailyCountLimit())
                    ||(r.annualCountLimit()!=null && yearly+counts.getOrDefault(r.id(),0)>=r.annualCountLimit())) {
                    lines.add(new Line(tx.id(),r.id(),"LIMIT_REACHED",0,"Frequency limit"));continue;
                }
                Long raw=rawBenefit(r,tx);
                if("ENTITLEMENT".equals(r.spec().path("kind").asText())) {
                    counts.merge(r.id(),1,Integer::sum);dailyCounts.merge(day,1,Integer::sum);
                    lines.add(new Line(tx.id(),r.id(),"ELIGIBLE_SERVICE",0,"Non-monetary benefit; not valued as cash"));continue;
                }
                if(raw==null) {lines.add(new Line(tx.id(),r.id(),"UNVALUED_SERVICE",0,"Entitlement or valuation fact required"));continue;}
                long value=raw;
                if(r.monthlyLimit()!=null) value=Math.min(value,Math.max(0,r.monthlyLimit()-paid.getOrDefault(r.id(),0L)));
                if(r.dailyLimit()!=null) value=Math.min(value,Math.max(0,r.dailyLimit()-dailyPaid.getOrDefault(day,0L)));
                if(r.capGroupId()!=null && !r.capGroupId().isBlank()) value=Math.min(value,Math.max(0,sharedCap(r,previousSpend,cardFacts)-groupPaid.getOrDefault(r.capGroupId(),0L)));
                if(value<0) throw new IllegalArgumentException("Negative benefit");
                if(value>0) {
                    paid.merge(r.id(),value,Long::sum);dailyPaid.merge(day,value,Long::sum);
                    if(r.capGroupId()!=null) groupPaid.merge(r.capGroupId(),value,Long::sum);
                    counts.merge(r.id(),1,Integer::sum);dailyCounts.merge(day,1,Integer::sum);total+=value;
                }
                lines.add(new Line(tx.id(),r.id(),value>0?"APPLIED":"LIMIT_REACHED",value,""));
            }
        }
        return new Result(total,List.copyOf(lines),Map.copyOf(paid));
    }

    /** Null means eligible spend cannot be established, never a zero default. */
    public Long eligibleSpend(List<TransactionBenefitRule> rules,List<BenefitTransaction> events) {
        JsonNode exclusions=rules.stream().map(TransactionBenefitRule::spec).filter(s->s.has("performance_exclusions"))
            .findFirst().orElseThrow(()->new IllegalArgumentException("Performance rules missing")).path("performance_exclusions");
        long total=0;
        for(var tx:events) {
            if(!"PURCHASE".equals(tx.event()))continue;
            String tag=tx.facts().get("exclusion_class");if(tag==null)return null;
            if("true".equals(tx.facts().get("needs_posted_time")) &&
                (tx.postedAt()==null || !java.time.YearMonth.from(tx.postedAt()).equals(java.time.YearMonth.from(tx.approvedAt()))))return null;
            boolean excluded=false;for(var e:exclusions)if(e.asText().equals(tag))excluded=true;
            if(!excluded)total=Math.addExact(total,tx.amount());
        }
        return total;
    }
}
