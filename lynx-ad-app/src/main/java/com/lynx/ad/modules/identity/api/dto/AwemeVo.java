package com.lynx.ad.modules.identity.api.dto;

public record AwemeVo(String awemeId, String name, String avatar, String type, boolean isPrimary, String sceneHint,
                      java.util.List<String> authTypes, Boolean isAllowMall) {}
