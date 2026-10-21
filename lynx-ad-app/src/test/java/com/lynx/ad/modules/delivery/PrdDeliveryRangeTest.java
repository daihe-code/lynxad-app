package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.lynx.ad.modules.delivery.application.CreationPayloadBuilder;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.common.exception.BizException;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrdDeliveryRangeTest {
    private CreationState valid() {
        var state = new CreationState();
        state.smartBidType="CUSTOM"; state.budget=new BigDecimal("300");
        state.roi2Goal=new BigDecimal("4.7"); state.scheduleType="FROM_NOW"; state.name="无费用校验";
        return state;
    }
    @ParameterizedTest @ValueSource(strings={"0.01","0.1","50","100"})
    void acceptsFullPrdRoiRange(String amount) {
        var state=valid();state.roi2Goal=new BigDecimal(amount);
        assertDoesNotThrow(()->CreationPayloadBuilder.settings(state));
    }
    @ParameterizedTest @ValueSource(strings={"300","100000.01","999999999.99"})
    void acceptsFullPrdCostBudgetRange(String amount) {
        var state=valid();state.budget=new BigDecimal(amount);
        assertDoesNotThrow(()->CreationPayloadBuilder.settings(state));
    }
    @ParameterizedTest @ValueSource(strings={"0","100.01","0.001"})
    void rejectsInvalidRoi(String amount) {
        var state=valid();state.roi2Goal=new BigDecimal(amount);
        assertThrows(BizException.class,()->CreationPayloadBuilder.settings(state));
    }
}
