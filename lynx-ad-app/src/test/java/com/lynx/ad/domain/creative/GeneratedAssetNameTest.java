package com.lynx.ad.domain.creative;

import static org.junit.jupiter.api.Assertions.*;

import com.lynx.ad.modules.creative.domain.service.GeneratedAssetName;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GeneratedAssetNameTest {
    private static final Instant TIME = Instant.parse("2026-11-03T13:00:00Z");
    private static final String ID = "6013b26f-72b1-4f64-9119-d2d60f4d91f3";

    @Test void sameTitleAndTimeStillDistinguishAssetsWithTheSameIdPrefix() {
        String first = GeneratedAssetName.create("瑜伽服晨间拉伸", "VIDEO", TIME, ID, ".mp4");
        String second = GeneratedAssetName.create("瑜伽服晨间拉伸", "VIDEO", TIME,
                "6013b26f-72b1-4f64-9119-d2d60f4d91f4", ".mp4");
        assertNotEquals(first, second);
        assertTrue(first.startsWith("瑜伽服晨间拉伸_视频_20261103-210000_"));
        assertTrue(first.endsWith(ID + ".mp4"));
    }

    @Test void givenThePersistedIdentityAndTimeTheNameIsStable() {
        assertEquals(GeneratedAssetName.create("素材标题", "VIDEO", TIME, ID, ".mp4"),
                GeneratedAssetName.create("素材标题", "VIDEO", TIME, ID, ".mp4"));
    }

    @Test void mediaComponentsRemainRecognizableAndKeepTheirActualExtensions() {
        assertTrue(GeneratedAssetName.create("标题", "VIDEO", TIME, ID, ".mp4").contains("_视频_"));
        assertTrue(GeneratedAssetName.create("标题", "COVER", TIME, ID, ".jpg").contains("_封面_"));
        assertTrue(GeneratedAssetName.create("标题", "IMAGE_0", TIME, ID, ".png").contains("_图片1_"));
        assertTrue(GeneratedAssetName.create("标题", "IMAGE_5", TIME, ID, ".jpg").contains("_图片6_"));
        for (String extension : new String[]{".wav", ".m4a", ".mp3"}) {
            String name = GeneratedAssetName.create("标题", "AUDIO", TIME, ID, extension);
            assertTrue(name.contains("_配音_"));
            assertTrue(name.endsWith(extension));
        }
        assertTrue(GeneratedAssetName.create("标题", "TEXT", TIME, ID, ".txt").endsWith(ID + ".txt"));
    }

    @Test void longUnicodeTitlesAreBoundedWithoutTruncatingIdentityExtensionOrCodePoints() {
        String name = GeneratedAssetName.create("瑜伽服🌸".repeat(100), "VIDEO", TIME, ID, ".mp4");
        byte[] encoded = name.getBytes(StandardCharsets.UTF_8);
        assertTrue(encoded.length <= 100);
        assertEquals(name, new String(encoded, StandardCharsets.UTF_8));
        assertTrue(name.endsWith(ID + ".mp4"));
    }

    @Test void unsafeTitleCharactersCannotBecomePathsOrInvisibleNames() {
        String name = GeneratedAssetName.create("../瑜伽\\服:\n\t*?\"<>|\u202e 展示...", "VIDEO", TIME, ID, ".mp4");
        assertFalse(name.contains(".."));
        assertFalse(name.chars().anyMatch(c -> Character.isISOControl(c) || "/\\:*?\"<>|".indexOf(c) >= 0));
        assertFalse(name.contains("\u202e"));
        assertTrue(name.contains("瑜伽_服"));
        assertTrue(name.endsWith(".mp4"));
    }

    @Test void missingOrUnusableTitlesStillHaveReadableUniqueNames() {
        for (String title : new String[]{null, "", " \n/\\:*?\"<>|...\u202e"}) {
            assertTrue(GeneratedAssetName.create(title, "VIDEO", TIME, ID, ".mp4").startsWith("智能素材_视频_"));
        }
    }
}
