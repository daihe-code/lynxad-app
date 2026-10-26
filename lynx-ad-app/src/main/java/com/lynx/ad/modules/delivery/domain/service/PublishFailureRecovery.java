package com.lynx.ad.modules.delivery.domain.service;

import java.util.Set;
import java.util.regex.Pattern;

/** Only explicit rejection evidence identifies an editor; unknown errors require the user's choice. */
public final class PublishFailureRecovery {
    private PublishFailureRecovery() {}
    private static final Pattern ACCOUNT = pattern("授权账户|(?:账户|广告主|抖音号).{0,12}(?:授权|权限)|授权(?:已)?(?:失效|过期|无效|取消|不可用)|凭证|权限不足|无权限操作|无权操作(?:此)?账户|access_token|refresh_token|\\b(?:advertiser_id|aweme_id)\\b");
    private static final Pattern ESTIMATE = pattern("(?:效果预估|投放参考信息|预估结果|平台预估).{0,30}(?:更新|过期|失效|重新获取)");
    private static final Pattern PRODUCT = pattern("\\b(?:product_ids?|goods_ids?|commodity_ids?)\\b|库存不足|缺货|商品(?!素材|视频|图片|图文|标题|文案)[^，。；;,]{0,20}(?:下架|不存在|不可投|不可推广|无投放权限|类目不支持|资质不符合)");
    private static final Pattern GOAL = pattern("预算|日限额|投放时间|开始时间|结束时间|投放日期|转化目标|成本目标|投放方式|\\b(?:roi|roi2_goal|budget|start_date|end_date|start_time|end_time|schedule_type|smart_bid_type)\\b");
    private static final Pattern MATERIAL = pattern("素材|视频|图片|图文|封面|创意|标题|文案|\\b(?:video_ids?|image_ids?|creative_list|title_list)\\b");
    private static Pattern pattern(String expression) { return Pattern.compile(expression, Pattern.CASE_INSENSITIVE); }

    public static String classify(Integer code, String message) {
        if (code != null && Set.of(1201,1205,1211,40102,40105).contains(code)) return "ACCOUNTS";
        String detail = message == null ? "" : message.replaceAll("https?://\\S+", "")
                .split("[（(](?:错误码|code)", 2)[0];
        if (ACCOUNT.matcher(detail).find()) return "ACCOUNTS";
        if (ESTIMATE.matcher(detail).find()) return "REFRESH_ESTIMATE";
        // If several rejected fields are named, repair the earliest dependent wizard step first.
        if (PRODUCT.matcher(detail).find()) return "PRODUCTS";
        if (GOAL.matcher(detail).find()) return "GOAL";
        if (MATERIAL.matcher(detail).find()) return "MATERIALS";
        return "UNKNOWN";
    }
}
