package com.lynx.ad.modules.identity.domain.model;

public record EncryptedToken(String cipherText) {

    @Override
    public String toString() {
        return "EncryptedToken(****)";
    }
}
