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

    private static final MeterRegistry registry = new SimpleMeterRegistry();
    private Timer timer = Timer.builder("GemFire.Function.Time")
            .description("Tracks the time taken to execute a method")
            .publishPercentiles(0.5, 0.95)  // Example percentiles (median and 95th percentile)
            .publishPercentileHistogram()
            .register(registry);
    private boolean exit = false;
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
            if (primeRegion) {
                accumulatorRegion.put("name", new DeltaCounter());
            }
            countDownLatch.await();
            // Reset the counter

            List<Thread> threads = new ArrayList<>();
            final long start = System.currentTimeMillis();


            for (int i = 0; i < 40; i++) {
                Thread t = new Thread(() -> {
                    if (!exit) {
                        DeltaCounter counter = new DeltaCounter();
                        for (int j = 0; j < 100; j++) {
                            timer.record(() -> {
                                DeltaCounterFunction.increment(accumulatorRegion, "name", 1, 400000);
                            });
                        }
                    }
                });
                t.start();
                threads.add(t);
            }

            System.out.println("Press Enter to exit...");

            try {
                System.in.read();  // Wait for the Enter key
            } catch (IOException e) {
                e.printStackTrace();
            }
            exit = true;

            System.out.println("Exiting...");

            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            registry.get("method.execution.time").timer().takeSnapshot().histogramCounts()
                    .forEach(count -> System.out.println("Histogram Bucket Count: " + count));

        };
    }
}
