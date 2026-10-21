package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.service.PlanStatusPolicy;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PlanStatusPolicyTest {
    record Case(String name,String status,String qcStatus,String group,String label,List<String> include,List<String> exclude) {
        @Override public String toString(){return name;}
    }
    static Stream<Case> cases()throws Exception {
        try(var input=PlanStatusPolicyTest.class.getResourceAsStream("/plan-status-cases.json")) {
            return new ObjectMapper().readValue(input,new TypeReference<List<Case>>(){}).stream();
        }
    }
    @ParameterizedTest(name="{0}") @MethodSource("cases")
    void sameContractAsBrowserWithoutGrantingAnyOperations(Case row) {
        var display=PlanStatusPolicy.display(row.status(),row.qcStatus());
        assertEquals(row.group(),display.group());assertEquals(row.label(),display.label());
        assertEquals(!"DELETED".equals(row.group()),PlanStatusPolicy.matches(row.status(),row.qcStatus(),null));
        assertTrue(PlanStatusPolicy.matches(row.status(),row.qcStatus(),"ALL_INCLUDE_DELETED"));
        row.include().forEach(filter->{PlanStatusPolicy.validate(filter);assertTrue(PlanStatusPolicy.matches(row.status(),row.qcStatus(),filter),filter);});
        row.exclude().forEach(filter->assertFalse(PlanStatusPolicy.matches(row.status(),row.qcStatus(),filter),filter));
    }
    @Test void inclusionOfDeletedRecordsIsAlwaysExplicitAndInvalidFiltersFailBeforeLoading() {
        for(String filter:List.of("DELETED","DELETE","ALL_INCLUDE_DELETED"))assertTrue(PlanStatusPolicy.includesDeleted(filter));
        for(String filter:List.of("","ALL","PAUSED","UNKNOWN"))assertFalse(PlanStatusPolicy.includesDeleted(filter));
        assertFalse(PlanStatusPolicy.includesDeleted(null));
        assertEquals(1402,assertThrows(BizException.class,()->PlanStatusPolicy.validate("NOT_A_FILTER")).code());
    }
}
