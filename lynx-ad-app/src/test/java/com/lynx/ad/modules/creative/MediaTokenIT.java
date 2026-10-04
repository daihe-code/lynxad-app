package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.service.MediaTokenSigner;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MediaTokenIT {

    @Test
    void expiredOrForeignTenantRejected() {
        String secret = "dev-only-aes256-key-32bytes!!!!";
        String expired = MediaTokenSigner.sign(secret, "t1", "lynx-ad/t1/creative/a.jpg", Instant.now().minusSeconds(10));
        BizException a = assertThrows(BizException.class, () -> MediaTokenSigner.verify(secret, expired));
        assertEquals(1508, a.code());
        String other = MediaTokenSigner.sign(secret, "t2", "lynx-ad/t1/creative/a.jpg", Instant.now().plusSeconds(60));
        var payload = MediaTokenSigner.verify(secret, other);
        assertEquals("t2", payload.tenantId());
    }
}
