package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.domain.port.StoragePort;
import com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PlanPreviewImagesTest {
    @Test void signsOnlyStoredImageAssetsOwnedByTheRequestedTenant() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:plan_covers_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("""
            CREATE TABLE creative_asset(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64),user_id VARCHAR(64),request_id VARCHAR(64),
              kind VARCHAR(16),filename VARCHAR(64),content_type VARCHAR(64),size_bytes BIGINT,object_key VARCHAR(128),
              storage_status VARCHAR(16),storage_mode VARCHAR(16),upload_id VARCHAR(64),upload_ticket_json VARCHAR(64),
              md5 VARCHAR(64),is_aigc BOOLEAN,width INT,height INT,text_content VARCHAR(64),canonical_id VARCHAR(64),
              error_message VARCHAR(64),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
            """);
        for(var row:List.of(new String[]{"owned","owner","IMAGE","STORED"},new String[]{"foreign","other","IMAGE","STORED"},
                new String[]{"video","owner","VIDEO","STORED"},new String[]{"waiting","owner","IMAGE","WAITING"}))
            jdbc.update("INSERT INTO creative_asset(id,tenant_id,kind,storage_status,storage_mode,object_key) VALUES(?,?,?,?,?,?)",row[0],row[1],row[2],row[3],"local","key-"+row[0]);
        var repository=new AssetUploadRepository(jdbc);var storage=mock(StoragePort.class);var tenant=new TenantId("owner");
        when(storage.getUrl(tenant,"key-owned")).thenReturn("https://storage.test/owned.jpg");
        var service=new AssetUploadService(repository,storage,mock(OauthAccountRepository.class),new LynxAdProperties(),new ObjectMapper(),mock(MediaFileInspector.class));
        assertEquals(Map.of("owned","https://storage.test/owned.jpg"),service.previewImages(tenant,List.of("owned","owned","foreign","video","waiting","missing")));
        verify(storage).getUrl(tenant,"key-owned");verifyNoMoreInteractions(storage);
        assertTrue(service.previewImages(tenant,List.of()).isEmpty());
    }
}
