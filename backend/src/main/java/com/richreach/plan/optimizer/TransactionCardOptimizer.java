package com.richreach.plan.optimizer;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import com.richreach.benefit.dto.*;
import com.richreach.benefit.service.TransactionBenefitCalculator;
import java.time.*;
import java.util.*;

/** Bounded rolling horizon. The objective is a lookup of the common rule evaluator,
 * not a second set of benefit formulas. Only the first assignment is committed. */
public final class TransactionCardOptimizer {
    public record Card(long id,List<TransactionBenefitRule> rules,List<BenefitTransaction> committed,
                       Long previousSpend,Map<String,String> facts) {
        public Card {rules=List.copyOf(rules);committed=List.copyOf(committed);facts=Map.copyOf(facts);}
    }
    public record Choice(long cardId,String status,long modeledBenefit,long bound,double seconds,int eventCount) {}
    private final TransactionBenefitCalculator calculator;
    public TransactionCardOptimizer(TransactionBenefitCalculator calculator){this.calculator=calculator;}

    public long objective(Card card,YearMonth month,List<BenefitTransaction> additions,boolean allowUnverified) {
        List<BenefitTransaction> current=new ArrayList<>(card.committed());
        additions.stream().filter(t->YearMonth.from(t.approvedAt()).equals(month)).forEach(current::add);
        long total=calculator.calculate(card.rules(),current,card.previousSpend(),card.facts(),allowUnverified).total();
        List<BenefitTransaction> next=additions.stream().filter(t->YearMonth.from(t.approvedAt()).equals(month.plusMonths(1))).toList();
        if(!next.isEmpty())total+=calculator.calculate(card.rules(),next,calculator.eligibleSpend(card.rules(),current),card.facts(),allowUnverified).total();
        return total;
    }
    private static List<Card> validate(BenefitTransaction current,List<Card> input) {
        if(input.isEmpty() || input.size()>10)throw new IllegalArgumentException("1..10 cards required");
        if(input.stream().map(Card::id).distinct().count()!=input.size())throw new IllegalArgumentException("Duplicate card");
        YearMonth month=YearMonth.from(current.approvedAt());
        for(var c:input)for(var t:c.committed())if(!YearMonth.from(t.approvedAt()).equals(month) || t.approvedAt().isAfter(current.approvedAt()) || t.id().equals(current.id()))
            throw new IllegalArgumentException("Committed events must be earlier in current month");
        return input.stream().sorted(Comparator.comparingLong(Card::id)).toList();
    }
    public Choice immediate(BenefitTransaction current,List<Card> input,boolean allowUnverified) {
        long start=System.nanoTime();List<Card> cards=validate(current,input);
        long best=Long.MIN_VALUE,id=0;YearMonth month=YearMonth.from(current.approvedAt());
        for(Card card:cards) {
            long delta=objective(card,month,List.of(current),allowUnverified)-objective(card,month,List.of(),allowUnverified);
            if(delta>best){best=delta;id=card.id();}
        }
        return new Choice(id,"GREEDY",best,best,(System.nanoTime()-start)/1e9,1);
    }
    public Choice cpSat(BenefitTransaction current,List<BenefitTransaction> forecast,List<Card> input,
                        boolean allowUnverified,double seconds) {
        long start=System.nanoTime();List<Card> cards=validate(current,input);
        if(forecast.size()>7 || seconds<=0)throw new IllegalArgumentException("At most 7 forecast events and positive limit");
        YearMonth month=YearMonth.from(current.approvedAt());
        for(var tx:forecast)if(!"true".equals(tx.facts().get("forecast")) || !tx.approvedAt().isAfter(current.approvedAt())
            || YearMonth.from(tx.approvedAt()).isAfter(month.plusMonths(1)))
            throw new IllegalArgumentException("Future inputs must be marked forecasts within two months");
        List<BenefitTransaction> events=new ArrayList<>();events.add(current);events.addAll(forecast);
        Loader.loadNativeLibraries();CpModel model=new CpModel();
        BoolVar[][] assign=new BoolVar[cards.size()][events.size()];List<IntVar> rewards=new ArrayList<>();
        for(int c=0;c<cards.size();c++) {
            for(int i=0;i<events.size();i++)assign[c][i]=model.newBoolVar("c"+c+"e"+i);
            int masks=1<<events.size();long[][] table=new long[masks][2];long lo=Long.MAX_VALUE,hi=Long.MIN_VALUE;
            for(int mask=0;mask<masks;mask++) {
                List<BenefitTransaction> selected=new ArrayList<>();for(int i=0;i<events.size();i++)if((mask&(1<<i))!=0)selected.add(events.get(i));
                long score=objective(cards.get(c),month,selected,allowUnverified);
                table[mask]=new long[]{mask,score};lo=Math.min(lo,score);hi=Math.max(hi,score);
            }
            IntVar maskVar=model.newIntVar(0,masks-1,"mask"+c),reward=model.newIntVar(lo,hi,"reward"+c);
            long[] weights=new long[events.size()];for(int i=0;i<events.size();i++)weights[i]=1L<<i;
            model.addEquality(maskVar,LinearExpr.weightedSum(assign[c],weights));
            model.addAllowedAssignments(new IntVar[]{maskVar,reward}).addTuples(table);rewards.add(reward);
        }
        for(int i=0;i<events.size();i++){BoolVar[] col=new BoolVar[cards.size()];for(int c=0;c<cards.size();c++)col[c]=assign[c][i];model.addExactlyOne(col);}
        // Preserve primary monetary objective; deterministic current-card tie break is worth < 1 monetary unit.
        var objective=LinearExpr.newBuilder();for(var r:rewards)objective.addTerm(r,cards.size()+1);
        for(int c=0;c<cards.size();c++)objective.addTerm(assign[c][0],cards.size()-c);
        model.maximize(objective);CpSolver solver=new CpSolver();solver.getParameters().setMaxTimeInSeconds(seconds).setNumSearchWorkers(1).setRandomSeed(0);
        var status=solver.solve(model);
        if(status!=CpSolverStatus.OPTIMAL && status!=CpSolverStatus.FEASIBLE) {
            var fallback=immediate(current,cards,allowUnverified);
            return new Choice(fallback.cardId(),status+"_FALLBACK_GREEDY",fallback.modeledBenefit(),0,(System.nanoTime()-start)/1e9,events.size());
        }
        long id=0,total=0;for(int c=0;c<cards.size();c++){if(solver.booleanValue(assign[c][0]))id=cards.get(c).id();total+=solver.value(rewards.get(c));}
        long bound=(long)Math.floor(solver.bestObjectiveBound()/(cards.size()+1));
        return new Choice(id,status.name(),total,bound,(System.nanoTime()-start)/1e9,events.size());
    }
}
