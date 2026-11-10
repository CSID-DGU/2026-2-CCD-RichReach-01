package com.richreach.plan.optimizer;

import com.richreach.benefit.dto.BenefitTransaction;
import java.time.*;
import java.util.*;

/** Repeat the latest completed month's observed events, not the future replay file.
 * This deliberately small horizon is a forecast sample, not a full monthly budget. */
public final class HistoricalTransactionForecast {
    public List<BenefitTransaction> predict(LocalDateTime now,List<BenefitTransaction> observed) {
        if(observed.stream().anyMatch(t->!t.approvedAt().isBefore(now)))
            throw new IllegalArgumentException("Forecast history contains present/future events");
        YearMonth month=YearMonth.from(now),previous=month.minusMonths(1);
        List<BenefitTransaction> history=observed.stream().filter(t->YearMonth.from(t.approvedAt()).equals(previous))
            .sorted(Comparator.comparing(BenefitTransaction::approvedAt).thenComparing(BenefitTransaction::id)).toList();
        List<BenefitTransaction> result=new ArrayList<>();
        for(int offset=0;offset<=1;offset++) {
            YearMonth target=month.plusMonths(offset);int limit=offset==0?4:3,count=0;
            for(var tx:history) {
                LocalDateTime time=target.atDay(Math.min(tx.approvedAt().getDayOfMonth(),target.lengthOfMonth())).atTime(tx.approvedAt().toLocalTime());
                if(!time.isAfter(now))continue;
                Map<String,String> facts=new HashMap<>(tx.facts());facts.put("forecast","true");facts.put("forecast_source_id",tx.id());
                // A past posting delay is not evidence of a future posting time.
                result.add(new BenefitTransaction("forecast:"+target+":"+tx.id(),time,null,tx.amount(),tx.merchant(),tx.category(),tx.event(),facts));
                if(++count==limit)break;
            }
        }
        return List.copyOf(result);
    }
}
