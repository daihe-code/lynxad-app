package com.lynx.ad.common.crypto;

public interface Encryptor {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
