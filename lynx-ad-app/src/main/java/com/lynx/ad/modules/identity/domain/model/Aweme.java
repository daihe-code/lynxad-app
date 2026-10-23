package com.lynx.ad.modules.identity.domain.model;

import com.lynx.ad.common.domain.Ids.AwemeId;

public class Aweme {

    private final AwemeId awemeId;
    private final String name;
    private final String avatar;
    private final AwemeType type;
    private final boolean primary;
    private final String sceneHint;
    private final java.util.List<String> authTypes;
    private final Boolean isAllowMall;

    public Aweme(AwemeId awemeId, String name, String avatar, AwemeType type, boolean primary, String sceneHint) {
        this(awemeId,name,avatar,type,primary,sceneHint,java.util.List.of(),null);
    }

    public Aweme(AwemeId awemeId, String name, String avatar, AwemeType type, boolean primary, String sceneHint,
                 java.util.List<String> authTypes, Boolean isAllowMall) {
        this.awemeId = awemeId;
        this.name = name;
        this.avatar = avatar;
        this.type = type;
        this.primary = primary;
        this.sceneHint = sceneHint;
        this.authTypes = authTypes == null ? java.util.List.of() : java.util.List.copyOf(authTypes);
        this.isAllowMall = isAllowMall;
    }

    public java.util.List<String> authTypes() { return authTypes; }
    public Boolean isAllowMall() { return isAllowMall; }

    public AwemeId awemeId() {
        return awemeId;
    }

    public String name() {
        return name;
    }

    public String avatar() {
        return avatar;
    }

    public AwemeType type() {
        return type;
    }

    public boolean primary() {
        return primary;
    }

    public String sceneHint() {
        return sceneHint;
    }

    public boolean canCreateVideoPromGoods() {
        return true;
    }
}
