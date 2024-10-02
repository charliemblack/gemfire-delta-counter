package dev.gemfire.counters;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@SpringBootApplication
@RestController

public class Main {

    private MeterRegistry registry;

    @Autowired
    public Main(MeterRegistry meterRegistry) {
        registry = meterRegistry;
    }

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(Main.class, args);
    }

    @GetMapping
    public List doSomthing() {
        return new ArrayList();
    }

    @Bean
    CommandLineRunner run(@Value("${gemfire.locator.port:10334}") int port) {
        final Timer timer = Timer.builder("GemFire.Function.Time")
                .description("Tracks the time taken to execute a method")
                .publishPercentiles(0.5, 0.70, 0.90, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
        return args -> {
            ClientCache clientCache = new ClientCacheFactory()
                    .addPoolLocator("localhost", port)
                    .setPoolPRSingleHopEnabled(true)
                    .setPoolSubscriptionEnabled(true)
                    .create();

            ClientRegionFactory<String, DeltaCounter> clientRegionFactory = clientCache
                    .<String, DeltaCounter>createClientRegionFactory(ClientRegionShortcut.PROXY);
            final Region<String, DeltaCounter> accumulatorRegion = clientRegionFactory.create("accumulatorRegion");

            for (int i = 0; i < 40; i++) {
                Thread t = new Thread(() -> {
                    while (true) {
                        timer.record(() -> {
                            DeltaCounterFunction.increment(accumulatorRegion, "name", 1, Integer.MAX_VALUE);
                        });
                    }
                });
                t.start();
            }
        };
    }
}
