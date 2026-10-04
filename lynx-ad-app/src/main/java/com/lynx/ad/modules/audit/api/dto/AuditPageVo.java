package com.lynx.ad.modules.audit.api.dto;

import java.util.List;

public record AuditPageVo(List<AuditRecordVo> list, long total, int pageNum, int pageSize) {}
