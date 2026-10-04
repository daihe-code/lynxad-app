package com.lynx.ad.modules.catalog.infrastructure.adapter;

import com.lynx.ad.modules.catalog.domain.port.ProductPoolPort;
import java.util.List;

/** DevSeedRunner 专用烘焙种子入口，不经 Live。 */
public final class MockProductPool {

    private MockProductPool() {}

    public static List<ProductPoolPort.PoolItem> bakery() {
        return MockProductPoolAdapter.bakery();
    }
}
