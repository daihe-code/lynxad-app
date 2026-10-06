<template>
  <ReferenceModal class="generation-confirmation" title="素材智能生成确认" :width="1104" :busy="busy" :show-close="false" :aria-describedby="introId" @close="emit('close')">
    <p :id="introId" class="gpick__intro">系统将对选中商品智能生成优质投放素材，无需智能生成素材的商品可取消选中：</p>
    <div class="gpick" role="group" aria-label="选择需要智能生成素材的商品">
      <button v-for="product in products" :key="product.qcProductId" type="button" class="gcard" role="checkbox" :aria-checked="picked.includes(product.qcProductId)" :disabled="busy" @click="toggle(product.qcProductId)">
        <span class="gcard__thumb" aria-hidden="true"><img v-if="product.imageUrl" :src="product.imageUrl" alt="" @error="($event.target as HTMLImageElement).hidden = true" /><ReferenceIcon v-else name="image" /></span>
        <span class="gcard__name" :title="product.name">{{ product.name }}</span>
        <span class="gcard__tick" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5" /></svg></span>
      </button>
    </div>
    <template #footer><div class="gpick__foot"><span class="gpick__sum" aria-live="polite">已选 <b>{{ picked.length }}</b>/{{ products.length }}</span><button type="button" class="gpick__ok" :disabled="busy || !picked.length" @click="emit('confirm', [...picked])">{{ busy ? '处理中…' : '确定' }}</button></div></template>
  </ReferenceModal>
</template>
<script setup lang="ts">
import { ref, useId } from 'vue';
import type { ProductVo } from '@/features/catalog/catalogApi';
import ReferenceModal from '../reference/ReferenceModal.vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
const props = defineProps<{ products: ProductVo[]; selection: string[]; busy: boolean }>();
const emit = defineEmits<{ close: []; confirm: [ids: string[]]; selection: [ids: string[]] }>();
const introId = useId();
const picked = ref(props.selection.filter(id => props.products.some(p => p.qcProductId === id)));
function toggle(id: string) {
  if (props.busy) return;
  picked.value = picked.value.includes(id) ? picked.value.filter(value => value !== id) : [...picked.value, id];
  emit('selection', [...picked.value]);
}
</script>
<style scoped>
.generation-confirmation :deep(.reference-modal__mask) { background: rgba(26,27,46,.45); backdrop-filter: none; }
.generation-confirmation :deep(.reference-modal__box) { padding: 28px 42px 26px; border-radius: 20px; box-shadow: 0 12px 32px -12px rgba(26,27,46,.24),0 2px 8px rgba(26,27,46,.06); font-family: inherit; }
.generation-confirmation :deep(header h2) { font-size: 16px; font-weight: 700; line-height: 1; }
.generation-confirmation :deep(.reference-modal__body) { margin: 0; }
.gpick__intro { margin:14px 0 0; font-size:13px; line-height:1.65; color:rgb(var(--c-text-2)); }
.gpick { margin-top:20px; display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:30px 62px; }
.gcard { position:relative; display:flex; align-items:center; gap:10px; min-height:66px; padding:10px 32px 10px 10px; border:1px solid rgb(var(--c-border)); border-radius:12px; background:#fff; text-align:left; cursor:pointer; transition:border-color .16s ease,background .16s ease,box-shadow .16s ease; }
.gcard:hover { border-color:rgb(var(--c-primary)/.5); box-shadow:0 6px 16px -10px rgb(var(--c-primary)/.9); }
.gcard[aria-checked="true"] { border-color:rgb(var(--c-primary)); background:rgb(var(--c-primary-soft-3)); }
.gcard:focus-visible { outline:2px solid rgb(var(--c-primary)); outline-offset:2px; }
.gcard__thumb { display:grid; height:38px; width:38px; flex-shrink:0; place-items:center; border-radius:9px; background:rgb(var(--c-ad-thumb)); overflow:hidden; }
.gcard__thumb img { width:100%; height:100%; object-fit:cover; }.gcard__thumb svg { width:22px; height:22px; color:rgb(var(--c-text-3)); }
.gcard__name { display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; font-size:12px; line-height:1.25; color:rgb(var(--c-text-1)); }
.gcard__tick { position:absolute; right:11px; top:50%; transform:translateY(-50%); display:grid; height:16px; width:16px; place-items:center; border-radius:9999px; border:1.5px solid rgb(var(--c-ad-check)); color:transparent; }
.gcard__tick svg { height:10px; width:10px; }.gcard[aria-checked="true"] .gcard__tick { border-color:rgb(var(--c-primary)); background:rgb(var(--c-primary)); color:#fff; }
.gpick__foot { width:100%; margin-top:22px; display:flex; align-items:center; justify-content:flex-end; gap:16px; }.gpick__sum { font-size:13px; color:rgb(var(--c-text-2)); }.gpick__sum b { font-size:15px; font-weight:700; color:rgb(var(--c-text-1)); }
.gpick__ok { height:38px; min-width:92px; border:0; border-radius:10px; background:rgb(var(--c-primary)); font-size:14px; font-weight:600; color:#fff; cursor:pointer; }.gpick__ok:hover { background:rgb(var(--c-primary-hover)); }.gpick__ok:disabled { background:rgb(var(--c-ad-check)); cursor:not-allowed; }
@media(max-width:1279px) { .gpick { gap:24px 34px; } }
@media(max-width:1023px) { .gpick { grid-template-columns:repeat(3,minmax(0,1fr)); gap:20px 28px; } }
@media(max-width:639px) { .generation-confirmation :deep(.reference-modal__box) { padding:20px 16px 16px; }.gpick { grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; }.gcard { min-height:60px; padding:8px 30px 8px 8px; }.gcard__thumb { height:32px; width:32px; } }
</style>
