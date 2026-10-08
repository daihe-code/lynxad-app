<template>
  <article class="ac-card" :class="{ 'is-picked': picked }">
    <div class="ac-box">
      <div class="ac-media" :class="`ac-t${tone}`"><AssetThumbnail :item="item" /></div>
      <span v-if="item.reviewBadge !== 'NONE'" class="ac-st" :class="item.reviewBadge === 'REVIEWING' ? 'ac-st--reviewing' : 'ac-st--rejected'"><AssetIcon :name="item.reviewBadge === 'REVIEWING' ? 'clock' : 'warn-fill'" />{{ item.aiReview && item.aiReview.status !== 'APPROVED' ? assetStatus(item).label : item.reviewBadge === 'REVIEWING' ? '千川审核中' : '千川审核未通过' }}</span>
      <span v-if="item.type === 'VIDEO' && item.durationSec != null" class="ac-dur"><AssetIcon name="video" />{{ duration(item.durationSec) }}</span>
      <label class="ac-pick"><input class="checkbox" type="checkbox" :aria-label="`选择素材 ${item.filename}`" :checked="picked" :disabled="busy" @change="emit('pick')" /></label>
      <span class="ac-src" :class="{ 'ac-src--plat': item.source === 'EXTERNAL' }">{{ sourceLabel(item) }}</span>
      <button class="asset-preview-target" type="button" :aria-label="`预览素材 ${item.filename}`" @click="emit('preview')"></button>
      <div class="ac-hover">
        <div class="ac-hops"><button v-if="item.type === 'VIDEO'" class="ac-hbtn" type="button" :aria-label="`试播 ${item.filename}`" @click="emit('preview')"><AssetIcon name="play" />试播</button><button class="ac-hbtn" type="button" :aria-label="`放大预览 ${item.filename}`" @click="emit('preview')"><AssetIcon name="expand" />预览</button><button class="ac-hbtn" type="button" :aria-label="`查看 ${item.filename} 的详情`" @click="emit('detail')"><AssetIcon name="info-line" />详情</button></div>
        <div class="ac-hinfo"><div class="ac-hrow"><span>规格</span><b>{{ assetDimensions(item) }}</b></div><div class="ac-hrow"><span>大小</span><b>{{ fileSize(item.sizeBytes) }}</b></div><div class="ac-hrow"><span>{{ item.type === 'VIDEO' ? '时长' : '比例' }}</span><b>{{ item.type === 'VIDEO' ? duration(item.durationSec) : assetRatio(item) }}</b></div><div class="ac-hrow"><span>引用计划</span><b>—</b></div></div>
      </div>
    </div>
    <p class="ac-card__n" :title="item.filename">{{ item.filename }}</p>
    <p class="ac-card__m"><span>{{ fileSize(item.sizeBytes) }}</span><i class="ac-card__dot">·</i><template v-if="item.type === 'VIDEO' && item.durationSec != null"><span>{{ duration(item.durationSec) }}</span><i class="ac-card__dot">·</i></template><span>{{ item.category || '—' }}</span><i class="ac-card__dot">·</i><span>{{ assetDay(item.createdAt) }}</span></p>
    <p class="ac-card__m ac-card__m--st"><span class="ac-use" :class="assetStatus(item).cls"><i aria-hidden="true"></i>{{ assetStatus(item).label }}</span><i class="ac-card__dot">·</i><button v-if="item.used" class="ac-card__ref" type="button" @click="emit('detail')">查看引用</button><span v-else class="ac-card__ref ac-card__ref--off">未被引用</span></p>
  </article>
</template>
<script setup lang="ts">
import type { LibraryItemVo } from "@/features/creative/creativeApi";
import AssetIcon from "./AssetIcon.vue";
import AssetThumbnail from "./AssetThumbnail.vue";
import { duration, fileSize, assetDay, sourceLabel, assetStatus, assetRatio, assetDimensions } from "./assetFormat";
defineProps<{ item: LibraryItemVo; tone: number; picked: boolean; busy: boolean }>();
const emit = defineEmits<{ pick: []; preview: []; detail: [] }>();
</script>
<style scoped>
.asset-preview-target { position: absolute; inset: 0; z-index: 1; width: 100%; height: 100%; }
.ac-card:focus-within .ac-hover { opacity: 1; }
.ac-card:focus-within :is(.ac-src, .ac-st, .ac-dur) { opacity: 0; }
</style>
