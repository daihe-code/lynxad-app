package com.lynx.ad.application.dataops;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MockReportUnchangedTest {

    @Test
    void mockAdapterKeepsFixedNumbers() throws Exception {
        Path src = Path.of("src/main/java/com/lynx/ad/modules/dataops/infrastructure/adapter/MockReportPullAdapter.java");
        String text = Files.readString(src);
        assertTrue(text.contains("100_000") || text.contains("100000"));
        assertTrue(text.contains("200_000") || text.contains("200000"));
        assertTrue(text.contains("2.00"));
    }
}
