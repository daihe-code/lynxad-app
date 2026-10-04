package com.lynx.ad.common.domain;

import com.lynx.ad.common.exception.DomainException;
import java.util.List;
import java.util.Objects;

/** 企业规模四档或空。连字符为 U+2013。非法值 1604。 */
public final class CompanyScale {

    public static final String SIZE_1_10 = "1–10 人";
    public static final String SIZE_11_50 = "11–50 人";
    public static final String SIZE_51_200 = "51–200 人";
    public static final String SIZE_200_PLUS = "200 人以上";

    public static final List<String> ALLOWED = List.of(SIZE_1_10, SIZE_11_50, SIZE_51_200, SIZE_200_PLUS);

    private final String value;

    private CompanyScale(String value) {
        this.value = value;
    }

    public static CompanyScale empty() {
        return new CompanyScale(null);
    }

    public static CompanyScale parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        String trimmed = raw.trim();
        if (!ALLOWED.contains(trimmed)) {
            throw new DomainException(1604, "企业规模不合法");
        }
        return new CompanyScale(trimmed);
    }

    public boolean isEmpty() {
        return value == null || value.isBlank();
    }

    /** 持久化值；空则为 null。 */
    public String valueOrNull() {
        return isEmpty() ? null : value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompanyScale that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
