package dev.gemfire.counters;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.execute.*;
import org.apache.geode.distributed.DistributedLockService;
import org.apache.geode.internal.cache.PartitionedRegion;
import org.apache.geode.internal.cache.PartitionedRegionHelper;
import org.apache.geode.logging.internal.log4j.api.LogService;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DeltaCounterFunction implements Function {

    public static final String ID = "DeltaCounterFunction";
    static final Map<String, Object> lockMap = new HashMap<>();
    private static final Logger logger = LogService.getLogger();

    public static int increment(Region region, String counterName, int deltaChange, int maxValue) {

        ResultCollector collector = FunctionService.onRegion(region)
                .withFilter(Set.of(counterName)) //important since we will be sending to the primary and this is how.
                .setArguments(new DeltaCounterFunctionArguments(counterName, deltaChange, maxValue))
                .execute(ID);
        return ((Collection<Integer>) collector.getResult()).iterator().next();
    }

    @Override
    public void execute(FunctionContext functionContext) {
        // We can assume we are running on the primary except on rebalance or infrastructure issues.
        // Even then we are only a hop away if we use delta and gemfire will autocorrect
        // next call.

        if (functionContext instanceof RegionFunctionContext) {
            RegionFunctionContext<DeltaCounterFunctionArguments> rfc = (RegionFunctionContext<DeltaCounterFunctionArguments>) functionContext;

            Region<String, DeltaCounter> deltaCounterRegion = rfc.getDataSet();
            DeltaCounterFunctionArguments arguments = rfc.getArguments();
            String counterName = arguments.getCounterName();
            // the key to this is not having GemFire in copy on read mode.
            final DeltaCounter counter = deltaCounterRegion.get(arguments.getCounterName());
            if (counter == null) {
                deltaCounterRegion.put(arguments.getCounterName(), new DeltaCounter(arguments.getDeltaChange()));
                rfc.getResultSender().lastResult(arguments.getDeltaChange());
            } else {
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

    private synchronized DistributedLockService getLockService(RegionFunctionContext rfc) {
        DistributedLockService lockService = DistributedLockService.getServiceNamed("DeltaCounterFunction");
        if (lockService == null) {
            lockService = DistributedLockService.create("DeltaCounterFunction", rfc.getCache().getDistributedSystem());
        }
        if (!lockService.isLockGrantor()) {
            // This is an interesting case - if the current host isn't the lock grantor then it is a remote procedure
            // call.   So we would want to become the lock grantor so the speed of the lock is a local call.
            // HOWEVER - if we have several "locks" with in a "service" then the lock will jump around which will not
            // be ideal.  So it would be better to change up the locking strategy.
            // One strategy would be a lock service per bucket. (see below)
            lockService.becomeLockGrantor();
        }
        return lockService;
    }

    private synchronized DistributedLockService getLockServicePerBucket(RegionFunctionContext rfc, String counterName) {

        // Maybe make the number of buckets on the region to not be something not to crazy.
        int bucket = PartitionedRegionHelper.getHashKey((PartitionedRegion) rfc.getDataSet(), counterName);
        String serviceName = "DeltaCounterFunction" + bucket;

        // Don't cache the lockService.
        DistributedLockService lockService = DistributedLockService.getServiceNamed(serviceName);
        if (lockService == null) {
            lockService = DistributedLockService.create(serviceName, rfc.getCache().getDistributedSystem());
        }
        if (!lockService.isLockGrantor()) {
            lockService.becomeLockGrantor();
        }
        return lockService;
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
}
