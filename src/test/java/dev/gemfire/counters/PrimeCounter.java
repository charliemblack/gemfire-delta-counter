package dev.gemfire.counters;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;

public class PrimeCounter {

    public static void main(String[] args) {
        ClientCache clientCache = new ClientCacheFactory()
                .addPoolLocator("localhost", 10334)
                .setPoolPRSingleHopEnabled(true)
                .setPoolSubscriptionEnabled(true)
                .create();

        ClientRegionFactory<String, DeltaCounter> clientRegionFactory = clientCache
                .<String, DeltaCounter>createClientRegionFactory(ClientRegionShortcut.PROXY);
        final Region<String, DeltaCounter> accumulatorRegion = clientRegionFactory.create("accumulatorRegion");

        accumulatorRegion.put("name", new DeltaCounter());
    }
}
