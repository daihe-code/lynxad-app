<template>
  <div class="live-materials creative-workspace ad-step3">
    <CreationFrame v-if="!embedded" :step="3" navigable :navigation-disabled="busy || running" :max-step="maxStep ?? 3" @navigate="step => emit('navigate',step)" :account="account" :account-chips="accountChips" />
    <main class="ad-main"><div class="container-page"><section class="panel"><div class="panel__pad">
      <h2 class="panel__title">{{ batchRunning ? '生成投放素材' : '确定投放素材' }}</h2><p class="panel__sub">{{ batchRunning ? `我正在为你选中的 ${state.generationProductIds.length || state.products.length} 款商品准备素材，生成后由你预览确认` : '我已整理好素材，请预览并确认用于本次投放的内容' }}</p>
      <MaterialGenerationProgress v-if="batchRunning" ref="generationProgress" :state="state" :reconnecting="reconnecting" :busy="busy || running" @retry="type => emit('retryGeneration', type)" />
      <template v-else>
      <div v-if="showStatusNotice" class="tip" role="status"><ReferenceIcon class="tip__icon" name="tipmark" /><p class="tip__text">{{ statusMessage }}<span v-if="typographyPendingCount"> 有 {{ typographyPendingCount }} 条素材的文字待完善，现有画面已保留。</span></p></div>
      <p v-if="running" class="material-processing-message" role="status">正在处理本次单条素材操作，已有素材仍可预览；任务会在后台继续。</p>
      <section v-if="state.materials.length" class="processing-summary" aria-label="素材处理阶段" aria-live="polite"><p><b>生成阶段：</b>{{ generationResultLabel(state) }}</p><p><b>后续处理：</b>{{ processingSummary(state.materials) }}</p></section>
      <details v-if="additionHistory.length" class="addition-history"><summary>新增生成记录（{{ additionHistory.length }}）</summary><ul><li v-for="record in additionHistory" :key="record.id"><div><strong>{{ tabs[record.type as 'VIDEO' | 'IMAGE' | 'CAROUSEL'] }}</strong><span>{{ additionStatus[record.status] || record.status }}</span><button v-if="!readOnly && ['SUCCEEDED', 'FAILED'].includes(record.status)" type="button" :disabled="busy || running" @click="openAddition(record.type as CreationAdditionBody['type'])">再次生成</button></div><p>{{ record.description ? `历史生成描述：${record.description}` : '依据商品分析自动生成' }}</p><p v-if="record.error" class="error">{{ record.error }}</p></li></ul></details>
      <div class="srow"><p class="srow__label">素材可见性<el-tooltip effect="light" placement="top" :trigger="['hover', 'focus']" :show-after="150" popper-class="material-visibility-tooltip"><template #content><p>选择“抖音主页单次可见”后，用户可通过内容推荐看到相关视频与创意；仅当用户在信息流中刷到该视频时，当次进入主页可看到该视频，其他情况主页不展示该视频。</p><p class="material-visibility-tooltip__note">注意：计划下自动生成的视频创意、采纳使用的 AI 修复视频、AIGC 动态创意视频将默认设为单次可见，不跟随设置变化；原抖音主页视频的公开状态不受影响。</p></template><button class="visibility-help" type="button" aria-label="素材可见性说明"><ReferenceIcon name="help-fill" /></button></el-tooltip></p><div class="srow__body">
        <div class="seg seg--lg" role="group" aria-label="素材可见性" :aria-busy="busy">
          <button type="button" class="seg__item" :aria-pressed="state.hideInAweme" :disabled="visibilityLocked" @click="selectVisibility(true)">抖音主页单次可见<span class="seg__badge">推荐</span></button>
          <button type="button" class="seg__item" :aria-pressed="!state.hideInAweme" :disabled="visibilityLocked" @click="selectVisibility(false)">抖音主页始终可见</button>
        </div>
        <p class="srow__hint">适用于官方或自运营抖音号的非主页原生素材，实际展示以抖音平台规则为准</p>
      </div></div>
      <div class="srow"><p class="srow__label"><label for="material-product">素材对应商品</label></p><div class="srow__body"><div class="product-picker"><el-select id="material-product" v-model="productId" class="material-product-select" :disabled="busy" popper-class="material-product-options" aria-label="素材对应商品"><template #label="{ label }"><span class="product-option-label">{{ label }}<span v-if="productHasFailure(state, productId)" class="material-failure-badge" role="img" aria-label="该商品有素材失败" title="该商品有素材失败">!</span></span></template><el-option v-for="p in state.products" :key="p.qcProductId" :value="p.qcProductId" :label="p.name"><span class="product-option-label" :title="p.name">{{ p.name }}<span v-if="productHasFailure(state, p.qcProductId)" class="material-failure-badge" role="img" aria-label="该商品有素材失败" title="该商品有素材失败">!</span></span></el-option></el-select><span v-if="materials.some(m => m.engineMaterialId)" class="srctag srctag--ai product-source"><ReferenceIcon name="sparkle" />智能生成</span><ReferenceIcon class="product-caret" name="caret" /></div><p class="srow__hint">下拉可切换查询各商品对应素材情况<button v-if="otherFailedProducts.length" class="other-product-failures" type="button" @click="showOtherFailure"><span class="material-failure-badge" aria-hidden="true">!</span>另有 {{ otherFailedProducts.length }} 款商品有素材失败，点击查看</button></p><p v-for="(issue, index) in productIssues" :key="index" class="product-generation-failure" role="alert">本商品生成任务未完成：{{ issue.message }}</p></div></div>
      <p v-if="missing.length" class="error">以下商品尚无可投放素材：{{ missing.map(p => p.name).join('、') }}。{{ readOnly ? '' : '请补齐素材后再发布。' }}</p>
      <div class="mblock">
        <div class="mblock__head"><p class="mblock__label">素材和标题</p><p class="mblock__count">已添加：素材 <b>{{ materials.length }}</b>/100，标题 <b>{{ titleCount }}</b>/30</p></div>
        <p v-if="!readOnly && regenerationWaitSeconds" class="material-processing-message" role="status">本计划重新生成操作间隔为30秒，{{ regenerationWaitSeconds }} 秒后可再次操作。</p>
        <p class="mblock__hint">{{ readOnly ? '点击素材或标题可查看投放效果预览' : '每条素材均可独立编辑，不满意可直接删除或重新生成，页面右侧可预览投放效果' }}</p>
        <div class="tabs" role="tablist" aria-label="素材类型"><button v-for="(label, key) in tabs" :key="key" class="tabs__item" type="button" role="tab" :aria-selected="tab === key" @click="tab = key">{{ label }}<span class="tabs__count">{{ key === 'TITLE' ? titleCount : materials.filter(m => m.type === key).length }}</span><span v-if="productHasFailure(state, productId, key)" class="material-failure-badge" role="img" :aria-label="`${label}有失败项`" :title="`${label}有失败项，点击查看原因`">!</span></button></div>
        <div class="work"><div class="work__main">
          <MaterialTitleList v-if="tab === 'TITLE'" :materials="materials" :pool="state.titlePools?.[productId]" :busy="busy || running" :read-only="readOnly" @save="(kind, values) => emit('productTitles', productId, kind, values)" />
          <template v-else>
            <div class="matlist" role="list">
              <div v-for="m in visible" :key="m.id" class="matrow" :class="{ 'matrow--active': active?.id === m.id }">
                <button class="material-select" type="button" :aria-pressed="active?.id === m.id" @click="selectedId = m.id">
                  <span class="matrow__thumb"><img v-if="thumbnail(m) && !brokenThumbnails[m.id]" :src="thumbnail(m)" alt="" @load="imageMetadata(m.id, $event)" @error="brokenThumbnails[m.id] = true" /><ReferenceIcon v-else :name="m.type === 'VIDEO' ? 'video' : m.type === 'IMAGE' ? 'image' : 'article'" /></span>
                  <span class="matrow__body"><span class="matrow__title" :title="m.displayName || m.title">{{ m.displayName || m.title }}</span><span class="matrow__meta">{{ metadataLabel(m) }}</span><span class="matrow__tag"><span class="srctag" :class="m.engineMaterialId ? 'srctag--ai' : 'srctag--manual'"><ReferenceIcon v-if="m.engineMaterialId" name="sparkle" />{{ materialSourceLabel(m) }}</span></span><span v-if="m.generationDescription" class="material-generation-description" :title="m.generationDescription">生成要求：{{ m.generationDescription }}</span><span v-if="!materialIsReady(m)" class="material-status" :class="{ 'material-status--error': m.status === 'FAILED' || ['FAILED','REJECTED','UNCERTAIN'].includes(m.processing?.syncStatus || '') }" :title="m.error">{{ materialProcessingLabel(m) }}</span><span v-if="m.processing?.message && !materialFailed(m) && !activeGenerationIssues(state).some(issue => issue.materialId === m.id)" class="material-processing-message">{{ m.processing.message }}</span></span>
                </button>
                <p v-if="materialFailed(m) || activeGenerationIssues(state).some(issue => issue.materialId === m.id)" class="material-failure-reason" role="alert"><span class="material-failure-badge" aria-hidden="true">!</span><span>失败原因：{{ materialFailureReason(m, state) }}</span></p>
                <div class="material-actions">
                <span v-if="!readOnly" class="matrow__ops">

                  <button v-if="m.processing?.canRetrySync" class="sync-retry" type="button" :disabled="busy || running" @click="emit('retrySync', m.id)">{{ m.processing.syncStatus === 'PENDING' ? '开始同步千川' : '重新同步失败项' }}</button>
                  <template v-if="['UNCERTAIN','UNKNOWN'].includes(m.processing?.syncStatus || '')"><button class="sync-retry" type="button" :disabled="busy" @click="emit('verifySync', m.id)">核查上传结果</button><router-link class="sync-retry" to="/creatives">查看上传记录</router-link></template>
                  <button v-if="materialIsReady(m) && !materialHasPreview(m)" class="sync-retry" type="button" :disabled="busy" @click="emit('refreshStatus')">刷新预览</button>
                  <router-link v-if="['REJECTED','FAILED'].includes(m.processing?.reviewStatus || '')" class="sync-retry" to="/creatives">查看质检结果</router-link>
                  <router-link v-if="m.processing?.syncStatus === 'WAITING_AUTH'" class="sync-retry" to="/accounts">恢复千川授权</router-link>
                  <button v-if="m.status === 'FAILED' && !m.processing?.canRetrySync && !['UNCERTAIN','WAITING_AUTH'].includes(m.processing?.syncStatus || '')" class="iconbtn" type="button" :disabled="busy || running" title="重新检查素材" aria-label="重新检查素材" @click="emit('recheck', m.id)"><ReferenceIcon name="check-circle" /></button>
                  <button class="iconbtn" type="button" :disabled="busy || running || !m.engineMaterialId || regenerationWaitSeconds > 0" aria-label="重新生成素材" :title="regenerationWaitSeconds ? `${regenerationWaitSeconds} 秒后可再次重新生成` : '重新生成素材'" @click="emit('action', 'regenerate', m.id)"><ReferenceIcon name="refresh" /></button>
                  <button class="iconbtn" type="button" :disabled="busy || running" aria-label="删除素材" title="删除" @click="emit('action', 'delete', m.id)"><ReferenceIcon name="trash" /></button>
                </span>
                </div>
                <details v-if="m.processing?.components.length" class="material-pipeline"><summary>组件处理进度与原因</summary><ul><li v-for="component in m.processing.components" :key="component.component"><b>{{ componentLabel(component.component) }}</b><span>登记：{{ processingLabel(component.storageStatus) }}</span><span>质检：{{ processingLabel(component.reviewStatus) }}</span><span>千川同步：{{ processingLabel(component.syncStatus) }}</span><p v-if="component.message">{{ component.message }}</p></li></ul></details>
              </div>
            </div>
            <p v-if="!visible.length && !failureRows.length" class="empty">当前商品暂无{{ tabs[tab] }}素材{{ readOnly ? '。' : '，可智能生成或从素材库选择。' }}</p>
          </template>
          <div v-if="failureRows.length" class="material-failure-list"><article v-for="(issue, index) in failureRows" :key="issue.slotId || issue.materialId || index" class="material-generation-failure" :aria-label="`${tabs[tab]}生成失败`"><div class="material-failure-heading"><span class="material-failure-badge" aria-hidden="true">!</span><strong>{{ issue.title || `${tabs[tab]}生成失败` }}</strong><span>生成失败</span></div><p class="material-failure-reason" role="alert">失败原因：{{ issue.message }}</p><button v-if="!readOnly && canRetryFailureType" class="sync-retry" type="button" :disabled="busy || running" title="重试本次任务中该类型的全部失败项，已有素材保留" @click="retryFailureType">重试同类型失败项</button></article></div>
          <button v-if="!readOnly && tab !== 'TITLE'" class="addrow" :disabled="busy || running" @click="openAddition()"><ReferenceIcon name="plus" />新增素材（智能生成或上传）</button>
        </div><aside class="work__side" aria-label="投放效果预览"><MaterialPreview :material="active" :product="previewProduct" :account="account"  @metadata="(id, data) => mediaMetadata[id] = data" /></aside></div>
      </div>
      </template>
    </div></section><section v-if="!embedded && !batchRunning" class="panel plan"><div class="plan__pad"><div class="plan__row"><p class="plan__label"><label for="material-plan-name">投放计划名称</label></p><div class="plan__body"><input id="material-plan-name" v-model="planName" class="plan__input" maxlength="30" :disabled="readOnly || busy || running" /><p class="plan__hint">自动生成，用于后续跟踪查看，可修改。发布前会核对当前商品、标题与素材版本。</p></div></div></div></section></div></main>
    <div v-if="!embedded && !readOnly" class="actionbar"><div class="container-page actionbar__inner"><button v-if="!batchRunning" class="btn-prev" :disabled="busy || running" @click="emit('prev')">上一步</button><button v-if="!batchRunning" class="btn-next-primary" :disabled="busy || running || !ready" @click="emit('publish')">发布投放</button><button class="btn-cancel" :disabled="busy" @click="emit('exit')">保存并退出</button></div></div>
    <ReferenceModal v-if="!readOnly && generationOpen" class="generation-dialog" title="选择需要生成素材的商品" :width="1104" :busy="busy || generationSubmitting" @close="generationOpen = false">
      <p class="generation-intro">我会为勾选的商品生成投放素材。已有素材、无需生成的商品，可取消勾选。</p>
      <div class="generation-products" role="group" aria-label="选择需要智能生成素材的商品">
        <button v-for="p in state.products" :key="p.qcProductId" class="generation-product" type="button" role="checkbox" :aria-checked="generationIds.includes(p.qcProductId)" :disabled="busy || generationSubmitting" @click="toggleGeneration(p.qcProductId)">
          <span class="generation-product__thumb"><img v-if="p.imageUrl && !brokenGenerationImages[p.qcProductId]" :src="p.imageUrl" alt="" @error="brokenGenerationImages[p.qcProductId] = true" /><span v-else aria-hidden="true">▧</span></span>
          <span class="generation-product__name" :title="p.name">{{ p.name }}</span>
          <span class="generation-product__tick" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m5 12 4 4L19 6" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" /></svg></span>
        </button>
      </div>
      <p class="generation-help">未勾选的商品仍保留在投放计划中，需从素材库补充素材。<br />所有商品配齐素材后，才能发布投放。</p>
      <template #footer><span class="generation-count" aria-live="polite">已选 <b>{{ generationIds.length }}</b> 款商品</span><button class="btn-brand" type="button" :disabled="busy || generationSubmitting || running" @click="submitGeneration">{{ busy || generationSubmitting ? '正在提交…' : '为所选商品生成素材' }}</button></template>
    </ReferenceModal>
    <AddMaterialDialog v-if="!readOnly && addOpen" :state="state" :product-id="productId" :initial-type="additionPreset.type" :busy="busy" @close="addOpen = false" @generate="body => { tab = body.type; emit('generateAddition', body); }" @add="body => { tab = body.type as 'VIDEO' | 'IMAGE' | 'CAROUSEL'; emit('add', body); }" />
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import CreationFrame from '../reference/CreationFrame.vue';
import ReferenceModal from '../reference/ReferenceModal.vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import MaterialGenerationProgress from './MaterialGenerationProgress.vue';
import MaterialPreview from './MaterialPreview.vue';
import MaterialTitleList from './ProductTitleList.vue';
import AddMaterialDialog from './AddMaterialDialog.vue';
import '../reference/creative-workspace.css';
import type { CreationState, CreationMaterial, CreationAdditionBody } from '@/features/delivery/creationApi';
import { regenerationWait } from '@/features/delivery/regenerationPolicy';
import { materialIsReady, materialHasPreview, materialProcessingLabel, generationResultLabel, processingSummary, componentLabel, processingLabel } from '@/features/delivery/materialProcessing';
import { activeGenerationIssues, generationFailureRows, materialFailed, materialFailureReason, productHasFailure } from '@/features/delivery/materialFailures';
const props = defineProps<{ state: CreationState; busy: boolean; account?: { awemeName: string; awemeId: string; avatar?: string }; accountChips: string[]; readOnly?: boolean; embedded?: boolean; reconnecting?: boolean; maxStep?: number }>();
const planName = defineModel<string>('planName', { required: true });
const emit = defineEmits<{ navigate:[step:number]; exit: []; retryGeneration: [type: 'VIDEO'|'IMAGE'|'CAROUSEL']; productTitles: [productId:string,kind:'CUSTOM'|'CARD',titles:string[]]; visibility: [hideInAweme: boolean]; context: [id: string]; generate: [ids: string[]]; generateAddition: [body: CreationAdditionBody]; action: [action: 'regenerate' | 'delete', id: string]; titles: [id: string, titles: string[]]; recheck: [id: string]; retrySync: [id: string]; verifySync: [id: string]; refreshStatus: []; typography: [id: string]; prev: []; confirm: []; publish: []; cancel: []; add: [body: { productId: string; type: string; videoAssetId?: string; coverAssetId?: string; imageAssetIds?: string[]; carouselId?: string; titles: string[] }] }>();
const now = ref(Date.now()), timer = setInterval(() => { now.value = Date.now(); }, 1000);
onUnmounted(() => clearInterval(timer));
watch(() => props.state.regenerationRetryAt, () => { now.value = Date.now(); }, { flush: 'sync' });
const regenerationWaitSeconds = computed(() => regenerationWait(props.state.regenerationRetryAt, now.value));
const tabs = { VIDEO: '视频', IMAGE: '图片', CAROUSEL: '图文', TITLE: '标题' };
const productId = ref(props.state.products[0]?.qcProductId || ''), tab = ref<keyof typeof tabs>('VIDEO'), selectedId = ref('');
const materials = computed(() => props.state.materials.filter(m => m.productId === productId.value));
const visible = computed(() => materials.value.filter(m => m.type === tab.value));
const failureRows = computed(() => generationFailureRows(props.state, productId.value, tab.value));
const productIssues = computed(() => activeGenerationIssues(props.state).filter(i => i.productId === productId.value && !i.type));
const otherFailedProducts = computed(() => props.state.products.filter(p => p.qcProductId !== productId.value && productHasFailure(props.state, p.qcProductId)));
const canRetryFailureType = computed(() => tab.value !== 'TITLE' && props.state.generationCounts?.some(c => c.type === tab.value && (c.failed || 0) > 0));
function retryFailureType() { if (!props.readOnly && !props.busy && !running.value && tab.value !== 'TITLE' && canRetryFailureType.value) emit('retryGeneration', tab.value); }
function showOtherFailure() {
  const product = otherFailedProducts.value[0]; if (!product) return;
  productId.value = product.qcProductId;
  tab.value = (Object.keys(tabs) as (keyof typeof tabs)[]).find(type => productHasFailure(props.state, product.qcProductId, type)) || 'VIDEO';
}
const active = computed(() => tab.value === 'TITLE' ? materials.value.find(m => m.id === selectedId.value) || materials.value[0] : visible.value.find(m => m.id === selectedId.value) || visible.value[0]);
const previewProduct = computed(() => props.state.products.find(p => p.qcProductId === (active.value?.productId || productId.value)));
const previewableCount = computed(() => props.state.materials.filter(materialHasPreview).length);
const typographyPendingCount = computed(() => props.state.materials.filter(m => m.typographyStatus === 'pending').length);
const titleCount = computed(() => { const pool=props.state.titlePools?.[productId.value]; return pool ? new Set([...pool.customTitles,...pool.cardTitles]).size : new Set(materials.value.flatMap(m=>m.titles)).size; });
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
const missing = computed(() => props.state.products.filter(p => !props.state.materials.some(m => m.productId === p.qcProductId && materialIsReady(m))));
const ready = computed(() => !!props.state.materials.length && !missing.value.length && props.state.materials.every(materialIsReady));
const running = computed(() => generationSubmitting.value || ['QUEUED','SUBMITTING','RUNNING','IMPORTING','REVISING','UNCERTAIN'].includes(props.state.generationStatus));
const batchRunning = computed(() => running.value && props.state.generationStatus !== 'REVISING' && !props.state.generationAdditions?.some(r=>['QUEUED','SUBMITTING','RUNNING','IMPORTING','UNCERTAIN'].includes(r.status)) && !(props.state.generationCounts?.length === 1 && props.state.materials.length));
const showStatusNotice=computed(()=>props.reconnecting || typographyPendingCount.value>0 || props.state.publishStatus === 'FAILED');
const statusMessage = computed(() => props.state.publishStatus === 'FAILED' ? props.state.publishError || '发布未完成，请核查投放结果。' : props.reconnecting ? '暂时无法更新进度，正在重新连接。已有结果已保留。' : running.value ? props.state.generationStatus === 'IMPORTING' ? '素材已生成，正在登记、检查并同步千川。' : '素材准备中，任务会在后台继续，完成后可返回草稿查看。' : previewableCount.value ? '素材已准备，请预览并确认本次投放内容。后续处理问题会显示在对应素材下。' : props.state.materials.length ? '素材记录已保留，预览暂不可用，请核查处理状态。' : '请选择需要生成素材的商品，或从素材库补齐素材。');
const visibilityLocked = computed(() => props.readOnly || props.busy || running.value || !['NONE','FAILED'].includes(props.state.publishStatus));
function selectVisibility(hideInAweme: boolean) {
  if (!visibilityLocked.value && props.state.hideInAweme !== hideInAweme) emit('visibility', hideInAweme);
}
const generationOpen = ref(!props.readOnly && props.state.generationStatus === 'NONE'), generationIds = ref(props.state.products.map(p => p.qcProductId));
const generationSubmitting = ref(false), brokenGenerationImages = ref<Record<string, boolean>>({});
const generationProgress = ref<InstanceType<typeof MaterialGenerationProgress>>();
watch(batchRunning, async generating => {
  if (!generating) return;
  await nextTick();
  if (batchRunning.value) generationProgress.value?.$el.scrollIntoView({ block: 'start', behavior: 'smooth' });
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
  if (props.readOnly || props.busy || generationSubmitting.value || running.value) return;
  generationOpen.value = false;
  if (!generationIds.value.length) return;
  generationSubmitting.value = true;
  emit('generate', [...generationIds.value]);
}
watch(() => props.busy, busy => { if (!busy) generationSubmitting.value = false; });
watch(() => props.state.generationStatus, status => {
  if (['QUEUED','SUBMITTING','RUNNING','IMPORTING','REVISING','UNCERTAIN'].includes(status)) {
    generationOpen.value = false;
    generationSubmitting.value = false;
  }
});
const addOpen = ref(false);
const additionPreset = ref<{ type: CreationAdditionBody['type'] }>({ type: 'VIDEO' });
const additionHistory = computed(() => (props.state.generationAdditions || []).filter(r => r.productId === productId.value).slice(-10).reverse());
const additionStatus: Record<string, string> = { QUEUED: '等待生成', SUBMITTING: '提交中', RUNNING: '生成中', IMPORTING: '正在同步千川', UNCERTAIN: '提交状态待确认', SUCCEEDED: '已完成', FAILED: '失败' };
function openAddition(type: CreationAdditionBody['type'] = tab.value === 'TITLE' ? 'VIDEO' : tab.value) { additionPreset.value = { type }; addOpen.value = true; }
const materialSourceLabel = (m: CreationMaterial) => m.deliverySource === 'product_reference' ? '商品原图版' : m.engineMaterialId ? '智能生成' : '人工上传';
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
watch(productId, () => { selectedId.value = ''; const first = materials.value[0]; if (first && tab.value !== 'TITLE' && !visible.value.length && !failureRows.value.length) tab.value = first.type; });
</script>
<style scoped>
.material-failure-list{display:grid;gap:12px;margin-top:12px}
.processing-summary{margin:14px 0;padding:12px 16px;border:1px solid #e4e3f9;background:#f8f8ff;border-radius:10px;color:#606582;font-size:13px;line-height:1.7}.processing-summary p{margin:0}.material-processing-message{display:block;color:#8588a5;font-size:12px;line-height:1.5;overflow-wrap:anywhere;margin-top:4px}.sync-retry{font-size:12px;color:#575cf6;padding:7px 9px;border:1px solid #deddfc;border-radius:7px;white-space:nowrap}.sync-retry:disabled{opacity:.5;cursor:wait}.matrow{flex-wrap:wrap}.material-pipeline{flex:1 1 100%;min-width:0;margin-top:8px;font-size:12px;color:#737793}.material-pipeline summary{cursor:pointer}.material-pipeline ul{list-style:none;margin:8px 0 0;padding:0}.material-pipeline li{display:flex;flex-wrap:wrap;gap:5px 14px;margin-top:7px}.material-pipeline li p{flex:1 1 100%;margin:0;color:#9b692c;overflow-wrap:anywhere}

.typography-repair{border:1px solid #deddfc;border-radius:8px;padding:7px 10px;color:#5b54ff;background:#f8f7ff;white-space:nowrap;font-size:12px}.typography-repair:disabled{opacity:.5;cursor:wait}
.material-actions{display:flex;align-items:center;gap:4px;flex-wrap:wrap}.matrow__ops{flex-wrap:wrap;justify-content:flex-end}@media(max-width:600px){.matrow{flex-wrap:wrap}.material-select{flex:1 1 100%}.material-actions{width:100%;justify-content:flex-end}}
.material-generation-description{display:block;font-size:11px;color:#878aa5;max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.addition-history{border:1px solid #e7e7f5;border-radius:10px;padding:12px 16px;margin:16px 0;color:#656a89;font-size:12px}.addition-history summary{cursor:pointer}.addition-history ul{list-style:none;padding:0;margin:12px 0 0}.addition-history li+li{border-top:1px solid #ececf5;padding-top:12px;margin-top:12px}.addition-history li>div{display:flex;align-items:center;gap:12px}.addition-history button{margin-left:auto;color:#575cf6}.addition-history p{margin:8px 0;overflow-wrap:anywhere}
.material-failure-badge{display:inline-grid;place-items:center;flex-shrink:0;width:15px;height:15px;margin-left:5px;border-radius:50%;background:#e6535c;color:white;font-size:11px;font-weight:700;line-height:1;vertical-align:middle}.product-option-label{display:inline-flex;align-items:center;gap:4px;max-width:100%}.other-product-failures{display:block;margin-top:6px;color:#d83e4a;font:inherit;text-align:left}.material-failure-reason{display:flex;align-items:flex-start;gap:6px;flex:1 1 100%;min-width:0;margin-top:8px;color:#cf3847;font-size:12px;line-height:1.7;overflow-wrap:anywhere}.material-failure-reason .material-failure-badge{margin:3px 0 0}.material-generation-failure{padding:14px;border:1px solid #f0c8cd;border-radius:10px;background:#fffafa}.material-failure-heading{display:flex;align-items:center;gap:6px;font-size:13px;line-height:1.7;overflow-wrap:anywhere}.material-failure-heading>span:last-child{flex-shrink:0;margin-left:auto;color:#cf3847;font-size:12px}.material-generation-failure .sync-retry{margin-top:10px}.product-generation-failure{margin-top:8px;color:#cf3847;font-size:12px;line-height:1.7;overflow-wrap:anywhere}.material-failure-heading strong{min-width:0}.material-failure-badge{white-space:nowrap}
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
.generation-count { white-space: nowrap; }
@media (max-width:1023px) { .generation-products { grid-template-columns: repeat(3,minmax(0,1fr)); gap: 20px; } }
@media (max-width:639px) { .generation-dialog :deep(.reference-modal__box) { padding: 20px 16px 16px; } .generation-products { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px; } }
/* Shared reference CSS supplies proportions, typography and colors. */
.live-materials { background: #f7f7ff; min-height: 100vh; color: #1a1b2e; padding-bottom: 75px; }
.panel__pad { padding-bottom: 40px; }
.tip__icon { width: 16px; height: 16px; }
.live-materials .visibility-help { display: inline-flex; flex-shrink: 0; align-items: center; justify-content: center; width: 18px; height: 18px; margin-top: -4px; padding: 0; border: 0; background: transparent; color: #a0a3bd; transition: color .15s; }
.live-materials .visibility-help svg { width: 15px; height: 15px; color: inherit; }
.live-materials .visibility-help:hover, .live-materials .visibility-help:focus-visible { color: #575cf6; }
.visibility-help:focus-visible { outline: 2px solid #575cf6; outline-offset: 2px; border-radius: 50%; }
:global(.el-popper.material-visibility-tooltip) { box-sizing: border-box; width: 340px; max-width: calc(100vw - 24px); padding: 12px 14px; border: 1px solid #e0e2ef; border-radius: 8px; background: #fff; color: #525570; font-size: 12px; line-height: 1.75; box-shadow: 0 8px 24px #1a1b2e1a; }
:global(.material-visibility-tooltip p) { margin: 0; }
:global(.material-visibility-tooltip .material-visibility-tooltip__note) { margin-top: 8px; color: #8588a5; }
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

<style scoped>
.material-product-select{width:100%;min-width:0}.product-picker{width:100%;max-width:none}.material-product-select :deep(.el-select__wrapper){min-height:40px}.material-product-select :deep(.el-select__selected-item){white-space:normal;line-height:1.6}.material-product-select+.product-source{margin-left:8px}.material-product-select~.product-caret{display:none}:global(.material-product-options .el-select-dropdown__item.is-selected){color:#575cf6;background:#f1efff}:global(.material-product-options .el-select-dropdown__item){height:auto;min-height:36px;line-height:1.6;white-space:normal;padding:9px 16px}
</style>
