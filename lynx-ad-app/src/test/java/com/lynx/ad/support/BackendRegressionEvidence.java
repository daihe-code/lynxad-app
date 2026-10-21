package com.lynx.ad.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits only explicit, non-sensitive observations after successful assertions. */
public final class BackendRegressionEvidence {
    private BackendRegressionEvidence() {}
    public static void record(String id,Map<String,?> observations) {
        var data=new LinkedHashMap<String,Object>();
        data.put("id",id);data.put("at",Instant.now().toString());data.put("observations",observations);
        try { System.out.println("BACKEND_REGRESSION_EVIDENCE "+new ObjectMapper().writeValueAsString(data)); }
        catch(Exception ex) { throw new AssertionError("Cannot serialize regression evidence",ex); }
    }
}
