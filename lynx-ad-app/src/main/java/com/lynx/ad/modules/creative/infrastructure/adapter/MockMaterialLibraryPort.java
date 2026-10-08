package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lynx.ad.mock.qianchuan", havingValue = "true", matchIfMissing = true)
public class MockMaterialLibraryPort implements MaterialLibraryPort {

    @Override
    public MaterialPage list(MaterialLibraryQuery query) {
        // Mock authorization must not fabricate materials in the user's library.
        return new MaterialPage(List.of());
    }
}
