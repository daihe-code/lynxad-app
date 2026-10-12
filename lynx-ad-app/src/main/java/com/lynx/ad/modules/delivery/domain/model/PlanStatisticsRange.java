package com.lynx.ad.modules.delivery.domain.model;

import com.lynx.ad.common.exception.BizException;
import java.time.*;
import java.time.temporal.ChronoUnit;

/** Calendar days in the platform's Asia/Shanghai reporting timezone, both ends included. */
public record PlanStatisticsRange(LocalDate startDate,LocalDate endDate) {
    public PlanStatisticsRange {
        if(startDate==null || endDate==null) throw new BizException(1402,"请选择完整的统计起止日期");
        long days=ChronoUnit.DAYS.between(startDate,endDate)+1;
        if(days<1) throw new BizException(1402,"统计开始日期不能晚于结束日期");
        if(days>180) throw new BizException(1402,"统计时间区间最长为180天（包含起止日期）");
        if(endDate.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))) throw new BizException(1402,"统计日期不能晚于今天");
    }
    public static PlanStatisticsRange optional(String start,String end) {
        if(start==null && end==null)return null;
        try { return new PlanStatisticsRange(start==null?null:LocalDate.parse(start),end==null?null:LocalDate.parse(end)); }
        catch(java.time.format.DateTimeParseException ex) { throw new BizException(1402,"统计日期格式应为YYYY-MM-DD"); }
    }
}
