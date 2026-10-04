package com.lynx.ad.common.domain;

import com.lynx.ad.common.exception.DomainException;

public final class Ids {
    private Ids() {}

    public record TenantId(String value) {
        public TenantId {
            require(value, "租户标识为空");
        }
    }

    public record UserId(String value) {
        public UserId {
            require(value, "用户标识为空");
        }
    }

    public record AccountId(String value) {
        public AccountId {
            require(value, "账户标识为空");
        }
    }

    public record AdvertiserId(String value) {
        public AdvertiserId {
            require(value, "广告主标识为空");
        }
    }

    public record AwemeId(String value) {
        public AwemeId {
            require(value, "抖音号标识为空");
        }
    }

    public record ProductId(String value) {
        public ProductId {
            require(value, "商品标识为空");
        }
    }

    public record QcProductId(String value) {
        public QcProductId {
            require(value, "千川商品标识为空");
        }
    }

    public record PlanId(String value) {
        public PlanId {
            require(value, "计划标识为空");
        }
    }

    public record QcPlanId(String value) {
        public QcPlanId {
            require(value, "千川计划标识为空");
        }
    }

    public record TaskId(String value) {
        public TaskId {
            require(value, "任务标识为空");
        }
    }

    public record PackageId(String value) {
        public PackageId {
            require(value, "素材包标识为空");
        }
    }

    public record VideoId(String value) {
        public VideoId {
            require(value, "视频标识为空");
        }
    }

    public record CoverImageId(String value) {
        public CoverImageId {
            require(value, "封面标识为空");
        }
    }

    public record NotificationId(String value) {
        public NotificationId {
            require(value, "通知标识为空");
        }
    }

    public record ActionId(String value) {
        public ActionId {
            require(value, "动作标识为空");
        }
    }

    public record IdempotencyKey(String value) {
        public IdempotencyKey {
            require(value, "幂等键为空");
        }

        public static IdempotencyKey ofPublish(PlanId id, int publishVersion) {
            return new IdempotencyKey(id.value() + ":" + publishVersion);
        }
    }

    private static void require(String value, String msg) {
        if (value == null || value.isBlank()) {
            throw new DomainException(1001, msg);
        }
    }
}
