package com.richreach.plan.optimizer;

import com.richreach.benefit.dto.*;
import com.richreach.benefit.service.TransactionBenefitCalculator;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionCardOptimizerTest {
    final TransactionBenefitCalculator calc=new TransactionBenefitCalculator();
    TransactionBenefitRule rule(String id,String rate,Long fixed,Long cap,Integer daily,Integer monthly,Long previous,String body){
        return new TransactionBenefitRule(id,id,rate==null?null:new BigDecimal(rate),fixed,cap,null,daily,monthly,null,previous,
            null,null,"KRW",true,TransactionBenefitRule.parse("{\"schema\":\"card-benefit-v1\","+body+"}"));
    }
    BenefitTransaction tx(String id,String date,long amount,Map<String,String> facts){return new BenefitTransaction(id,LocalDateTime.parse(date),null,amount,"CAFE","CAFE","PURCHASE",facts);}
    long total(List<TransactionBenefitRule> r,List<BenefitTransaction> t,Long p){return calc.calculate(r,t,p,Map.of("grace_active","false"),false).total();}
    @Test void fixedMinimumAndDailyMonthlyCount(){
        var r=rule("fixed",null,1000L,null,1,2,200000L,"\"minimum_amount\":10000");
        var t=List.of(tx("1","2026-09-01T10:00:00",9999,Map.of()),tx("2","2026-09-01T11:00:00",10000,Map.of()),tx("3","2026-09-01T12:00:00",20000,Map.of()),tx("4","2026-09-02T12:00:00",20000,Map.of()),tx("5","2026-09-03T12:00:00",20000,Map.of()));
        assertEquals(2000,total(List.of(r),t,200000L));assertEquals(0,total(List.of(r),t,199999L));
    }
    @Test void timeBoundariesAndMutualExclusion(){
        var base=rule("base","0.2",null,null,null,null,null,"\"exclusive_group\":\"x\",\"priority\":0");
        var time=rule("time","0.6",null,null,null,null,300000L,"\"exclusive_group\":\"x\",\"priority\":10,\"windows\":[[720,840],[1080,1200]]");
        for(String clock:List.of("12:00:00","13:59:59","18:00:00","19:59:59"))assertEquals(60,total(List.of(base,time),List.of(tx(clock,"2026-09-01T"+clock,10000,Map.of())),300000L));
        for(String clock:List.of("11:59:59","14:00:00","17:59:59","20:00:00"))assertEquals(20,total(List.of(base,time),List.of(tx(clock,"2026-09-01T"+clock,10000,Map.of())),300000L));
    }
    @Test void unknownFactsAreNotFalseOrZeroBenefits(){
        var r=rule("auto",null,2000L,null,null,null,null,"\"facts\":{\"autopay\":\"true\"}");
        var result=calc.calculate(List.of(r),List.of(tx("1","2026-09-01T10:00:00",50000,Map.of())),null,Map.of(),false);
        assertEquals("UNKNOWN",result.lines().getFirst().status());
    }
    @Test void unknownPreviousAndNewCardGrace(){
        var r=rule("grace","1",null,null,null,null,300000L,"\"new_card_grace\":true");
        var t=List.of(tx("1","2026-09-01T10:00:00",10000,Map.of()));
        assertEquals("UNKNOWN",calc.calculate(List.of(r),t,null,Map.of(),false).lines().getFirst().status());
        assertEquals(100,calc.calculate(List.of(r),t,0L,Map.of("grace_active","true"),false).total());
    }
    @Test void unknownPostingTimeCannotQualifyForTimeBonus(){
        var r=rule("time","0.6",null,null,null,null,null,"\"windows\":[[720,840]]");
        assertEquals("UNKNOWN",calc.calculate(List.of(r),List.of(tx("1","2026-09-01T12:00:00",10000,Map.of("needs_posted_time","true"))),null,Map.of(),false).lines().getFirst().status());
    }
    @Test void performanceExclusionIsSeparateFromBenefitExclusion(){
        var r=rule("r","1",null,null,null,null,null,"\"exclusions\":[\"TAX\"],\"performance_exclusions\":[\"GIFT_CARD\"]");
        var t=List.of(tx("1","2026-09-01T12:00:00",10000,Map.of("exclusion_class","TAX")));
        assertEquals(0,total(List.of(r),t,0L));assertEquals(10000L,calc.eligibleSpend(List.of(r),t));
        assertNull(calc.eligibleSpend(List.of(r),List.of(tx("2","2026-09-01T12:00:00",10000,Map.of()))));
    }
    @Test void rankTieUsesAreaOrderAndRecomputesAtMonthEnd(){
        var r=rule("smart","0.4",null,5000L,null,null,200000L,"\"rank\":1,\"areas\":[[{\"category\":\"CAFE\"}],[{\"category\":\"SHOP\"}]]");
        var cafe=tx("1","2026-09-01T10:00:00",10000,Map.of());
        var shop=new BenefitTransaction("2",LocalDateTime.parse("2026-09-02T10:00:00"),null,10000,"shop","SHOP","PURCHASE",Map.of());
        var result=calc.calculate(List.of(r),List.of(cafe,shop),200000L,Map.of(),false);
        assertEquals(40,result.total());assertTrue(result.lines().stream().anyMatch(l->l.transactionId().equals("1")&&l.benefit()==40));
        var extra=new BenefitTransaction("3",LocalDateTime.parse("2026-09-03T10:00:00"),null,5000,"shop","SHOP","PURCHASE",Map.of());
        assertEquals(60,total(List.of(r),List.of(cafe,shop,extra),200000L));
    }
    @Test void cpMatchesBruteForceForSameHorizon(){
        var a=rule("a","1",null,100L,null,null,null,"\"performance_exclusions\":[]");
        var b=rule("b",null,80L,null,1,null,null,"\"minimum_amount\":5000,\"performance_exclusions\":[]");
        var cards=List.of(new TransactionCardOptimizer.Card(1,List.of(a),List.of(),0L,Map.of()),new TransactionCardOptimizer.Card(2,List.of(b),List.of(),0L,Map.of()));
        var current=tx("1","2026-09-01T10:00:00",5000,Map.of("exclusion_class","NONE"));
        var future=List.of(tx("2","2026-09-01T12:00:00",10000,Map.of("forecast","true","exclusion_class","NONE")),tx("3","2026-09-02T10:00:00",5000,Map.of("forecast","true","exclusion_class","NONE")));
        var optimizer=new TransactionCardOptimizer(calc);var result=optimizer.cpSat(current,future,cards,false,5);
        var events=new ArrayList<>(future);events.addFirst(current);long best=0;
        for(int mask=0;mask<8;mask++){List<BenefitTransaction> aa=new ArrayList<>(),bb=new ArrayList<>();for(int i=0;i<3;i++)((mask&(1<<i))==0?aa:bb).add(events.get(i));best=Math.max(best,optimizer.objective(cards.get(0),YearMonth.of(2026,9),aa,false)+optimizer.objective(cards.get(1),YearMonth.of(2026,9),bb,false));}
        assertEquals("OPTIMAL",result.status());assertEquals(best,result.modeledBenefit());assertEquals(best,result.bound());
    }
    @Test void forecastRejectsFutureLeakage(){
        var now=LocalDateTime.parse("2026-09-10T12:00:00");
        var f=new HistoricalTransactionForecast();
        assertThrows(IllegalArgumentException.class,()->f.predict(now,List.of(tx("future","2026-09-11T12:00:00",10000,Map.of()))));
        var list=f.predict(now,List.of(tx("old","2026-08-20T12:00:00",10000,Map.of())));
        assertEquals(2,list.size());assertTrue(list.stream().allMatch(t->t.approvedAt().isAfter(now)&&"true".equals(t.facts().get("forecast"))));
    }
    @Test void unmarkedForecastIsRejected(){
        var r=rule("r","1",null,null,null,null,null,"\"performance_exclusions\":[]");
        var card=new TransactionCardOptimizer.Card(1,List.of(r),List.of(),0L,Map.of());
        assertThrows(IllegalArgumentException.class,()->new TransactionCardOptimizer(calc).cpSat(tx("a","2026-09-01T10:00:00",1000,Map.of()),List.of(tx("b","2026-09-02T10:00:00",1000,Map.of())),List.of(card),false,1));
    }
}
