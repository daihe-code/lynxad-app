package com.lynx.ad.modules.delivery.domain.model;

import com.lynx.ad.common.exception.DomainException;
import java.util.Objects;

public final class PlanName {
    private final String text;
    public PlanName(String text) { this(text,30); }
    private PlanName(String text,int max) {
        if(text==null || text.isBlank() || text.trim().length()>max) throw new DomainException(1401,"计划名称须 1–"+max+" 字");
        this.text=text.trim();
    }
    public static PlanName fromPlatform(String text) { return new PlanName(text,128); }
    public String text() { return text; }
    @Override public boolean equals(Object other) { return other instanceof PlanName name && text.equals(name.text); }
    @Override public int hashCode() { return Objects.hash(text); }
    @Override public String toString() { return text; }
}
