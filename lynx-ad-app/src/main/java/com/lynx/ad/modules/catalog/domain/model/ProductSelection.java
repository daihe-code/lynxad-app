package com.lynx.ad.modules.catalog.domain.model;

import com.lynx.ad.common.domain.Ids.AwemeId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import java.util.List;

public record ProductSelection(AwemeId awemeId, List<QcProductId> ids) {

    public int size() {
        return ids == null ? 0 : ids.size();
    }
}
