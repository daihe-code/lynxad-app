package com.lynx.ad.modules.delivery.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import java.util.*;

/** Read-only classification. It must never grant pause/resume or optimization permissions. */
public final class PlanStatusPolicy {
    public record Option(String value,String label,List<Option> children) {}
    public record Display(String group,String detail,String label) {}
    public record Rules(String source,List<Option> options,Map<String,String> localLabels,Map<String,Display> platform) {}
    public static final Rules RULES=load();
    private PlanStatusPolicy() {}
    private static Rules load() {
        try(var input=PlanStatusPolicy.class.getResourceAsStream("/plan-status-policy.json")) {
            if(input==null)throw new IllegalStateException("Missing plan status policy");
            return new ObjectMapper().readValue(input,Rules.class);
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    public static Display display(String status,String qcStatus) {
        String local=Objects.toString(status,"UNKNOWN"),raw=qcStatus==null?"":qcStatus.trim();
        if("DELETED".equals(local))return local(local); // Durable deletion wins over stale cached payloads.
        var remote=RULES.platform().get(raw);
        if(remote!=null) {
            if(Set.of("DELETED","TERMINATED","COMPLETED","UNKNOWN").contains(remote.group()))return remote;
            // A confirmed local operation may precede the next platform sync. Do not restore its older state.
            if(Set.of("DRAFT","SUBMITTING").contains(local)
                    ||"PAUSED".equals(local)&&"DELIVERING".equals(remote.group())
                    ||"DELIVERING".equals(local)&&"DISABLE".equals(remote.detail())
                    ||"FINISHED".equals(local)&&!Set.of("FINISHED","COMPLETED","TERMINATED").contains(remote.group()))return local(local);
            return remote;
        }
        if(!raw.isEmpty())return new Display("UNKNOWN",null,"平台状态："+raw);
        return local(local);
    }
    private static Display local(String status) {return new Display(RULES.localLabels().containsKey(status)?status:"UNKNOWN",null,RULES.localLabels().getOrDefault(status,"状态待核查"));}
    public static boolean includesDeleted(String filter) {return Set.of("DELETED","DELETE","ALL_INCLUDE_DELETED").contains(Objects.toString(filter,""));}
    public static void validate(String filter) {
        if(filter==null||filter.isBlank()||"ALL".equals(filter)||RULES.platform().containsKey(filter)||contains(RULES.options(),filter))return;
        throw new BizException(1402,"不支持的投放状态筛选");
    }
    private static boolean contains(List<Option> options,String filter) {
        return options.stream().anyMatch(option->option.value().equals(filter)||option.children()!=null&&contains(option.children(),filter));
    }
    public static boolean matches(String status,String qcStatus,String filter) {
        var display=display(status,qcStatus);
        if("ALL_INCLUDE_DELETED".equals(filter))return true;
        if(filter==null||filter.isBlank()||"ALL".equals(filter))return !"DELETED".equals(display.group());
        if("PENDING_AUDIT".equals(filter))return Set.of("AUDIT","REAUDIT","PENDING_AUDIT").contains(display.group());
        if("FINISHED".equals(filter))return Set.of("COMPLETED","TERMINATED","FINISHED").contains(display.group());
        if(filter.equals(display.group())||filter.equals(display.detail()))return true;
        var raw=RULES.platform().get(filter);
        return raw!=null&&(raw.detail()!=null?raw.detail().equals(display.detail()):raw.group().equals(display.group())
                &&(filter.equals(qcStatus)||Set.of("DELIVERY_OK","DELETE","TIME_DONE","FROZEN","OFFLINE_AUDIT").contains(filter)));
    }
}
