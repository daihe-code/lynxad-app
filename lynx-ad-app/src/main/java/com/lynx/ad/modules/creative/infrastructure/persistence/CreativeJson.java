package com.lynx.ad.modules.creative.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.CoverImageId;
import com.lynx.ad.common.domain.Ids.QcProductId;
import com.lynx.ad.common.domain.Ids.VideoId;
import com.lynx.ad.modules.creative.domain.model.AdTitle;
import com.lynx.ad.modules.creative.domain.model.CreativeArtifact;
import com.lynx.ad.modules.creative.domain.model.PipelineStep;
import com.lynx.ad.modules.creative.domain.model.StepName;
import com.lynx.ad.modules.creative.domain.model.StepStatus;
import com.lynx.ad.modules.creative.domain.model.TitleType;
import com.lynx.ad.modules.creative.domain.model.MaterialFeedback;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

final class CreativeJson {

    private final ObjectMapper mapper;

    CreativeJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String feedback(MaterialFeedback value) { return write(value); }
    MaterialFeedback readFeedback(String value) {
        return value == null || value.isBlank() ? MaterialFeedback.empty() : read(value, new TypeReference<MaterialFeedback>() {});
    }

    String productIds(List<QcProductId> ids) {
        return write(ids.stream().map(QcProductId::value).toList());
    }

    List<QcProductId> readProductIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return read(json, new TypeReference<List<String>>() {}).stream().map(QcProductId::new).toList();
    }

    String steps(List<PipelineStep> steps) {
        return write(steps.stream()
                .map(s -> new StepWire(s.name().name(), s.status().name(), s.productId().map(QcProductId::value).orElse(null)))
                .toList());
    }

    List<PipelineStep> readSteps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return read(json, new TypeReference<List<StepWire>>() {}).stream()
                .map(s -> new PipelineStep(
                        StepName.valueOf(s.name()),
                        StepStatus.valueOf(s.status()),
                        Optional.ofNullable(s.productId()).map(QcProductId::new)))
                .toList();
    }

    String artifacts(List<CreativeArtifact> items) {
        return write(items.stream()
                .map(i -> new ArtifactWire(
                        i.productId().value(),
                        i.titles().stream().map(t -> new TitleWire(t.text(), t.type().name())).toList(),
                        i.videoId().map(VideoId::value).orElse(null),
                        i.coverImageId().map(CoverImageId::value).orElse(null),
                        i.videoObjectKey(),
                        i.coverObjectKey(),
                        i.storageMode(),
                        i.filename(),
                        i.sizeBytes(),
                        i.durationSec(),
                        i.width(),
                        i.height(),
                        i.imageMode(),
                        i.createdAt() == null ? null : i.createdAt().toString(),
                        i.mockContent()))
                .toList());
    }

    List<CreativeArtifact> readArtifacts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return read(json, new TypeReference<List<ArtifactWire>>() {}).stream()
                .map(i -> new CreativeArtifact(
                        new QcProductId(i.productId()),
                        i.titles() == null
                                ? List.of()
                                : i.titles().stream()
                                        .map(t -> new AdTitle(t.text(), TitleType.valueOf(t.type())))
                                        .toList(),
                        Optional.ofNullable(i.videoId()).map(VideoId::new),
                        Optional.ofNullable(i.coverImageId()).map(CoverImageId::new),
                        i.videoObjectKey(),
                        i.coverObjectKey(),
                        i.storageMode(),
                        i.filename(),
                        i.sizeBytes(),
                        i.durationSec(),
                        i.width(),
                        i.height(),
                        i.imageMode(),
                        parseInstant(i.createdAt()),
                        Boolean.TRUE.equals(i.mockContent())))
                .toList();
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("创意 JSON 序列化失败", ex);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("创意 JSON 反序列化失败", ex);
        }
    }

    record StepWire(String name, String status, String productId) {}

    record TitleWire(String text, String type) {}

    record ArtifactWire(
            String productId,
            List<TitleWire> titles,
            String videoId,
            String coverImageId,
            String videoObjectKey,
            String coverObjectKey,
            String storageMode,
            String filename,
            Long sizeBytes,
            Integer durationSec,
            Integer width,
            Integer height,
            String imageMode,
            String createdAt,
            Boolean mockContent) {}

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
