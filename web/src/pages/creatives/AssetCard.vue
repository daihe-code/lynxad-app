<template>
  <article class="ac-card" :class="{ 'is-picked': picked }">
    <div class="ac-box">
      <div class="ac-media" :class="`ac-t${tone}`"><AssetThumbnail :item="item" fit="contain" @metadata="observed = $event" /></div>
      <span v-if="geometry.label" class="ac-format" :title="`${geometry.label} · ${assetDimensions(displayItem)}`">{{ geometry.label }}</span>
      <span v-if="item.reviewBadge !== 'NONE'" class="ac-st" :class="item.reviewBadge === 'REVIEWING' ? 'ac-st--reviewing' : 'ac-st--rejected'"><AssetIcon :name="item.reviewBadge === 'REVIEWING' ? 'clock' : 'warn-fill'" />{{ item.aiReview && !['APPROVED','NOT_REQUIRED'].includes(item.aiReview.status) ? assetStatus(item).label : item.reviewBadge === 'REVIEWING' ? '千川审核中' : '千川审核未通过' }}</span>
      <span v-if="item.type === 'VIDEO' && item.durationSec != null" class="ac-dur"><AssetIcon name="video" />{{ duration(item.durationSec) }}</span>
      <label class="ac-pick"><input class="checkbox" type="checkbox" :aria-label="`选择素材 ${item.filename}`" :checked="picked" :disabled="busy" @change="emit('pick')" /></label>
      <span class="ac-src" :class="{ 'ac-src--plat': item.source === 'EXTERNAL' }">{{ sourceLabel(item) }}</span>
      <button class="asset-preview-target" type="button" :aria-label="`预览素材 ${item.filename}`" @click="emit('preview')"></button>
      <div class="ac-hover">
        <div class="ac-hops"><button class="ac-hbtn" type="button" :aria-label="`放大预览 ${item.filename}`" @click="emit('preview')"><AssetIcon name="expand" />预览</button><button class="ac-hbtn" type="button" :aria-label="`查看 ${item.filename} 的详情`" @click="emit('detail')"><AssetIcon name="info-line" />详情</button></div>
        <div class="ac-hinfo"><div class="ac-hrow"><span>规格</span><b>{{ assetDimensions(displayItem) }}</b></div><div class="ac-hrow"><span>大小</span><b>{{ fileSize(item.sizeBytes) }}</b></div><div class="ac-hrow"><span>{{ item.type === 'VIDEO' ? '时长' : '比例' }}</span><b>{{ item.type === 'VIDEO' ? duration(item.durationSec) : assetRatio(displayItem) }}</b></div><div class="ac-hrow"><span>引用计划</span><b>{{ referenceLabel(item) }}</b></div></div>
      </div>
    </div>
    <p class="ac-card__n" :title="item.filename">{{ item.filename }}</p>
    <p class="ac-card__id" :title="assetIdentifier(item)">素材 ID：{{ assetIdentifier(item) }}</p>
    <p class="ac-card__m"><span>{{ fileSize(item.sizeBytes) }}</span><i class="ac-card__dot">·</i><template v-if="item.type === 'VIDEO' && item.durationSec != null"><span>{{ duration(item.durationSec) }}</span><i class="ac-card__dot">·</i></template><span>{{ item.category || '—' }}</span><i class="ac-card__dot">·</i><span :title="`${item.source === 'EXTERNAL' ? '千川上传时间' : '上传时间'}：${assetDateTime(item.createdAt)}`">{{ assetDay(item.createdAt) }}</span></p>
    <p class="ac-card__m ac-card__m--st"><template v-if="cardStatus?.label"><span class="ac-use" :class="cardStatus.cls"><i aria-hidden="true"></i>{{ cardStatus.label }}</span><i class="ac-card__dot">·</i></template><button v-if="item.used" class="ac-card__ref" type="button" @click="emit('detail')">{{ referenceLabel(item) }}</button><span v-else class="ac-card__ref ac-card__ref--off">{{ referenceLabel(item) }}</span></p>
  </article>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { LibraryItemVo } from "@/features/creative/creativeApi";
import { referenceLabel } from '@/features/creative/assetReferences';
import { assetGeometry } from '@/features/creative/assetGeometry';
import AssetIcon from "./AssetIcon.vue";
import AssetThumbnail from "./AssetThumbnail.vue";
import { duration, fileSize, assetDay, assetDateTime, sourceLabel, assetStatus, assetIdentifier, assetRatio, assetDimensions } from "./assetFormat";
const props = defineProps<{ item: LibraryItemVo; tone: number; picked: boolean; busy: boolean }>();
const cardStatus = computed(() => props.item.type === 'CAROUSEL' ? undefined : assetStatus(props.item));
const observed = ref<{width:number;height:number}>();
const geometry = computed(() => assetGeometry(props.item, observed.value));
const displayItem = computed(() => geometry.value.width ? {...props.item, width:geometry.value.width, height:geometry.value.height} : props.item);
watch(() => props.item.id, () => { observed.value = undefined; });
const emit = defineEmits<{ pick: []; preview: []; detail: [] }>();
</script>
<style scoped>
.asset-preview-target { position: absolute; inset: 0; z-index: 1; width: 100%; height: 100%; }
.ac-box { flex-shrink: 0; aspect-ratio: 16 / 9; }
.ac-card__id { margin-top:6px; color:rgb(var(--c-text-3)); font-size:10px; line-height:14px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; font-variant-numeric:tabular-nums; }
.ac-format { position:absolute; left:9px; bottom:9px; z-index:2; padding:4px 7px; border-radius:5px; background:rgba(26,27,46,.72); color:#fff; font-size:11px; line-height:1.2; pointer-events:none; }
.ac-card:focus-within .ac-hover { opacity: 1; }
.ac-card:focus-within :is(.ac-src, .ac-st, .ac-dur) { opacity: 0; }
</style>
