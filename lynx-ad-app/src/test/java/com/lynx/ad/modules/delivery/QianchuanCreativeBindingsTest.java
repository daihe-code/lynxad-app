package com.lynx.ad.modules.delivery;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanCreativeBindings;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class QianchuanCreativeBindingsTest {
    final ObjectMapper json = new ObjectMapper();

    ObjectNode detail(String field, JsonNode material) {
        var detail = json.createObjectNode();
        var group = detail.putArray("multi_product_creative_list").addObject()
                .put("product_id", new BigInteger("3841018224732930459"));
        group.putArray(field).add(material);
        return detail;
    }

    @ParameterizedTest @ValueSource(strings = {"0", "\"0\"", "\"\""})
    void uploadedVideoIgnoresUnusedAwemeIdAndKeepsItsActualBinding(String unused) throws Exception {
        var video = json.createObjectNode().put("video_id", "v28033gi0000dbhjhvnog65gegkuv2sg")
                .put("video_cover_id", "cover-1").put("image_mode", "VIDEO_VERTICAL");
        video.set("aweme_item_id", json.readTree(unused));
        var input = detail("video_material", video);
        var snapshot = input.deepCopy();
        var groups = QianchuanCreativeBindings.groups(input);
        var result = groups.get(0).path("video_material").get(0);
        assertEquals(video.path("video_id"), result.path("video_id"));
        assertEquals(video.path("video_cover_id"), result.path("video_cover_id"));
        assertFalse(result.has("aweme_item_id"));
        assertEquals(snapshot, input); // The authoritative response remains untouched.
        assertEquals(groups, QianchuanCreativeBindings.groups(
                json.createObjectNode().set("multi_product_creative_list", groups)));
    }

    @Test void unusedCarouselIdKeepsTheAlternativeWithoutLosingPrecision() {
        var id = new BigInteger("7702061887688769555");
        var carousel = json.createObjectNode().put("carousel_id", 0).put("aweme_carousel_id", id);
        var result = QianchuanCreativeBindings.groups(detail("carousel_material", carousel))
                .get(0).path("carousel_material").get(0);
        assertFalse(result.has("carousel_id"));
        assertEquals(id, result.path("aweme_carousel_id").bigIntegerValue());
        assertTrue(result.path("aweme_carousel_id").isIntegralNumber());
    }

    @Test void uploadedCarouselKeepsItsIdWhenAwemeIdIsZero() {
        var id = new BigInteger("7702061887688769555");
        var carousel = json.createObjectNode().put("carousel_id", id).put("aweme_carousel_id", 0);
        var result = QianchuanCreativeBindings.groups(detail("carousel_material", carousel))
                .get(0).path("carousel_material").get(0);
        assertEquals(id, result.path("carousel_id").bigIntegerValue());
        assertFalse(result.has("aweme_carousel_id"));
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "not-an-id"})
    void genuineMalformedIdsRemainRejected(String value) {
        var video = json.createObjectNode().put("video_id", "valid-video").put("aweme_item_id", value);
        assertThrows(BizException.class, () -> QianchuanCreativeBindings.groups(detail("video_material", video)));
    }

    @Test void zeroProductIdRemainsRejected() {
        var input = detail("video_material", json.createObjectNode().put("video_id", "valid-video"));
        ((ObjectNode) input.path("multi_product_creative_list").get(0)).put("product_id", 0);
        assertThrows(BizException.class, () -> QianchuanCreativeBindings.groups(input));
    }

    @Test void nativeVideoKeepsItsPositiveAwemeIdWithoutAnUploadedVideoId() {
        var id = new BigInteger("7702061887688769555");
        var result = QianchuanCreativeBindings.groups(detail("video_material",
                json.createObjectNode().put("aweme_item_id", id)))
                .get(0).path("video_material").get(0);
        assertEquals(id, result.path("aweme_item_id").bigIntegerValue());
    }

    @Test void carouselWithoutEitherBindingCannotBeSilentlyDropped() {
        assertThrows(BizException.class, () -> QianchuanCreativeBindings.groups(detail(
                "carousel_material", json.createObjectNode().put("carousel_id", 0).put("aweme_carousel_id", 0))));
    }

    @Test void videoWithoutEitherBindingCannotBeSilentlyDropped() {
        assertThrows(BizException.class, () -> QianchuanCreativeBindings.groups(
                detail("video_material", json.createObjectNode().put("aweme_item_id", 0))));
    }
}
