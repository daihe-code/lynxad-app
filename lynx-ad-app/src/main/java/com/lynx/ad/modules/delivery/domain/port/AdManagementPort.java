package com.lynx.ad.modules.delivery.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

/** Non-creation management operations. Authorization lifecycle remains in identity. */
public interface AdManagementPort {
    JsonNode detail(Credential credential, String adId);
    Map<String, String> guarantees(Credential credential, List<String> adIds);
    void settings(Credential credential, String adId, String name, java.math.BigDecimal budget, java.math.BigDecimal roi);
    default void settings(Credential credential, String adId, String name, java.math.BigDecimal budget, java.math.BigDecimal roi, String deepExternalAction) {
        settings(credential,adId,name,budget,roi);
    }
    List<JsonNode> report(Credential credential, LocalDate date);
    List<JsonNode> report(Credential credential, LocalDate startDate, LocalDate endDate);
    List<JsonNode> materials(Credential credential, String adId);
    default List<JsonNode> materialReport(Credential credential,String adId,LocalDate start,LocalDate end,String type) {
        throw new com.lynx.ad.common.exception.BizException(1406,"当前账户暂未提供素材报表");
    }
    default List<JsonNode> materialMetadata(Credential credential,String type,List<String> materialIds) { return List.of(); }
    default List<JsonNode> materialAttributes(Credential credential,List<String> materialIds) { return List.of(); }
    default List<JsonNode> products(Credential credential,String adId,LocalDate start,LocalDate end) {
        throw new com.lynx.ad.common.exception.BizException(1406,"当前账户暂未提供商品报表");
    }
    default void deleteProduct(Credential credential,String adId,String productId) {
        throw new com.lynx.ad.common.exception.BizException(1406,"当前账户暂不支持删除计划商品");
    }
    default List<JsonNode> monitoringProducts(Credential c,String id,LocalDate start,LocalDate end){return products(c,id,start,end);}
    default void automaticContent(Credential c,String id,String operation,JsonNode payload){throw new com.lynx.ad.common.exception.BizException(1406,"当前数据源不支持自动内容调控");}
    default String automaticControl(Credential c,String id,String scene,String name,java.math.BigDecimal budget,java.math.BigDecimal hours,List<String> materials){return control(c,id,scene,name,budget,hours,materials);}
    String control(Credential credential, String adId, String scene, String name,
                   java.math.BigDecimal budget, java.math.BigDecimal duration, List<String> materialIds);
    List<JsonNode> controls(Credential credential, String adId);
    default List<JsonNode> controls(Credential credential,String adId,String scene) {
        return controls(credential,adId).stream().filter(row->scene.equals(row.path("scene").asText())).toList();
    }
    default List<JsonNode> controls(Credential credential,String adId,String scene,LocalDate start,LocalDate end) {
        throw new com.lynx.ad.common.exception.BizException(1406,"当前账户暂未提供调控任务数据");
    }
    record InsightReport(JsonNode summary,List<JsonNode> trend,String timeDimension,Map<String,String> metricFields,String warning) {}
    default void controlStatus(Credential credential,String taskId,String operation) {
        throw new com.lynx.ad.common.exception.BizException(1406,"当前账户暂不支持修改调控任务");
    }
    default InsightReport controlReport(Credential credential,String adId,String taskId,String scene,LocalDate start,LocalDate end,String granularity) {
        throw new com.lynx.ad.common.exception.BizException(1406,"当前账户暂未提供调控任务报表");
    }
    default InsightReport insightReport(Credential credential,String adId,LocalDate start,LocalDate end) {
        throw new com.lynx.ad.common.exception.BizException(1406,"当前账户暂未提供计划趋势报表");
    }
    default InsightReport insightReport(Credential credential,String adId,LocalDate start,LocalDate end,boolean daily) {
        return insightReport(credential,adId,start,end);
    }
    default JsonNode operationLogs(Credential credential,String adId,LocalDate start,LocalDate end,int page,int pageSize) {
        throw new com.lynx.ad.common.exception.BizException(1406,"当前账户暂未提供千川操作日志");
    }
    default JsonNode guaranteeDetail(Credential credential,String adId) {
        String status=guarantees(credential,List.of(adId)).get(adId);
        return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("compensate_status",status);
    }
}
