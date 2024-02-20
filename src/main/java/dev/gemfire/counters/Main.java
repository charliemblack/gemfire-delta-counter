package dev.gemfire.counters;

import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.util.CacheListenerAdapter;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) throws Exception{
        ClientCache clientCache = new ClientCacheFactory()
                .addPoolLocator("192.168.1.109", 10334)
                .setPoolPRSingleHopEnabled(true)
                .setPoolSubscriptionEnabled(true)
                .create();

        final AtomicInteger atomicInteger = new AtomicInteger(0);

        final Region<String, DeltaCounter> accumulatorRegion = clientCache.<String, DeltaCounter>createClientRegionFactory(ClientRegionShortcut.PROXY).create("accumulatorRegion");
        accumulatorRegion.getAttributesMutator().addCacheListener(new CacheListenerAdapter<String, DeltaCounter>() {
            @Override
            public void afterCreate(EntryEvent<String, DeltaCounter> event) {
                afterUpdate(event);
            }
            @Override
            public void afterUpdate(EntryEvent<String, DeltaCounter> event) {
                //System.out.println("event.getNewValue() = " + event.getNewValue() + " current counter" + atomicInteger.get());
            }
        });
        //Reset the counter
        accumulatorRegion.put("name", new DeltaCounter());
        ArrayList<Thread> threads = new ArrayList<>();
        final long start = System.currentTimeMillis();
        for(int i = 0; i < 100; i++) {
            Thread t = new Thread(() -> {
                DeltaCounter counter = new DeltaCounter();
                for(int j = 0; j < 100000; j++) {
                    if(atomicInteger.incrementAndGet() >= 400000){
                        int deltaCounter = DeltaCounterFunction.increment(accumulatorRegion, "name", 1, 400000);
                        long duration = System.currentTimeMillis() - start;
                        System.out.println("delta counter " + deltaCounter + " time " + duration + " per second " + (400000/TimeUnit.MILLISECONDS.toSeconds(duration)));
                        break;
                    }else {
                        DeltaCounterFunction.increment(accumulatorRegion, "name", 1, 400000);
                    }
                }
            });
            t.start();
            threads.add(t);
        }
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("accumulatorRegion = " + accumulatorRegion.get("name"));
        System.out.println("atomicInteger = " + atomicInteger);
        Thread.sleep(5 * 1000);
        System.out.println("accumulatorRegion = " + accumulatorRegion.get("name"));
        System.out.println("atomicInteger = " + atomicInteger);
    }
}
