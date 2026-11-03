package com.lynx.ad.modules.creative.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CarouselPlatformService {
    private final CarouselService bundles;
    private final CreationQianchuanGateway gateway;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    public CarouselPlatformService(CarouselService bundles,CreationQianchuanGateway gateway,ObjectMapper json,JdbcTemplate jdbc){this.bundles=bundles;this.gateway=gateway;this.json=json;this.jdbc=jdbc;}
    public record Receipt(String status,String objectId) {}
    /** Inspect a durable result without creating or replaying a platform request. */
    public Receipt receipt(TenantId tenant,String id,String account) {
        return jdbc.query("SELECT status,qc_carousel_id FROM creative_carousel_platform WHERE tenant_id=? AND carousel_id=? AND account_id=?",
                (r,n)->new Receipt(r.getString(1),r.getString(2)),tenant.value(),id,account).stream().findFirst().orElse(new Receipt("PENDING",null));
    }
    public String ensure(TenantId tenant,String id,String account) {
        var bundle=bundles.detail(tenant,id);
        if(!java.util.Set.of("APPROVED","NOT_REQUIRED").contains(bundle.aiReview().status()))throw new BizException(1525,"上传图文质检未通过或引擎图文尚未就绪");
        var c=gateway.credential(tenant,account,null);
        var body=json.createObjectNode();body.put("advertiser_id",CreationQianchuanGateway.numericId(c.advertiserId().value()));
        body.put("file_name",bundle.name());
        bundle.parts().stream().filter(p->p.role().equals("TEXT")).findFirst().ifPresent(p->body.put("description",p.asset().textContent()));
        var images=body.putArray("images");
        for(var part:bundle.parts()) {
            if(part.role().equals("TEXT"))continue;
            var sync=part.asset().syncs().stream().filter(s->account.equals(s.accountId())&&"UPLOADED".equals(s.status())&&!"REJECTED".equals(s.reviewStatus())&&s.objectId()!=null).findFirst().orElseThrow(()->new BizException(1525,"图文组件正在同步千川"));
            if(part.role().equals("IMAGE"))images.addObject().put("image_id",sync.objectId());else body.put("audio_id",sync.objectId());
        }
        var old=jdbc.query("SELECT status,qc_carousel_id FROM creative_carousel_platform WHERE tenant_id=? AND carousel_id=? AND account_id=?",(r,n)->new String[]{r.getString(1),r.getString(2)},tenant.value(),id,account);
        if(!old.isEmpty()) {
            if("SUCCEEDED".equals(old.getFirst()[0]))return old.getFirst()[1];
            if(!"FAILED".equals(old.getFirst()[0]))throw new BizException(1523,"图文创建结果待确认，请在平台核对后处理");
            if(jdbc.update("UPDATE creative_carousel_platform SET status='SENDING',updated_at=? WHERE tenant_id=? AND carousel_id=? AND account_id=? AND status='FAILED'",Timestamp.from(Instant.now()),tenant.value(),id,account)!=1)throw new BizException(1523,"图文正在创建，请稍后查询");
        }else {
            try{jdbc.update("INSERT INTO creative_carousel_platform(tenant_id,carousel_id,account_id,status,updated_at) VALUES(?,?,?,'SENDING',?)",tenant.value(),id,account,Timestamp.from(Instant.now()));}
            catch(DuplicateKeyException e){throw new BizException(1523,"图文正在创建，请稍后查询");}
        }
        try {
            var data=gateway.write(c,"https://api.oceanengine.com/open_api/2/carousel/create/",body);
            String qc=data.path("carousel").path("carousel_id").asText(data.path("carousel_id").asText());
            try { CreationQianchuanGateway.numericId(qc); } catch(BizException missing) { throw new BizException(1523,"千川未返回图文 ID，结果待确认"); }
            jdbc.update("UPDATE creative_carousel_platform SET status='SUCCEEDED',qc_carousel_id=?,updated_at=? WHERE tenant_id=? AND carousel_id=? AND account_id=?",qc,Timestamp.from(Instant.now()),tenant.value(),id,account);
            return qc;
        }catch(BizException e){jdbc.update("UPDATE creative_carousel_platform SET status=?,error_message=?,updated_at=? WHERE tenant_id=? AND carousel_id=? AND account_id=?",e.code()==1523?"UNCERTAIN":"FAILED",e.getMessage(),Timestamp.from(Instant.now()),tenant.value(),id,account);throw e;}
    }
}
