package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.catalog.application.ProductQueryService;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.domain.port.*;
import com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.delivery.application.PlanCreativeBindingQuery;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class CarouselLibraryReadTest {
    static class CountingSource extends AbstractDataSource {
        final DataSource delegate; final AtomicInteger queries=new AtomicInteger();
        CountingSource(DataSource delegate) { this.delegate=delegate; }
        public Connection getConnection()throws SQLException { return wrap(delegate.getConnection()); }
        public Connection getConnection(String user,String password)throws SQLException { return wrap(delegate.getConnection(user,password)); }
        Connection wrap(Connection c) {
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("prepareStatement"))queries.incrementAndGet();
                try { return method.invoke(c,args); } catch(InvocationTargetException e) { throw e.getCause(); }
            });
        }
    }
    @ParameterizedTest @ValueSource(strings={"PostgreSQL","MySQL"})
    void thirtyTwoCarouselsUseSixQueriesAndOnlyTheRequestedPageSignsFileUrls(String mode)throws Exception {
        var ds=new CountingSource(new DriverManagerDataSource("jdbc:h2:mem:carousel_library_"+UUID.randomUUID()+";MODE="+mode+";DB_CLOSE_DELAY=-1","sa",""));
        var jdbc=new JdbcTemplate(ds);String directory="db/migration"+(mode.equals("MySQL")?"-mysql":"")+"/";
        try(var c=ds.getConnection()) {
            for(String script:List.of("V12__creative_asset_upload.sql","V14__asset_ai_review.sql","V15__creative_carousel.sql","V19__engine_asset_reference.sql")) {
                var source=new ClassPathResource(directory+script);
                String sql=new String(source.getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8).replace("TIMESTAMPTZ","TIMESTAMP");
                ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
        }
        // H2 uses one ADD COLUMN per statement; exercise the real read schema without vendor ALTER syntax.
        for(String column:List.of("bucket VARCHAR(128)","storage_endpoint VARCHAR(255)","path_version VARCHAR(16)","origin VARCHAR(32)","duration_ms BIGINT","etag VARCHAR(128)","preview_asset_id VARCHAR(36)"))jdbc.execute("ALTER TABLE creative_asset ADD "+column);
        jdbc.execute("ALTER TABLE creative_asset_platform ADD verification_state VARCHAR(24)");
        jdbc.execute("ALTER TABLE creative_asset_platform ADD verification_checked_at TIMESTAMP");
        jdbc.execute("ALTER TABLE creative_asset_platform ADD verification_message VARCHAR(512)");
        var assets=new AssetUploadRepository(jdbc);var bundles=new CarouselRepository(jdbc);var storage=mock(StoragePort.class);
        var tenant=new TenantId("carousel-tenant");var now=Instant.parse("2026-11-04T00:00:00Z");
        var uploadService=new AssetUploadService(assets,storage,mock(OauthAccountRepository.class),new LynxAdProperties(),new ObjectMapper(),mock(MediaFileInspector.class));
        var service=new CarouselService(bundles,assets,uploadService,mock(OauthAccountRepository.class));
        when(storage.getUrl(eq(tenant),anyString())).thenAnswer(i->"/preview/"+i.getArgument(1));
        for(int group=0;group<32;group++) {
            var parts=new ArrayList<CarouselRepository.Part>();
            for(int part=0;part<6;part++) {
                String kind=part==0?"TEXT":part==5?"AUDIO":"IMAGE",id="file-"+group+"-"+part;
                var file=new AssetUploadRepository.Asset(id,tenant.value(),"user",id,kind,id,"application/octet-stream",100,"key-"+id,
                        "STORED","local",null,null,null,false,1200,1200,part==0?"作品描述 "+group:null,null,null,now,now);
                assets.insert(file);assets.carouselComponent(tenant.value(),id);
                jdbc.update("UPDATE creative_asset SET width=1200,height=1200,text_content=? WHERE tenant_id=? AND id=?",file.textContent(),tenant.value(),id);
                assets.queueReview(tenant.value(),id);
                jdbc.update("UPDATE creative_asset_review SET status=? WHERE tenant_id=? AND asset_id=?",
                        group==1 && part==5?"REJECTED":group==2 && part==4?"PENDING":"APPROVED",tenant.value(),id);
                if(group==0)jdbc.update("INSERT INTO creative_engine_asset(asset_id,tenant_id,job_id,material_id,component) VALUES(?,?,?,?,?)",id,tenant.value(),id,id,kind);
                assets.addSync(file,"account");
                parts.add(new CarouselRepository.Part(kind,part,id));
            }
            bundles.insert(new CarouselRepository.Bundle("group-"+group,tenant.value(),"user","request-"+group,"图文 "+group,List.of("account"),"PENDING",now),parts);
        }
        ds.queries.set(0);long before=System.nanoTime();var details=service.list(tenant);long fullMs=(System.nanoTime()-before)/1_000_000;int fullQueries=ds.queries.get();
        clearInvocations(storage);ds.queries.set(0);before=System.nanoTime();var summary=service.library(tenant);long summaryMs=(System.nanoTime()-before)/1_000_000;
        int summaryQueries=ds.queries.get();assertEquals(6,summaryQueries);assertTrue(fullQueries>900);verifyNoInteractions(storage);
        assertEquals(details.size(),summary.size());
        for(var entry:summary) {
            var detail=details.stream().filter(d->d.id().equals(entry.id())).findFirst().orElseThrow();
            assertEquals(detail.aiReview().status(),entry.aiReview().status());assertEquals(detail.aiReview().reasons(),entry.aiReview().reasons());
            assertEquals(600,entry.sizeBytes());assertEquals(4,entry.imageCount());
            assertEquals(detail.parts().stream().filter(p->p.role().equals("IMAGE")).findFirst().orElseThrow().asset().id(),entry.cover().id());
            assertEquals(detail.parts().stream().filter(p->p.role().equals("TEXT")).findFirst().orElseThrow().asset().textContent(),entry.textContent());
            assertEquals(6,entry.syncs().size());
        }
        var tasks=mock(CreativeTaskRepository.class);var oauth=mock(OauthQueryService.class);var bindings=mock(PlanCreativeBindingQuery.class);
        var query=new LibraryQueryService(tasks,storage,mock(MaterialLibraryPort.class),oauth,bindings,mock(ProductQueryService.class));
        query.carouselDependencies(service);query.uploadDependencies(assets,null,null);
        var page=query.query(tenant,"CAROUSEL",null,null,null,"ALL",null,1,8);
        assertEquals(32,page.pageInfo().total());assertEquals(8,page.items().size());verify(storage,times(8)).getUrl(eq(tenant),anyString());
        assertTrue(service.library(new TenantId("other-tenant")).isEmpty());
        assertTrue(assets.libraryAssets("other-tenant",List.of("file-0-0")).isEmpty());
        System.out.printf("Carousel list %s: detail=%d queries/%dms; summary=%d queries/%dms; page URLs=8%n",mode,fullQueries,fullMs,summaryQueries,summaryMs);
        // A canonical component outside the bundle is still resolved under the same tenant.
        jdbc.update("UPDATE creative_asset SET canonical_id=? WHERE tenant_id=? AND id=?","file-4-1",tenant.value(),"file-3-1");
        assertEquals("file-4-1",service.library(tenant).stream().filter(g->g.id().equals("group-3")).findFirst().orElseThrow().cover().id());
        // Missing component evidence must not turn a pending set into an approved one.
        jdbc.update("DELETE FROM creative_asset_review WHERE tenant_id=? AND asset_id=?",tenant.value(),"file-5-1");
        assertEquals("PENDING",service.library(tenant).stream().filter(g->g.id().equals("group-5")).findFirst().orElseThrow().aiReview().status());
        jdbc.execute("SHUTDOWN");
    }
}
