package com.lynx.ad.modules.creative.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.port.AssetReceiptLookupPort;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.Platform;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AssetSyncReconciliationService {
    private final AssetUploadRepository repository;private final AssetSyncVerificationStore verifications;
    private final AssetUploadService files;private final OauthAccountRepository accounts;
    private final QianchuanCredentialFacade credentials;private final AssetReceiptLookupPort lookup;
    public AssetSyncReconciliationService(AssetUploadRepository repository,AssetSyncVerificationStore verifications,AssetUploadService files,OauthAccountRepository accounts,QianchuanCredentialFacade credentials,AssetReceiptLookupPort lookup){this.repository=repository;this.verifications=verifications;this.files=files;this.accounts=accounts;this.credentials=credentials;this.lookup=lookup;}
    public record Result(String state,String message,AssetUploadService.Detail asset) {}
    public Result reconcile(TenantId tenant,String id,String account) {
        var asset=repository.require(tenant.value(),id);if(asset.canonicalId()!=null)asset=repository.require(tenant.value(),asset.canonicalId());
        if(account==null||account.isBlank())throw new BizException(1520,"请明确选择要核查的千川授权账户");
        var bound=accounts.findById(tenant,new AccountId(account)).orElseThrow(()->new BizException(1201,"授权账户不存在或不属于当前灵投账户"));
        if(bound.platform()!=Platform.QIANCHUAN||"DELETED".equals(bound.status()))throw new BizException(1201,"请选择已有千川授权账户");
        var sync=repository.syncs(tenant.value(),asset.id()).stream().filter(s->account.equals(s.accountId())).findFirst().orElseThrow(()->new BizException(1522,"该素材没有所选账户的上传记录"));
        if(!"UNCERTAIN".equals(sync.status()))return new Result("UPLOADED".equals(sync.status())?"CONFIRMED":"NOT_REQUIRED","当前上传记录未处于待确认状态，本次没有发起上传或生成请求",files.detail(tenant,asset.id()));
        var claim=verifications.claim(sync).orElse(null);
        if(claim==null)return new Result("BUSY",AssetSyncVerificationStore.message("BUSY"),files.detail(tenant,asset.id()));
        AssetReceiptLookupPort.Result result;
        try {
            var payload=claim.signature()!=null&&claim.signature().equalsIgnoreCase(Objects.toString(asset.md5(),""))?asset:files.syncPayloadForSignature(tenant,asset,claim.signature());
            if(payload==null||!"STORED".equals(payload.storageStatus())||claim.dispatchedAt()==null||claim.advertiser()==null||claim.signature()==null||!claim.signature().matches("(?i)[0-9a-f]{32}"))result=AssetReceiptLookupPort.Result.pending("MISSING_EVIDENCE");
            else {
                var c=credentials.getUsableCredential(tenant,new AccountId(account));
                if(!claim.advertiser().equals(c.advertiserId().value()))result=AssetReceiptLookupPort.Result.pending("SCOPE_CHANGED");
                else result=lookup.find(c,new AssetReceiptLookupPort.Expected(AssetKind.valueOf(payload.kind()),claim.signature(),payload.sizeBytes(),payload.width(),payload.height(),payload.isAigc(),sync.objectId(),sync.materialId()));
            }
        }catch(BizException unavailable){result=AssetReceiptLookupPort.Result.pending(Set.of(1201,1205,1211,401).contains(unavailable.code())||com.lynx.ad.common.http.Qianchuan401ReplayCodes.isAuthExpired(unavailable)?"AUTH_REQUIRED":"UNAVAILABLE");}
        catch(RuntimeException unavailable){result=AssetReceiptLookupPort.Result.pending("UNAVAILABLE");}
        if(!verifications.finish(claim,result))return new Result("BUSY","核查记录已发生变化，请读取当前上传记录；本次未重发上传",files.detail(tenant,asset.id()));
        return new Result(result.state(),AssetSyncVerificationStore.message(result.state()),files.detail(tenant,asset.id()));
    }
}
