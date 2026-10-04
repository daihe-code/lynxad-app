package com.lynx.ad.modules.creative.domain.service;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.creative.domain.model.CreativeMode;
import com.lynx.ad.modules.creative.domain.model.StepName;

public class CreativePipelinePolicy {

    public void rejectReserved(CreativeMode mode) {
        if (mode == CreativeMode.RESERVED) {
            throw new DomainException(1501, "本轮仅支持标准短视频链路");
        }
    }

    public StepName next(StepName current) {
        if (current == null) {
            return StepName.PARSE;
        }
        return switch (current) {
            case PARSE -> StepName.TITLE;
            case TITLE -> StepName.COPY;
            case COPY -> StepName.TTS;
            case TTS -> StepName.STORYBOARD;
            case STORYBOARD -> StepName.VIDEO;
            case VIDEO -> StepName.COVER;
            case COVER -> StepName.UPLOAD;
            case UPLOAD -> StepName.DONE;
            case DONE -> throw new DomainException(1502, "链路已结束");
        };
    }
}
