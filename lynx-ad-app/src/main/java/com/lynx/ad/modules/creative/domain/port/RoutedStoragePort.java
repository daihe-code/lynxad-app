package com.lynx.ad.modules.creative.domain.port;

/** Select the persisted location of an asset independently of the default used for new writes. */
public interface RoutedStoragePort extends StoragePort {
    StoragePort forMode(String mode);
    static StoragePort select(StoragePort port, String mode) {
        return port instanceof RoutedStoragePort routed ? routed.forMode(mode) : port;
    }
}
