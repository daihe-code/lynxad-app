package com.lynx.ad.modules.creative.infrastructure.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class CreativeFixture {

    private CreativeFixture() {}

    public static byte[] jpeg() {
        return load("fixture/creative/placeholder.jpg");
    }

    public static byte[] mp4() {
        return load("fixture/creative/placeholder.mp4");
    }

    private static byte[] load(String path) {
        try (InputStream in = CreativeFixture.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return new byte[] {
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9
                };
            }
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
