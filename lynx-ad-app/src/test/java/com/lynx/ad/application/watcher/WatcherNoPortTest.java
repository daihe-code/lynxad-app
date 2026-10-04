package com.lynx.ad.application.watcher;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.lynx.ad.agents.watcher.WatcherJob;
import org.junit.jupiter.api.Test;

class WatcherNoPortTest {

    @Test
    void constructorHasNoBidPorts() {
        for (var ctor : WatcherJob.class.getDeclaredConstructors()) {
            for (var p : ctor.getParameterTypes()) {
                String name = p.getName();
                assertFalse(name.contains("UpdateBid"), name);
                assertFalse(name.contains("UpdateRoi"), name);
            }
        }
    }
}
