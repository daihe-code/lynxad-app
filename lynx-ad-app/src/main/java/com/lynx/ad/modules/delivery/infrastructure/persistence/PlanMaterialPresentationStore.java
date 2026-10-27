package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Only a published material's display label; never exposes a publisher's draft, files or credentials. */
@Repository
public class PlanMaterialPresentationStore {
    public record Label(String name,List<String> titles) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PlanMaterialPresentationStore(JdbcTemplate jdbc,ObjectMapper json) {this.jdbc=jdbc;this.json=json;}

    public Optional<Label> label(TenantId tenant,AccountId account,String qcId,String type,String objectId,Collection<String> products,List<String> nativeTitles) {
        if(objectId==null || products.isEmpty() || nativeTitles.isEmpty() || !Set.of("VIDEO","IMAGE").contains(type))return Optional.empty();
        var candidates=new LinkedHashMap<String,Label>();
        jdbc.query("""
            SELECT c.state_json,upload.asset_id FROM oauth_account viewer
            JOIN oauth_account publisher ON publisher.platform=viewer.platform AND publisher.advertiser_id=viewer.advertiser_id
            JOIN ad_plan origin ON origin.tenant_id=publisher.tenant_id AND origin.oauth_account_id=publisher.id
            JOIN delivery_creation c ON c.tenant_id=origin.tenant_id AND c.plan_id=origin.id
            JOIN creative_asset_platform upload ON upload.tenant_id=publisher.tenant_id AND upload.account_id=publisher.id
            JOIN creative_asset asset ON asset.tenant_id=upload.tenant_id AND asset.id=upload.asset_id
            WHERE viewer.tenant_id=? AND viewer.id=? AND viewer.platform='QIANCHUAN' AND viewer.status='ACTIVE'
              AND viewer.grant_id IS NOT NULL AND publisher.grant_id IS NOT NULL AND origin.qc_plan_id=?
              AND asset.kind=? AND asset.storage_status='STORED' AND upload.status='UPLOADED' AND upload.object_id=?
              AND upload.advertiser_id=publisher.advertiser_id
              AND ((origin.source='LINGTOU' AND origin.publish_version>0) OR EXISTS (
                SELECT 1 FROM delivery_publish_attempt receipt WHERE receipt.tenant_id=origin.tenant_id AND receipt.plan_id=origin.id
                  AND receipt.qc_plan_id=origin.qc_plan_id AND receipt.status='CREATED'))
            """,(org.springframework.jdbc.core.RowCallbackHandler) row->{
            try {
                var state=json.readTree(row.getString(1));String asset=row.getString(2);
                if(state==null)return;
                for(var material:state.path("materials")) {
                    if(!type.equals(material.path("type").asText()) || !products.contains(material.path("productId").asText()))continue;
                    boolean matches="VIDEO".equals(type) && asset.equals(material.path("videoAssetId").asText());
                    if("IMAGE".equals(type))for(var image:material.path("imageAssetIds"))if(asset.equals(image.asText()))matches=true;
                    String name=material.path("title").asText("").trim();
                    if(!matches || name.isEmpty())continue;
                    // Only provider-confirmed ad copy can become the preview caption. The material label is separate.
                    var titles=new LinkedHashSet<String>();
                    if(nativeTitles.contains(name))titles.add(name);
                    for(var title:material.path("titles"))if(nativeTitles.contains(title.asText()))titles.add(title.asText());
                    titles.addAll(nativeTitles);candidates.put(name,new Label(name,List.copyOf(titles)));
                }
            }catch(com.fasterxml.jackson.core.JsonProcessingException invalid){/* Old/incomplete snapshots retain native labels. */}
        },tenant.value(),account.value(),qcId,type,objectId);
        return candidates.size()==1?Optional.of(candidates.values().iterator().next()):Optional.empty();
    }
}
