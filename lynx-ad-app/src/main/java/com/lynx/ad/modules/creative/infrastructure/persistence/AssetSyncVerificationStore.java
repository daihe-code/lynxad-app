package com.lynx.ad.modules.creative.infrastructure.persistence;

import com.lynx.ad.modules.creative.domain.port.AssetReceiptLookupPort;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class AssetSyncVerificationStore {
    public record Claim(AssetUploadRepository.Sync sync,String key,String advertiser,String signature,Instant dispatchedAt) {}
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public AssetSyncVerificationStore(JdbcTemplate jdbc,PlatformTransactionManager manager){this(jdbc,manager,Clock.systemUTC());}
    public AssetSyncVerificationStore(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.clock=clock;}
    public Optional<Claim> claim(AssetUploadRepository.Sync s) {
        Instant now=clock.instant();String key=UUID.randomUUID().toString();
        int changed=jdbc.update("UPDATE creative_asset_platform SET verification_key=?,verification_lease_until=?,verification_checked_at=?,verification_state='CHECKING',verification_message='正在只读查询千川文件记录，不会重复上传' WHERE tenant_id=? AND id=? AND asset_id=? AND account_id=? AND status='UNCERTAIN' AND attempts=? AND (verification_lease_until IS NULL OR verification_lease_until<=?) AND (verification_checked_at IS NULL OR verification_checked_at<=?)",
            key,ts(now.plusSeconds(120)),ts(now),s.tenantId(),s.id(),s.assetId(),s.accountId(),s.attempts(),ts(now),ts(now.minusSeconds(30)));
        if(changed!=1)return Optional.empty();
        return jdbc.query("SELECT attempted_advertiser_id,attempted_signature,upload_dispatched_at FROM creative_asset_platform WHERE tenant_id=? AND id=? AND verification_key=?",
            (r,n)->new Claim(s,key,r.getString(1),r.getString(2),r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant()),s.tenantId(),s.id(),key).stream().findFirst();
    }
    public boolean finish(Claim claim,AssetReceiptLookupPort.Result result) {
        return Boolean.TRUE.equals(tx.execute(s->{
            var receipt=claim.sync();
            var owners=jdbc.queryForList("SELECT verification_key FROM creative_asset_platform WHERE tenant_id=? AND id=? AND asset_id=? AND account_id=? AND status='UNCERTAIN' AND attempts=? FOR UPDATE",String.class,receipt.tenantId(),receipt.id(),receipt.assetId(),receipt.accountId(),receipt.attempts());
            if(owners.isEmpty()||!claim.key().equals(owners.getFirst()))return false;
            if("CONFIRMED".equals(result.state())) {
                if(result.objectId()==null||result.objectId().isBlank()||result.materialId()==null||result.materialId().isBlank())throw new IllegalArgumentException("核查结果缺少文件标识");
                jdbc.update("UPDATE creative_asset_platform SET status='UPLOADED',advertiser_id=?,object_id=?,material_id=?,review_status='UNKNOWN',error_message=NULL WHERE tenant_id=? AND id=? AND verification_key=?",claim.advertiser(),result.objectId(),result.materialId(),receipt.tenantId(),receipt.id(),claim.key());
            }
            jdbc.update("UPDATE creative_asset_platform SET verification_state=?,verification_message=?,verification_checked_at=?,verification_key=NULL,verification_lease_until=NULL,updated_at=? WHERE tenant_id=? AND id=? AND verification_key=?",result.state(),message(result.state()),ts(clock.instant()),ts(clock.instant()),receipt.tenantId(),receipt.id(),claim.key());
            return true;
        }));
    }
    public static String message(String state){return switch(state){
        case "CONFIRMED"->"已按上传时的广告账户和文件签名核查，回执已补齐；本次未上传文件，平台审核状态仍待查询";
        case "NOT_FOUND"->"本次未查到足够匹配记录，平台可能仍在处理；保留待确认状态，不会重新上传";
        case "AMBIGUOUS"->"找到多个不同文件标识，暂不能唯一确认原回执；保留待确认状态，不会重新上传";
        case "INCOMPLETE"->"平台返回记录或分页信息不完整，暂不能确认；保留待确认状态，不会重新上传";
        case "MISSING_EVIDENCE"->"缺少上传时的账户或文件签名证据，无法自动补齐回执；保留待确认状态，不会重新上传";
        case "SCOPE_CHANGED"->"当前广告账户与上传时不一致，未查询或绑定其他广告账户的素材";
        case "AUTH_REQUIRED"->"千川授权暂不可用，请恢复授权后再次核查；本次没有重新上传";
        case "BUSY"->"已有核查正在进行或尚在刷新冷却期，请稍后读取结果";
        default->"本次只读查询未完成，已保留待确认状态，可稍后再次核查，不会重新上传";
    };}
    private static Timestamp ts(Instant v){return Timestamp.from(v);}
}
