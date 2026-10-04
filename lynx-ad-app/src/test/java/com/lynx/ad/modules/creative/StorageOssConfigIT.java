package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.infrastructure.adapter.UnconfiguredOssStorageAdapter;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageOssConfigIT {

    @TempDir
    Path localDir;

    @Test
    void unconfiguredOssDoesNotFallbackLocal() throws Exception {
        UnconfiguredOssStorageAdapter adapter = new UnconfiguredOssStorageAdapter();
        BizException ex = assertThrows(
                BizException.class,
                () -> adapter.put(
                        new TenantId("t1"),
                        "lynx-ad/t1/creative/20260919/x_a.jpg",
                        new ByteArrayInputStream(new byte[] {1}),
                        "image/jpeg"));
        assertEquals(1507, ex.code());
        assertTrue(Files.list(localDir).findAny().isEmpty());
    }
}
