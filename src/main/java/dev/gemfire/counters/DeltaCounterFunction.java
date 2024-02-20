package dev.gemfire.counters;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.execute.*;
import org.apache.geode.logging.internal.log4j.api.LogService;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Set;

public class DeltaCounterFunction implements Function {

    private static final Logger logger = LogService.getLogger();

    public static final String ID = "DeltaCounterFunction";

    @Override
    public void execute(FunctionContext functionContext) {
        // We can assume we are running on the primary except on rebalance or infrastructure issues.
        // Even then we are only a hop away if we use delta and gemfire will autocorrect
        // next call.

        if (functionContext instanceof RegionFunctionContext) {
            RegionFunctionContext<DeltaCounterFunctionArguments> rfc = (RegionFunctionContext<DeltaCounterFunctionArguments>) functionContext;

            Region<String, DeltaCounter> deltaCounterRegion = rfc.getDataSet();
            DeltaCounterFunctionArguments arguments = rfc.getArguments();

            final DeltaCounter counter = deltaCounterRegion.get(arguments.getCounterName());
            if (counter == null) {
                // new counter ?? please pre-load counters - if not I have to write code to make sure things are atomic (slower)
                deltaCounterRegion.put(arguments.getCounterName(), new DeltaCounter(arguments.getDeltaChange()));
                rfc.getResultSender().lastResult(arguments.getDeltaChange());
            } else {
                //I put in the synchronized block to ensure we don't have a concurrent add while past checking.
                synchronized (counter) {
                    if (counter.getValue() + arguments.getDeltaChange() > arguments.getMaxValue()) {
                        // we have gone over - I am going to return potential value but not store it  ¯\_(ツ)_/¯
                        rfc.getResultSender().lastResult(counter.getValue() + arguments.getDeltaChange());
                    } else {
                        // the just increment case - I will have the function return the new value.
                        counter.increment(arguments.getDeltaChange());
                        rfc.getResultSender().lastResult(counter.getValue());

                        //We could do eventual consistency by sending storage later.   It will be significantly faster.
                        deltaCounterRegion.put(arguments.getCounterName(), counter);
                        //at this point the delta change has been applied and the delta will be sent to remote sites if there are any.
                    }
                }
            }
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean optimizeForWrite() {
        // we want the function to run on a server that is the primary owner of the data.
        return true;
    }

    @Override
    public boolean isHA() {
        // GemFire will do the retry work for you.
        return true;
    }

    @Override
    public boolean hasResult() {
        return true;
    }

    public static int increment(Region region, String counterName, int deltaChange, int maxValue) {

        ResultCollector collector = FunctionService.onRegion(region)
                .withFilter(Set.of(counterName)) //important since we will be sending to the primary and this is how.
                .setArguments(new DeltaCounterFunctionArguments(counterName, deltaChange, maxValue))
                .execute(ID);
        return ((Collection<Integer>) collector.getResult()).iterator().next();
    }
}
