package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.List;

/** Application safeguard, not a Qianchuan platform limit. Stored under the shared draft row lock. */
public final class RegenerationPolicy {
    public static final long COOLDOWN_MS=30_000;
    private RegenerationPolicy() {}
    public static String instruction(List<String> reasons,String description) {
        if(reasons==null||reasons.isEmpty()||reasons.size()>10||reasons.stream().anyMatch(r->r==null||r.isBlank()||r.codePointCount(0,r.length())>100))throw new BizException(1503,"请选择重新生成原因");
        String detail=description==null?"":description.strip();
        if(detail.codePointCount(0,detail.length())>50)throw new BizException(1503,"重新生成说明最多50字");
        String text=String.join("、",reasons.stream().map(String::strip).distinct().toList())+(detail.isEmpty()?"":"；"+detail);
        if(text.codePointCount(0,text.length())>200)throw new BizException(1503,"重新生成原因与说明合计最多200字，请精简后提交");
        return text;
    }
    public static void reserve(CreationState state,long now) {
        if(state.regenerationRetryAt>now)throw new BizException(429,"重新生成操作过于频繁，请在 "+Math.max(1,(state.regenerationRetryAt-now+999)/1000)+" 秒后再试");
        state.regenerationRetryAt=now+COOLDOWN_MS;
    }
}
