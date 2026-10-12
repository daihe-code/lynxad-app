package com.lynx.ad.modules.creative.domain.service;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Shared V2 contract with LynxAd-ai-engine; origin is metadata, not a directory. */
public final class AssetStoragePolicy {
    private AssetStoragePolicy() {}
    public static String key(TenantId tenant,String asset,String filename) {
        if(!tenant.value().matches("[A-Za-z0-9_-]{1,128}") || !asset.matches("[A-Za-z0-9_-]{1,128}"))
            throw new BizException(1520,"素材存储归属不合法");
        String ext=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(java.util.Locale.ROOT);
        if(!ext.matches("[a-z0-9]{1,8}"))throw new BizException(1520,"素材扩展名不合法");
        return "lynx-ad/v2/"+tenant.value()+"/creative/"+LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE)+"/"+asset+"/source."+ext;
    }
    public static String bucket(LynxAdProperties.Oss config,String kind) {
        return switch(kind) {case "IMAGE"->config.getImageBucket();case "AUDIO"->config.getVoiceBucket();case "VIDEO"->config.getVideoBucket();case "TEXT"->null;default->throw new BizException(1520,"素材类型不支持");};
    }
    public static void assertV2(String tenant,String key) {
        if(key==null || !key.matches("lynx-ad/v2/"+java.util.regex.Pattern.quote(tenant)+"/creative/[0-9]{8}/[A-Za-z0-9_-]{1,128}/source\\.[a-z0-9]{1,8}"))
            throw new BizException(1521,"素材路径与租户不匹配");
    }
}
