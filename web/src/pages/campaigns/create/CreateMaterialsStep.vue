<template>
  <div class="live-materials creative-workspace ad-step3">
    <CreationFrame v-if="!embedded" :step="3" :account="account" :account-chips="accountChips" />
    <main class="ad-main"><div class="container-page"><section class="panel"><div class="panel__pad">
      <h2 class="panel__title">确定投放素材</h2><p class="panel__sub">系统根据您选中的商品智能生成优质投放素材，保障投放效果</p>
      <div class="tip"><ReferenceIcon class="tip__icon" name="tipmark" /><p class="tip__text">{{ running ? state.generationMessage || '素材生成处理中…' : previewableCount ? '素材已生成，点击任意素材可预览投放效果。' : readOnly ? '当前计划暂无素材预览信息。' : '请选择需要智能生成的商品，或从素材库添加素材。' }}</p></div>
      <MaterialGenerationProgress v-if="running" ref="generationProgress" :state="state" />
      <div v-if="state.generationStatus === 'FAILED'" class="generation-warning" role="status"><b>部分素材未完成</b><p>{{ state.generationMessage }}</p><p v-for="issue in state.generationIssues" :key="issue.productId">{{ issue.productName }}：{{ issue.message }}</p><p v-if="previewableCount">已生成 {{ previewableCount }} 条素材，可在下方查看和预览。</p></div>
      <template v-if="!running || state.materials.length">
      <div class="srow"><p class="srow__label">素材可见性</p><div class="srow__body">
        <div class="seg seg--lg" role="group" aria-label="素材可见性" :aria-busy="busy">
          <button type="button" class="seg__item" :aria-pressed="state.hideInAweme" :disabled="visibilityLocked" @click="selectVisibility(true)">抖音主页单次可见<span class="seg__badge">推荐</span></button>
          <button type="button" class="seg__item" :aria-pressed="!state.hideInAweme" :disabled="visibilityLocked" @click="selectVisibility(false)">抖音主页始终可见</button>
        </div>
        <p class="srow__hint">适用于官方或自运营抖音号的非主页原生素材，实际展示以抖音平台规则为准</p>
      </div></div>
      <div class="srow"><p class="srow__label"><label for="material-product">素材对应商品</label></p><div class="srow__body"><div class="product-picker"><select id="material-product" v-model="productId" class="pick" :disabled="busy"><option v-for="p in state.products" :key="p.qcProductId" :value="p.qcProductId">{{ p.name }}</option></select><span v-if="materials.some(m => m.engineMaterialId)" class="srctag srctag--ai product-source"><ReferenceIcon name="sparkle" />AI 智能生成</span><ReferenceIcon class="product-caret" name="caret" /></div><p class="srow__hint">下拉可切换查询各商品对应素材情况</p></div></div>
      <p v-if="missing.length" class="error">以下商品尚无可投放素材：{{ missing.map(p => p.name).join('、') }}。{{ readOnly ? '' : '请补齐素材后再发布。' }}</p>
      <div class="mblock">
        <div class="mblock__head"><p class="mblock__label">素材和标题</p><p class="mblock__count">已添加：素材 <b>{{ materials.length }}</b>/100，标题 <b>{{ titleCount }}</b>/30</p></div>
        <p class="mblock__hint">{{ readOnly ? '点击素材或标题可查看投放效果预览' : '每条素材均可独立编辑，不满意可直接删除或重新生成，页面右侧可预览投放效果' }}</p>
        <div class="tabs" role="tablist" aria-label="素材类型"><button v-for="(label, key) in tabs" :key="key" class="tabs__item" type="button" role="tab" :aria-selected="tab === key" @click="tab = key">{{ label }}<span class="tabs__count">{{ key === 'TITLE' ? titleCount : materials.filter(m => m.type === key).length }}</span></button></div>
        <div class="work"><div class="work__main">
          <MaterialTitleList v-if="tab === 'TITLE'" :materials="materials" :busy="busy || running" :read-only="readOnly" @select="selectTitle" @save="(id, values) => emit('titles', id, values)" />
          <template v-else>
            <div class="matlist" role="list">
              <div v-for="m in visible" :key="m.id" class="matrow" :class="{ 'matrow--active': active?.id === m.id }">
                <button class="material-select" type="button" :aria-pressed="active?.id === m.id" @click="selectedId = m.id">
                  <span class="matrow__thumb"><img v-if="thumbnail(m) && !brokenThumbnails[m.id]" :src="thumbnail(m)" alt="" @load="imageMetadata(m.id, $event)" @error="brokenThumbnails[m.id] = true" /><ReferenceIcon v-else :name="m.type === 'VIDEO' ? 'video' : m.type === 'IMAGE' ? 'image' : 'article'" /></span>
                  <span class="matrow__body"><span class="matrow__title" :title="m.title">{{ m.title }}</span><span class="matrow__meta">{{ metadataLabel(m) }}</span><span class="matrow__tag"><span class="srctag" :class="m.engineMaterialId ? 'srctag--ai' : 'srctag--manual'"><ReferenceIcon v-if="m.engineMaterialId" name="sparkle" />{{ m.engineMaterialId ? 'AI 智能生成' : '人工上传' }}</span></span><span class="material-status" :class="{ 'material-status--error': m.status === 'FAILED' }" :title="m.error">{{ statusLabel(m) }}</span></span>
                </button>
                <span v-if="!readOnly" class="matrow__ops">
                  <button v-if="m.status === 'FAILED'" class="iconbtn" type="button" :disabled="busy || running" title="重新检查素材" aria-label="重新检查素材" @click="emit('recheck', m.id)"><ReferenceIcon name="check-circle" /></button>
                  <button class="iconbtn" type="button" :disabled="busy || running || !m.engineMaterialId" aria-label="重新生成素材" title="重新生成素材" @click="emit('action', 'regenerate', m.id)"><ReferenceIcon name="sparkle" /></button>
                  <button class="iconbtn" type="button" :disabled="busy || running" aria-label="删除素材" title="删除" @click="emit('action', 'delete', m.id)"><ReferenceIcon name="trash" /></button>
                </span>
              </div>
            </div>
            <p v-if="!visible.length" class="empty">当前商品暂无{{ tabs[tab] }}素材{{ readOnly ? '。' : '，可智能生成或从素材库选择。' }}</p>
            <button v-if="!readOnly" class="addrow" :disabled="busy || running" @click="addOpen = true"><ReferenceIcon name="plus" />新增素材（素材库或智能生成）</button>
          </template>
        </div><aside class="work__side" aria-label="投放效果预览"><MaterialPreview :material="active" :product="previewProduct" :account="account" :title="tab === 'TITLE' ? previewTitle : undefined" @metadata="(id, data) => mediaMetadata[id] = data" /></aside></div>
      </div>
      </template>
    </div></section><section v-if="!embedded && (!running || state.materials.length)" class="panel plan"><div class="plan__pad"><div class="plan__row"><p class="plan__label"><label for="material-plan-name">投放计划名称</label></p><div class="plan__body"><input id="material-plan-name" v-model="planName" class="plan__input" maxlength="30" :disabled="readOnly || busy || running" /><p class="plan__hint">自动生成，用于后续跟踪查看，可修改。素材、标题或名称修改后需重新确认素材包。</p></div></div></div></section></div></main>
    <div v-if="!embedded && !readOnly" class="actionbar"><div class="container-page actionbar__inner"><button class="btn-prev" :disabled="busy || running" @click="emit('prev')">上一步</button><button class="btn-ghost-brand" :disabled="busy || running || !ready" @click="emit('confirm')">{{ confirmed ? '当前素材包已确认' : '确认素材包' }}</button><button class="btn-next-primary" :disabled="busy || running || !ready || !confirmed" @click="emit('publish')">发布投放</button><button class="btn-cancel" :disabled="busy" @click="emit('cancel')">取消投放</button></div></div>
    <ReferenceModal v-if="!readOnly && generationOpen" class="generation-dialog" title="素材智能生成确认" :width="1104" :busy="busy || generationSubmitting" @close="generationOpen = false">
      <p class="generation-intro">系统将对选中商品智能生成优质投放素材，无需智能生成素材的商品可取消选中：</p>
      <div class="generation-products" role="group" aria-label="选择需要智能生成素材的商品">
        <button v-for="p in state.products" :key="p.qcProductId" class="generation-product" type="button" role="checkbox" :aria-checked="generationIds.includes(p.qcProductId)" :disabled="busy || generationSubmitting" @click="toggleGeneration(p.qcProductId)">
          <span class="generation-product__thumb"><img v-if="p.imageUrl && !brokenGenerationImages[p.qcProductId]" :src="p.imageUrl" alt="" @error="brokenGenerationImages[p.qcProductId] = true" /><span v-else aria-hidden="true">▧</span></span>
          <span class="generation-product__name" :title="p.name">{{ p.name }}</span>
          <span class="generation-product__tick" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m5 12 4 4L19 6" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" /></svg></span>
        </button>
      </div>
      <p class="generation-help">未选中的商品可从素材库补齐素材；所有商品素材齐全后才能发布投放。</p>
      <template #footer><span class="generation-count" aria-live="polite">已选 <b>{{ generationIds.length }}</b>/{{ state.products.length }}</span><button class="btn-brand" type="button" :disabled="busy || generationSubmitting || running || !generationIds.length" @click="submitGeneration">{{ busy || generationSubmitting ? '正在提交…' : '确定' }}</button></template>
    </ReferenceModal>
    <ReferenceModal v-if="!readOnly && addOpen" title="新增素材" :width="460" @close="addOpen = false"><div class="add-options"><button class="btn-brand" :disabled="busy || running" @click="addOpen = false; openLibrary()"><ReferenceIcon name="file" />从素材库选择</button><button class="btn-ghost-brand" :disabled="busy || running" @click="addOpen = false; openGeneration()"><ReferenceIcon name="sparkle" />智能生成素材</button></div></ReferenceModal>
    <el-dialog v-if="!readOnly" v-model="libraryOpen" title="从素材库选择" width="780px" append-to-body>
      <p>选择已保存的{{ tabs[libraryType] }}素材；上传素材须通过审核，引擎生成素材直接同步当前千川账户。</p>
      <div v-if="libraryLoading">正在加载素材库…</div><p v-else-if="libraryError" class="error">{{ libraryError }}</p>
      <div class="library-grid"><label v-for="item in library" :key="item.id"><input v-model="libraryId" type="radio" :value="item.assetId || item.carouselId || item.id" /><img v-if="item.posterUrl" :src="item.posterUrl" alt="" /><span>{{ item.filename }}</span></label></div>
      <p v-if="!libraryLoading && !library.length">暂无可选择的本地素材，请先前往素材中心上传。</p>
      <label v-if="libraryType === 'VIDEO'">视频封面<select v-model="coverId"><option value="">请选择已上传图片</option><option v-for="item in covers" :key="item.id" :value="item.assetId">{{ item.filename }}</option></select></label>
      <p>素材标题（每行一条，最多 29 条，每条 55 字）</p><textarea v-model="titles" rows="3" maxlength="560" />
      <template #footer><button class="btn-prev" @click="libraryOpen = false">取消</button><button class="btn-brand" :disabled="busy || !libraryId || (libraryType === 'VIDEO' && !coverId) || !titles.trim()" @click="addSelected">加入计划</button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import CreationFrame from '../reference/CreationFrame.vue';
import ReferenceModal from '../reference/ReferenceModal.vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import MaterialGenerationProgress from './MaterialGenerationProgress.vue';
import MaterialPreview from './MaterialPreview.vue';
import MaterialTitleList from './MaterialTitleList.vue';
import '../reference/creative-workspace.css';
import { listLibraryFull, type LibraryItemVo } from '@/features/creative/creativeApi';
import type { CreationState, CreationMaterial } from '@/features/delivery/creationApi';
const props = defineProps<{ state: CreationState; busy: boolean; account?: { awemeName: string; awemeId: string; avatar?: string }; accountChips: string[]; readOnly?: boolean; embedded?: boolean }>();
const planName = defineModel<string>('planName', { required: true });
const emit = defineEmits<{ visibility: [hideInAweme: boolean]; generate: [ids: string[]]; action: [action: 'regenerate' | 'delete', id: string]; titles: [id: string, titles: string[]]; recheck: [id: string]; prev: []; confirm: []; publish: []; cancel: []; add: [body: { productId: string; type: string; videoAssetId?: string; coverAssetId?: string; imageAssetIds?: string[]; carouselId?: string; titles: string[] }] }>();
const tabs = { VIDEO: '视频', IMAGE: '图片', CAROUSEL: '图文', TITLE: '标题' };
const productId = ref(props.state.products[0]?.qcProductId || ''), tab = ref<keyof typeof tabs>('VIDEO'), selectedId = ref('');
const materials = computed(() => props.state.materials.filter(m => m.productId === productId.value));
const visible = computed(() => materials.value.filter(m => m.type === tab.value));
const active = computed(() => tab.value === 'TITLE' ? materials.value.find(m => m.id === selectedId.value) || materials.value[0] : visible.value.find(m => m.id === selectedId.value) || visible.value[0]);
const previewProduct = computed(() => props.state.products.find(p => p.qcProductId === (active.value?.productId || productId.value)));
const previewableCount = computed(() => props.state.materials.filter(m => m.previewUrl || m.posterUrl || m.imageUrls?.length).length);
const titleCount = computed(() => materials.value.reduce((count,m) => count + m.titles.length, 0));
const previewTitle = ref('');
function selectTitle(id: string, text: string) { selectedId.value = id; previewTitle.value = text; }
const mediaMetadata = ref<Record<string,{ width: number; height: number; duration?: number }>>({});
const brokenThumbnails = ref<Record<string,boolean>>({});
const thumbnail = (m: CreationMaterial) => m.posterUrl || m.imageUrls?.[0];
function imageMetadata(id: string, event: Event) {
  const image = event.target as HTMLImageElement;
  if (props.state.materials.find(m => m.id === id)?.type === 'IMAGE') mediaMetadata.value[id] = { width: image.naturalWidth, height: image.naturalHeight };
}
function metadataLabel(m: CreationMaterial) {
  const data = mediaMetadata.value[m.id];
  if (m.type === 'CAROUSEL') return `${m.imageUrls?.length || 0} 张图片 · 图文配音`;
  if (m.type === 'IMAGE') return data ? `${data.width} × ${data.height} · 商品卡图片` : '商品卡图片';
  const orientation = data ? data.height > data.width ? '竖屏' : data.height < data.width ? '横屏' : '方屏' : '视频';
  return data?.duration ? `${Math.round(data.duration)}s · ${orientation}` : orientation;
}
const missing = computed(() => props.state.products.filter(p => !props.state.materials.some(m => m.productId === p.qcProductId && m.status === 'READY')));
const ready = computed(() => !!props.state.materials.length && !missing.value.length && props.state.materials.every(m => m.status === 'READY'));
const confirmed = computed(() => props.state.confirmedVersion === props.state.packageVersion && planName.value.trim() === props.state.name);
const running = computed(() => generationSubmitting.value || ['QUEUED','SUBMITTING','RUNNING','IMPORTING','REVISING','UNCERTAIN'].includes(props.state.generationStatus));
const visibilityLocked = computed(() => props.readOnly || props.busy || running.value || ['QUEUED','RUNNING'].includes(props.state.analysisStatus) || !['NONE','FAILED'].includes(props.state.publishStatus));
function selectVisibility(hideInAweme: boolean) {
  if (!visibilityLocked.value && props.state.hideInAweme !== hideInAweme) emit('visibility', hideInAweme);
}
const generationOpen = ref(!props.readOnly && props.state.generationStatus === 'NONE'), generationIds = ref(props.state.products.map(p => p.qcProductId));
const generationSubmitting = ref(false), brokenGenerationImages = ref<Record<string, boolean>>({});
const generationProgress = ref<InstanceType<typeof MaterialGenerationProgress>>();
watch(running, async generating => {
  if (!generating) return;
  await nextTick();
  if (running.value) generationProgress.value?.$el.scrollIntoView({ block: 'start', behavior: 'smooth' });
});
function openGeneration() {
  if (props.readOnly || props.busy || running.value) return;
  generationIds.value = props.state.products.map(p => p.qcProductId);
  generationOpen.value = true;
}
function toggleGeneration(id: string) {
  if (props.readOnly || props.busy || generationSubmitting.value) return;
  generationIds.value = generationIds.value.includes(id) ? generationIds.value.filter(value => value !== id) : [...generationIds.value, id];
}
function submitGeneration() {
  if (props.readOnly || props.busy || generationSubmitting.value || running.value || !generationIds.value.length) return;
  generationSubmitting.value = true;
  generationOpen.value = false;
  emit('generate', [...generationIds.value]);
}
watch(() => props.busy, busy => { if (!busy) generationSubmitting.value = false; });
watch(() => props.state.generationStatus, status => {
  if (['QUEUED','SUBMITTING','RUNNING','IMPORTING','REVISING','UNCERTAIN'].includes(status)) {
    generationOpen.value = false;
    generationSubmitting.value = false;
  }
});
const addOpen = ref(false), libraryOpen = ref(false), libraryLoading = ref(false), libraryError = ref(''), library = ref<LibraryItemVo[]>([]), covers = ref<LibraryItemVo[]>([]), libraryId = ref(''), coverId = ref(''), titles = ref('');
const libraryType = ref<CreationMaterial['type']>('VIDEO');
const statusLabel = (m: CreationMaterial) => {
  const generated = !!m.engineMaterialId && !!(m.previewUrl || m.posterUrl || m.imageUrls?.length);
  if (generated && m.status === 'FAILED') return '已生成 · 千川同步失败';
  if (generated && m.status === 'PROCESSING') return '已生成 · 正在同步千川';
  return ({ READY: '可用 · 已同步千川', PROCESSING: '正在准备素材或同步千川', FAILED: '处理失败' }[m.status] || m.status);
};
watch(() => props.state.products, p => { if (!p.some(x => x.qcProductId === productId.value)) productId.value = p[0]?.qcProductId || ''; });
// Show an actual result when a different product group finished first.
watch(() => props.state.materials, (next, previous) => {
  for (const material of next) {
    const old = previous?.find(m => m.id === material.id);
    if (old && (thumbnail(old)?.split('?')[0] !== thumbnail(material)?.split('?')[0] || old.previewUrl?.split('?')[0] !== material.previewUrl?.split('?')[0])) {
      delete brokenThumbnails.value[material.id];
      delete mediaMetadata.value[material.id];
    }
  }
  if (!previous?.length && next.length) {
    if (!next.some(m => m.productId === productId.value)) productId.value = next[0]!.productId;
    const first = next.find(m => m.productId === productId.value && m.type === 'VIDEO') || next.find(m => m.productId === productId.value);
    if (first) tab.value = first.type;
  }
}, { immediate: true });
watch(productId, () => { selectedId.value = ''; previewTitle.value = ''; const first = materials.value[0]; if (first && tab.value !== 'TITLE' && !visible.value.length) tab.value = first.type; });
async function openLibrary() {
  if (props.readOnly || tab.value === 'TITLE' || props.busy || running.value) return;
  libraryType.value = tab.value;
  libraryOpen.value = true; libraryLoading.value = true; libraryError.value = ''; libraryId.value = ''; coverId.value = ''; titles.value = '';
  try {
    const result = await listLibraryFull({ type: libraryType.value, page: 1, pageSize: 100 });
    library.value = result.items.filter(i => (i.assetId || i.carouselId) && i.storageStatus !== 'FAILED');
    covers.value = libraryType.value === 'VIDEO' ? (await listLibraryFull({ type: 'IMAGE', page: 1, pageSize: 100 })).items.filter(i => !!i.assetId) : [];
  } catch { libraryError.value = '素材库加载失败，请重试'; library.value = []; } finally { libraryLoading.value = false; }
}
function addSelected() {
  if (props.readOnly) return;
  emit('add', { productId: productId.value, type: libraryType.value, titles: titles.value.split('\n').map(t => t.trim()).filter(Boolean),
    ...(libraryType.value === 'VIDEO' ? { videoAssetId: libraryId.value, coverAssetId: coverId.value } : libraryType.value === 'IMAGE' ? { imageAssetIds: [libraryId.value] } : { carouselId: libraryId.value }) });
  libraryOpen.value = false;
}
</script>
<style scoped>
.generation-warning { background: #fff8ed; border: 1px solid #f3ddbc; border-radius: 12px; padding: 18px 22px; color: #9b692c; font-size: 14px; line-height: 1.7; margin-bottom: 28px; }
.generation-warning p { margin-top: 6px; }
.generation-dialog :deep(.reference-modal__box) { padding: 28px 42px 26px; }
.generation-dialog :deep(.reference-modal__body) { margin: 14px 0 22px; }
.generation-intro,.generation-help,.generation-count { font-size: 13px; line-height: 1.65; color: #525570; }
.generation-help { margin-top: 18px; color: #8588a5; }
.generation-products { margin-top: 20px; display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 30px 40px; }
.generation-product { position: relative; display: flex; align-items: center; gap: 10px; min-height: 66px; padding: 10px 32px 10px 10px; border: 1px solid #e0e2ef; border-radius: 12px; background: #fff; text-align: left; }
.generation-product:hover { border-color: #575cf680; }
.generation-product[aria-checked=true] { border-color: #575cf6; background: #f7f7ff; }
.generation-product:focus-visible { outline: 2px solid #575cf6; outline-offset: 2px; }
.generation-product__thumb { display: grid; height: 38px; width: 38px; flex-shrink: 0; place-items: center; border-radius: 9px; overflow: hidden; background: #f5f5fa; color: #8588a5; }
.generation-product__thumb img { width: 100%; height: 100%; object-fit: cover; }
.generation-product__name { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 12px; line-height: 1.4; color: #1a1b2e; }
.generation-product__tick { position: absolute; right: 11px; top: 50%; transform: translateY(-50%); display: grid; height: 16px; width: 16px; place-items: center; border-radius: 50%; border: 1.5px solid #c7c9da; color: transparent; }
.generation-product__tick svg { width: 10px; height: 10px; }
.generation-product[aria-checked=true] .generation-product__tick { border-color: #575cf6; background: #575cf6; color: white; }
.generation-count b { font-size: 15px; color: #1a1b2e; }
@media (max-width:1023px) { .generation-products { grid-template-columns: repeat(3,minmax(0,1fr)); gap: 20px; } }
@media (max-width:639px) { .generation-dialog :deep(.reference-modal__box) { padding: 20px 16px 16px; } .generation-products { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px; } }
/* Shared reference CSS supplies proportions, typography and colors. */
.live-materials { background: #f7f7ff; min-height: 100vh; color: #1a1b2e; padding-bottom: 75px; }
.panel__pad { padding-bottom: 40px; }
.tip__icon { width: 16px; height: 16px; }
.seg--lg { width: auto; }
.seg__item:disabled { opacity: .65; cursor: not-allowed; }
.product-picker { position: relative; max-width: 300px; }
.product-picker .pick { appearance: none; padding-right: 139px; text-overflow: ellipsis; }
.product-source { position: absolute; right: 31px; top: 10px; pointer-events: none; }
.product-caret { position: absolute; width: 15px; height: 15px; right: 12px; top: 12px; pointer-events: none; color: #8588a5; }
.material-select { display: flex; align-items: center; flex: 1; min-width: 0; gap: 12px; text-align: left; }
.live-materials.creative-workspace .work__side { width: 100%; }
@media (min-width:1024px) { .live-materials.creative-workspace .work__side { width: 312px; } }
.matrow__thumb { overflow: hidden; }
.matrow__thumb img { width: 100%; height: 100%; object-fit: cover; }
.matrow__thumb svg { width: 23px; height: 23px; color: #8588a5; }
.material-status { display: block; margin-top: 5px; color: #8588a5; font-size: 11px; line-height: 1.5; }
.material-status--error { color: #e6535c; }
.matrow button:focus-visible { outline: 2px solid #575cf6; outline-offset: 3px; }
.empty { padding: 32px 12px; text-align: center; color: #8588a5; font-size: 13px; }
.error { color: #e6535c; margin: 18px 0; font-size: 13px; line-height: 1.7; }
.actionbar { position: fixed; left: 0; right: 0; }
.btn-brand { background: #575cf6; border: 0; border-radius: 10px; color: #fff; padding: 12px 20px; font-size: 13px; font-weight: 600; }
.btn-ghost-brand { border: 1px solid #e0e2ef; background: white; border-radius: 10px; padding: 12px 20px; font-size: 13px; color: #525570; }
button:disabled { opacity: .45; cursor: not-allowed; }
.add-options { display: flex; flex-wrap: wrap; gap: 12px; }
.add-options button { display: inline-flex; align-items: center; gap: 6px; }
.add-options svg { width: 16px; height: 16px; }
.library-grid { display: grid; grid-template-columns: repeat(3,1fr); gap: 12px; max-height: 330px; overflow: auto; margin: 18px 0; }
.library-grid label { padding: 12px; border: 1px solid #e0e2ef; border-radius: 12px; word-break: break-all; }
.library-grid img { height: 80px; object-fit: contain; margin: 8px auto; }
.library-grid span { display: block; font-size: 12px; }
textarea { width: 100%; padding: 12px; border: 1px solid #e0e2ef; border-radius: 10px; }
@media (max-width:639px) { .actionbar__inner { gap: 8px; flex-wrap: wrap; height: auto; min-height: 75px; padding-top: 10px; padding-bottom: 10px; } .btn-next-primary,.btn-prev { padding-left: 14px; padding-right: 14px; } .btn-ghost-brand { padding: 12px; } .library-grid { grid-template-columns: repeat(2,1fr); } .matrow { flex-wrap: wrap; } .matrow__ops { margin-left: auto; } }
</style>
