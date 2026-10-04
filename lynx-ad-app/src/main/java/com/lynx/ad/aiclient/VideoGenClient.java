package com.lynx.ad.aiclient;

public interface VideoGenClient {
    String submit(String payload);

    VideoJobStatus poll(String thirdPartyJobId);
}
