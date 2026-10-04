package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.domain.service.ObjectKeyPolicy;
import com.lynx.ad.modules.creative.infrastructure.adapter.MockOssClient;
import com.lynx.ad.modules.creative.infrastructure.adapter.OssStorageAdapter;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class StorageOssMockIT {

    @Test
    void putLandsInPrefix() {
        LynxAdProperties props = new LynxAdProperties();
        props.getStorage().getOss().setPrefix("lynx-ad/");
        props.getStorage().getOss().setBucket("demo-bucket");
        MockOssClient client = new MockOssClient();
        OssStorageAdapter adapter = new OssStorageAdapter(props, client);
        TenantId tenant = new TenantId("t-oss");
        String key = ObjectKeyPolicy.nextKey("lynx-ad/", tenant, "a.mp4");
        adapter.put(tenant, key, new ByteArrayInputStream(new byte[] {9, 9}), "video/mp4");
        assertTrue(client.exists("demo-bucket", key));
        assertTrue(key.startsWith("lynx-ad/t-oss/creative/"));
        assertTrue(adapter.getUrl(tenant, key).contains("mock-oss.local"));
    }
}
