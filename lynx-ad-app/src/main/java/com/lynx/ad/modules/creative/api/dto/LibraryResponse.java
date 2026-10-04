package com.lynx.ad.modules.creative.api.dto;

import java.util.List;

public record LibraryResponse(
        boolean qianchuanBound,
        TabCounts tabCounts,
        StatusCounts statusCounts,
        PageInfo pageInfo,
        List<LibraryItemVo> items) {

    public record TabCounts(int video, int image, int carousel) {}

    public record StatusCounts(int all, int used, int unused) {}

    public record PageInfo(int page, int pageSize, int total) {}
}
