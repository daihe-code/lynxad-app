package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.port.StorageObject;
import com.lynx.ad.modules.creative.domain.service.ObjectKeyPolicy;
import com.lynx.ad.modules.creative.infrastructure.adapter.LocalStorageAdapter;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageLocalIT {

    @TempDir
    Path dir;

    @Test
    void putGetReadDelete() throws Exception {
        LynxAdProperties props = new LynxAdProperties();
        props.getStorage().setLocalDir(dir.toString());
        props.getStorage().getOss().setPrefix("lynx-ad/");
        LocalStorageAdapter adapter = new LocalStorageAdapter(props, "/lynx-ad");
        TenantId tenant = new TenantId("t-local");
        String key = ObjectKeyPolicy.nextKey("lynx-ad/", tenant, "a.jpg");
        byte[] payload = new byte[] {1, 2, 3, 4};
        StorageObject stored = adapter.put(tenant, key, new ByteArrayInputStream(payload), "image/jpeg");
        assertTrue(stored.sizeBytes() == 4);
        assertTrue(adapter.getUrl(tenant, key).contains("/api/v1/creative/media?token="));
        assertArrayEquals(payload, adapter.read(tenant, key));
        adapter.delete(tenant, key);
        assertTrue(Files.notExists(dir.resolve(key)));
    }
}
