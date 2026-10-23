package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.modules.tenant.api.dto.CurrentUserResponse;

/** Internal result; controllers expose the current user body and session header separately. */
public record AuthenticatedUserUpdate(CurrentUserResponse currentUser, String token) {
    @Override
    public String toString() {
        return "AuthenticatedUserUpdate[token=REDACTED]";
    }
}
