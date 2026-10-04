package com.lynx.ad.modules.tenant.api.dto;

public record UpdatePasswordRequest(String oldPassword, String newPassword) {}
