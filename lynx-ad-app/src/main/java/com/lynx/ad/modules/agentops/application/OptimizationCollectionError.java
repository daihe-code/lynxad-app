package com.lynx.ad.modules.agentops.application;

import com.lynx.ad.common.http.OceanJson;

public final class OptimizationCollectionError {
    static final String AUTHORIZATION = "广告账户授权已失效，请重新授权。";
    static final String UNAVAILABLE = "暂时无法获取最新投放数据，系统会自动重试。";
    private static final String TIMEOUT = "平台响应超时，系统会自动重试。";

    private OptimizationCollectionError() {}

    public static String publicMessage(String message) {
        if (message == null) return null;
        if (OceanJson.isRpcTimeoutMessage(message)) return TIMEOUT;
        return switch (message) {
            case "授权失效", "授权已失效", "授权已失效，请重新授权", "授权已取消或失效，请重新授权",
                    "千川授权已失效，请在账户管理中重新授权", "广告账户授权已失效，请重新授权",
                    "授权令牌已过期，等待授权模块更新或重新授权", AUTHORIZATION -> AUTHORIZATION;
            case TIMEOUT, "等待当天最新报表", "投放状态正在核对", "投放状态已变化，等待最新检查",
                    "平台计划信息不完整，暂无法盯盘", "平台计划预算或模式缺失", "计划不存在",
                    "草稿尚未发布", "计划已结束", "真实盯盘需要连接千川正式数据源",
                    "盯盘服务尚未就绪，请联系管理员完成服务配置" -> message;
            default -> UNAVAILABLE;
        };
    }
}
