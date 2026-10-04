package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.List;

public interface SuggestBudgetPort {
    List<BudgetSuggestion> suggest(Credential c, AwemeId a, Money budget);
}
