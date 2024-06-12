package dev.gemfire.counters;

import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.util.CacheListenerAdapter;
import org.apache.logging.log4j.core.util.Integers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class Main {

    @Value("${dev.gemfire.counters.main.primeRegion:false}")
    private boolean primeRegion = false;
    public static void main(String[] args) {

        SpringApplication.run(Main.class, args);
    }
    @Bean
    CommandLineRunner run(@Value("${gemfire.locator.port:10334}") int port) {
        return args -> {
            ClientCache clientCache = new ClientCacheFactory()
                    .addPoolLocator("localhost", port)
                    .setPoolPRSingleHopEnabled(true)
                    .setPoolSubscriptionEnabled(true)
                    .create();
            final AtomicInteger atomicInteger = new AtomicInteger(0);

            ClientRegionFactory<String, DeltaCounter> clientRegionFactory = clientCache
                    .<String, DeltaCounter>createClientRegionFactory(ClientRegionShortcut.PROXY);
            clientRegionFactory.setConcurrencyChecksEnabled(false);
            final Region<String, DeltaCounter> accumulatorRegion = clientRegionFactory.create("accumulatorRegion");
            accumulatorRegion.registerInterestForAllKeys();
            CountDownLatch countDownLatch = new CountDownLatch(2);
            accumulatorRegion.getAttributesMutator().addCacheListener(new CacheListenerAdapter<String, DeltaCounter>() {
                @Override
                public void afterCreate(EntryEvent<String, DeltaCounter> event) {
                    afterUpdate(event);
                }

                @Override
                public void afterUpdate(EntryEvent<String, DeltaCounter> event) {
                    countDownLatch.countDown();
                }
            });
            if(primeRegion){
                accumulatorRegion.put("name", new DeltaCounter());
            }
            countDownLatch.await();
            // Reset the counter

            List<Thread> threads = new ArrayList<>();
            final long start = System.currentTimeMillis();

            for (int i = 0; i < 10; i++) {
                Thread t = new Thread(() -> {
                    DeltaCounter counter = new DeltaCounter();
                    for (int j = 0; j < 100; j++) {
                            DeltaCounterFunction.increment(accumulatorRegion, "name", 1, 400000);
                    }
                });
                t.start();
                threads.add(t);
            }

            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            System.out.println("accumulatorRegion = " + accumulatorRegion.get("name"));
            System.out.println("atomicInteger = " + atomicInteger);
            Thread.sleep(5 * 1000);
            System.out.println("accumulatorRegion = " + accumulatorRegion.get("name"));
            System.out.println("atomicInteger = " + atomicInteger);
        };
    }
}
