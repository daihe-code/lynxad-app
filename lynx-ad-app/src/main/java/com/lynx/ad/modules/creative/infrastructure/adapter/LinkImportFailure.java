package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.exception.BizException;

/**
 * 链接导入失败：携带失败原因码与仅供服务端日志使用的细节。
 *
 * <p>原因码（reason）随错误响应返回给前端，便于定位失败环节；detail 只写服务端日志，
 * 不返回给用户；给用户的提示文案由 message 承载，不含堆栈信息。
 */
public class LinkImportFailure extends BizException {
    public static final String LINK_FORMAT = "LINK_FORMAT";
    public static final String SEARCH_PAGE = "SEARCH_PAGE";
    public static final String SHORT_LINK_RESOLVE = "SHORT_LINK_RESOLVE";
    public static final String SHARE_PAGE_FETCH = "SHARE_PAGE_FETCH";
    public static final String TTWID_MISSING = "TTWID_MISSING";
    public static final String VIDEOINFO_MISSING = "VIDEOINFO_MISSING";
    public static final String PLAY_URL_EMPTY = "PLAY_URL_EMPTY";
    public static final String VIDEO_PRIVATE = "VIDEO_PRIVATE";
    public static final String VIDEO_DELETED = "VIDEO_DELETED";
    public static final String LOGIN_REQUIRED = "LOGIN_REQUIRED";
    public static final String DOWNLOAD_FAILED = "DOWNLOAD_FAILED";
    public static final String VIDEO_TOO_LARGE = "VIDEO_TOO_LARGE";
    public static final String SPEC_FAILED = "SPEC_FAILED";
    public static final String FFPROBE_UNAVAILABLE = "FFPROBE_UNAVAILABLE";
    public static final String DOWNLOAD_API_FAILED = "DOWNLOAD_API_FAILED";
    public static final String DETAIL_API_FAILED = "DETAIL_API_FAILED";

    private final String reason;
    private final String detail;
    private final String awemeId;

    public LinkImportFailure(String reason, String message, String detail) {
        this(reason, message, detail, null);
    }

    public LinkImportFailure(String reason, String message, String detail, String awemeId) {
        super(1402, message);
        this.reason = reason;
        this.detail = detail;
        this.awemeId = awemeId;
    }

    public String reason() {
        return reason;
    }

    public String detail() {
        return detail;
    }

    public String awemeId() {
        return awemeId;
    }

    /** 补上解析到的视频编号（供详情接口兜底使用）；已有编号时返回自身。 */
    public LinkImportFailure withAwemeId(String id) {
        if (awemeId != null || id == null) return this;
        return new LinkImportFailure(reason, getMessage(), detail, id);
    }
}
