<template>
  <div ref="root" class="picksel" @keydown.esc.prevent.stop="close">
    <button ref="trigger" class="pick" type="button" role="combobox" aria-label="素材对应商品" aria-haspopup="listbox" :aria-expanded="open" :aria-controls="menuId" @click="open = !open" @keydown.down.prevent="focusOption(0)" @keydown.up.prevent="focusOption(products.length - 1)">
      <span class="pick__l"><span class="pick__name">{{ current?.name || '请选择商品' }}</span><span v-if="aiProductIds.includes(value)" class="pick__ai"><ReferenceIcon name="tipmark" />AI 智能生成</span></span><ReferenceIcon name="caret" />
    </button>
    <div v-if="open" :id="menuId" class="pmenu" role="listbox" aria-label="素材对应商品">
      <button v-for="(product, index) in products" :key="product.qcProductId" type="button" class="pmenu__it" role="option" :aria-selected="product.qcProductId === value" @click="choose(product.qcProductId)" @keydown.down.prevent="focusOption((index + 1) % products.length)" @keydown.up.prevent="focusOption((index + products.length - 1) % products.length)" @keydown.home.prevent="focusOption(0)" @keydown.end.prevent="focusOption(products.length - 1)">
        <span class="pick__name">{{ product.name }}</span><span v-if="aiProductIds.includes(product.qcProductId)" class="pick__ai"><ReferenceIcon name="tipmark" />AI 智能生成</span>
      </button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useId } from 'vue';
import type { ProductVo } from '@/features/catalog/catalogApi';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
const props = defineProps<{ products: ProductVo[]; aiProductIds: string[] }>();
const value = defineModel<string>({ required: true });
const open = ref(false), root = ref<HTMLElement>(), trigger = ref<HTMLButtonElement>(), menuId = useId();
const current = computed(() => props.products.find(p => p.qcProductId === value.value));
function close() { if (open.value) { open.value = false; trigger.value?.focus(); } }
function choose(id: string) { value.value = id; close(); }
async function focusOption(index: number) { open.value = true; await nextTick(); root.value?.querySelectorAll<HTMLButtonElement>('[role="option"]')[index]?.focus(); }
function outside(event: Event) { if (!root.value?.contains(event.target as Node)) open.value = false; }
onMounted(() => document.addEventListener('pointerdown', outside));
onUnmounted(() => document.removeEventListener('pointerdown', outside));
</script>
<style scoped>
.picksel { position:relative; width:100%; max-width:300px; }.pick { width:100%; display:flex; align-items:center; justify-content:space-between; gap:8px; }
.pick__l { display:flex; flex:1 1 auto; min-width:0; align-items:center; gap:8px; }.pick__name { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.pick__ai { display:inline-flex; flex-shrink:0; align-items:center; gap:3px; border-radius:5px; background:rgb(var(--c-primary-soft)); padding:2px 6px; font-size:11px; font-weight:500; line-height:1.4; color:rgb(var(--c-primary-ink)); }.pick__ai svg { height:11px; width:11px; }
.pmenu { position:absolute; left:0; right:0; top:calc(100% + 6px); z-index:30; max-height:288px; overflow-y:auto; border:1px solid rgb(var(--c-border)); border-radius:12px; background:#fff; box-shadow:0 12px 32px -12px rgba(26,27,46,.24),0 2px 8px rgba(26,27,46,.06); }
.pmenu__it { display:flex; width:100%; align-items:center; justify-content:space-between; gap:10px; border:0; background:transparent; padding:10px 12px; text-align:left; font-size:13px; color:rgb(var(--c-text-1)); cursor:pointer; }.pmenu__it:hover { background:rgb(var(--c-primary-soft-3)); }.pmenu__it[aria-selected="true"] { color:rgb(var(--c-primary-ink)); font-weight:600; }.pmenu__it + .pmenu__it { border-top:1px solid rgb(var(--c-ad-hair)); }
</style>
