package com.lynx.ad.modules.creative.domain.port;

import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.nio.file.Path;

public interface AssetUploadPort {
    default void checkAvailable() {}
    Result upload(Credential credential, AssetKind kind, Path file, String filename, String mime, String md5, boolean aigc);
    record Result(String objectId, String materialId, String requestId) {}
}
