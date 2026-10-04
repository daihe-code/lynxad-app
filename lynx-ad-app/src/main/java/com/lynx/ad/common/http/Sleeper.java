package com.lynx.ad.common.http;

@FunctionalInterface
public interface Sleeper {

    Sleeper THREAD = millis -> {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    };

    void sleep(long millis) throws InterruptedException;
}
