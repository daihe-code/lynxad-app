package com.lynx.ad.modules.creative.api.dto;

import java.util.List;

public record LibraryResponse(
        boolean qianchuanBound,
        TabCounts tabCounts,
        StatusCounts statusCounts,
        PageInfo pageInfo,
        List<LibraryItemVo> items,
        String platformWarning,
        Integer relatedCount,
        String productScope) {
    public LibraryResponse(boolean bound,TabCounts tabs,StatusCounts statuses,PageInfo page,List<LibraryItemVo> items,String warning) { this(bound,tabs,statuses,page,items,warning,null,null); }
    public LibraryResponse(boolean bound,TabCounts tabs,StatusCounts statuses,PageInfo page,List<LibraryItemVo> items) { this(bound,tabs,statuses,page,items,null); }

    public record TabCounts(int video, int image, int carousel, int audio, int text) {
        public TabCounts(int video,int image,int carousel) { this(video,image,carousel,0,0); }
    }

    public record StatusCounts(int all, int used, int unused, int reviewing, int rejected) {}

    public record PageInfo(int page, int pageSize, int total) {}
}
