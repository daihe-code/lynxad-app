package com.lynx.ad.modules.identity.domain.model;

import com.lynx.ad.common.domain.Ids.AwemeId;

public class Aweme {

    private final AwemeId awemeId;
    private final String name;
    private final String avatar;
    private final AwemeType type;
    private final boolean primary;
    private final String sceneHint;

    public Aweme(AwemeId awemeId, String name, String avatar, AwemeType type, boolean primary, String sceneHint) {
        this.awemeId = awemeId;
        this.name = name;
        this.avatar = avatar;
        this.type = type;
        this.primary = primary;
        this.sceneHint = sceneHint;
    }

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
