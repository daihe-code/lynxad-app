package com.lynx.ad.modules.delivery.domain.port;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.util.Optional;

public interface SuggestRoiPort {
    Optional<RoiSuggestion> suggest(Credential c, AwemeId a);
}
