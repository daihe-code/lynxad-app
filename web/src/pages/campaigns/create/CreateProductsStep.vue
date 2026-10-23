<template>
  <div class="create-products" @keydown.esc="accountOpen = false">
    <svg aria-hidden="true" class="icon-defs"><defs>
      <symbol id="create-check" viewBox="0 0 24 24"><path d="m5 12.6 4.6 4.4L19 7.4" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round" /></symbol>
      <symbol id="create-close" viewBox="0 0 24 24"><path d="m6.4 6.4 11.2 11.2M17.6 6.4 6.4 17.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /></symbol>
      <symbol id="create-caret" viewBox="0 0 24 24"><path d="m6 9.6 6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></symbol>
    </defs></svg>
    <template v-if="!embedded"><section class="ad-banner" aria-label="投放计划概览">
      <div class="container-page ad-banner__inner">
        <div class="mt-[4px] min-w-0 banner-copy">
          <div class="ad-banner__head"><h1 class="ad-banner__title">灵投广告投放</h1><span class="ad-banner__tag"><svg class="h-[13px] w-[13px]" viewBox="0 0 24 24" aria-hidden="true"><path d="M2.6 19.4 9.4 7.6l4 6.5 2.1-3.2 5.9 8.5H2.6Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>抖音巨量千川 · 商品全域投放</span></div>
          <p class="ad-banner__sub">一次设置，广告自动出现在抖音商城、搜索、推荐等多个流量场景</p>
          <ul class="ad-banner__chips"><li v-for="text in bannerChips" :key="text" class="ad-chip"><svg class="ad-chip__ic" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.9" fill="none" stroke="currentColor" stroke-width="1.7" /><path d="m8.2 12.3 2.5 2.5 5.1-5.3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>{{ text }}</li></ul>
        </div>
        <div v-if="account" class="ad-account" @keydown.esc="accountOpen = false">
          <button class="ad-account__card w-full text-left" type="button" aria-haspopup="listbox" :disabled="saving || readOnly" :aria-expanded="accountOpen" @click="accountOpen = !accountOpen">
            <AwemeAccountSummary :account="account" :chips="accountChips" />
            <span class="ad-account__chevron" aria-hidden="true"><svg class="h-4 w-4" viewBox="0 0 24 24"><use href="#create-caret" /></svg></span>
          </button>
          <div v-if="accountOpen" class="account-menu" role="listbox" aria-label="选择抖音号"><button v-for="opt in accounts" :key="opt.key" type="button" role="option" :aria-selected="opt.key === account.key" @click="accountOpen = false; emit('account', opt.key)">{{ opt.awemeName }}<small>{{ opt.awemeId }}</small></button></div>
          <p class="ad-account__hint">下拉切换需要投放广告的抖音号</p>
        </div>
        <select v-if="account" class="account-mobile" :disabled="saving || readOnly" :value="account.key" aria-label="选择抖音号" @change="emit('account', ($event.target as HTMLSelectElement).value)"><option v-for="opt in accounts" :key="opt.key" :value="opt.key">{{ opt.awemeName }} · {{ opt.awemeId }}</option></select>
      </div>
    </section>
    <nav class="ad-stepper" aria-label="投放计划进度"><template v-for="(label, i) in steps" :key="label"><button type="button" :disabled="saving || i === 3 || i+1 > (maxStep ?? 1)" @click="emit('navigate', i+1)" :aria-label="label" class="step" :aria-current="i === 0 ? 'step' : undefined"><span class="step__dot"><svg v-if="i === 3" viewBox="0 0 24 24" aria-hidden="true"><use href="#create-check" /></svg><template v-else>{{ i + 1 }}</template></span><span class="step__label">{{ label }}</span></button><span v-if="i < 3" class="step__line" aria-hidden="true" /></template></nav>
    </template><main class="ad-main"><div class="container-page">
      <section v-if="!analysisOpen" class="panel" aria-labelledby="panel-goods">
        <div class="panel__pad">
          <div class="panel__head"><div class="min-w-0"><h2 class="panel__title" id="panel-goods">{{ readOnly ? '投放商品' : '选择要投放广告的商品' }}</h2><p class="panel__sub">{{ readOnly ? `当前计划已选择 ${selected.length} 款商品` : '确认商品组合，为下一步准备投放素材' }}</p></div></div>
          <p v-if="selectionStale" class="analysis-stale" role="status">商品已调整，需更新分析。原草稿已保留，旧分析不会用于后续素材与预算准备。</p>
          <p v-if="!readOnly && !tipDismissed" class="tip" role="note" aria-label="商品投放建议">
            <svg class="tip__icon h-[17px] w-[17px]" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10" fill="currentColor" /><path d="M12 6.4v7.2M12 17.1h.01" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" /></svg>
            <span class="tip__text">建议相似ROI目标的商品集中在一条计划下投放，效果会更好。<span class="tip__accent">灵投将为您推荐最佳投放组合</span></span>
            <button class="tip__close" type="button" aria-label="关闭商品投放建议" title="关闭提示" @click="dismissTip"><svg viewBox="0 0 24 24" aria-hidden="true"><use href="#create-close" /></svg></button>
          </p>
        </div>
        <div class="panel__body" id="goods-body">
          <ProductSuggestions v-if="!readOnly" ref="suggestions" :account="account" :selected="selected" :draft-id="draftId" :busy="saving" :source-product-id="recommendationSource" @select="chooseSuggested" @clear-focus="recommendationSource = undefined" />
          <div v-if="!readOnly" ref="filters" class="filters">
            <div class="seg" role="group" aria-label="商品筛选"><button v-for="category in catalogPolicy.tabs" :key="category.value" class="seg__item" type="button" :aria-pressed="tab === category.value" @click="tab = category.value">{{ category.label }}</button></div>
            <label class="check" :title="activeTab.unpromoted ? undefined : '该分类不支持未投放商品筛选'"><input :checked="unpromoted && activeTab.unpromoted" class="checkbox" type="checkbox" :disabled="!activeTab.unpromoted" @change="unpromoted = ($event.target as HTMLInputElement).checked" /><span class="check__label">只看未投放商品</span></label>
            <div class="filters__right"><span class="filters__hint">（最多可选10个）</span><span class="search"><svg class="search__icon h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.4" fill="none" stroke="currentColor" stroke-width="1.9" /><path d="m15.6 15.6 4 4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /></svg><input v-model="query" class="search__input" type="search" placeholder="搜索商品名称/ID" aria-label="搜索商品名称或ID" /></span></div>
          </div>
          <RequestFailureNotice v-if="!queryPending && loadError && loadFailure" :context="`${activeTab.label}加载失败`" :failure="loadFailure" :busy="!loaded" @retry="emit('retry')" />
          <p v-else-if="!queryPending && loadError" class="pgrid__empty" role="alert">商品加载失败，请重试。<button class="retry" type="button" @click="emit('retry')">重新加载</button></p>
          <p v-else-if="queryPending || !loaded" class="pgrid__empty" role="status">正在加载商品…</p>
          <div v-else-if="filtered.length" class="pgrid" role="listbox" aria-multiselectable="true" aria-label="可投放商品列表">
            <div v-for="p in visible" :key="p.productId" class="pcard" role="option" :tabindex="readOnly || p.draft && !isSelected(p.productId) ? -1 : 0" :aria-disabled="readOnly || !!p.draft && !isSelected(p.productId)" :class="{ 'pcard--occupied': !!p.draft }" :aria-selected="isSelected(p.productId)" :aria-checked="isSelected(p.productId)" :aria-label="p.name" @click="choose(p)" @keydown.enter.prevent="choose(p)" @keydown.space.prevent="choose(p)">
              <span class="pcard__thumb"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" alt="" @error="broken[p.productId] = true" /><svg v-else class="product-placeholder" viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="7" width="34" height="34" rx="5" /><circle cx="18" cy="18" r="3" /><path d="m8 34 10-10 8 8 6-6 9 9" /></svg></span><span class="pcard__check" aria-hidden="true" />
              <p class="pcard__title" :title="p.name">{{ p.name }}</p><p v-if="p.draft" class="pcard__draft" :title="`已在草稿「${p.draft.planName}」中`">已在草稿中 · <router-link :to="{ path: '/campaigns/' + p.draft.planId + '/edit', query: { step: '1' } }" aria-disabled="false" @click.stop @keydown.stop>继续编辑</router-link></p><div class="pcard__row"><span class="pcard__price"><ProductPrice :price-info="p.priceInfo" :price="p.price" :user-price="p.userPrice" /></span></div><div class="pcard__stats"><span class="pcard__stock" :title="p.stock == null ? '暂未提供库存数据' : `库存 ${p.stock}`">库存 {{ p.stock ?? '—' }}</span><span class="pcard__sales" :title="p.sales == null ? '平台暂未提供销量数据' : `销量 ${p.sales}`">销量 {{ p.sales ?? '—' }}</span></div>
            </div>
          </div>
          <p v-else class="pgrid__empty">{{ emptyMessage }}</p>
          <div v-if="!readOnly && cursorPaging" class="pager"><span>第 {{ page }} 页 · 本页 {{ products.length }} 款商品</span><button class="pager__btn" type="button" :disabled="queryPending || !loaded || loadError || page <= 1 || !cursorHistory?.[page - 1]" aria-label="上一页" @click="page--"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m14.4 6-6 6 6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span class="pager__num" aria-current="page">{{ page }}</span><button class="pager__btn" type="button" :disabled="queryPending || !loaded || loadError || !pagination?.hasMore || !pagination?.nextCursor" aria-label="下一页" @click="page++"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m9.6 6 6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button></div>
          <div v-if="!readOnly && !cursorPaging" class="pager"><span class="tnum">共 {{ total }} 条记录</span><button class="pager__btn" type="button" :disabled="queryPending || !loaded || loadError || page <= 1" aria-label="上一页" @click="page--"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m14.4 6-6 6 6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span class="pager__num" aria-current="page">{{ page }}</span><button class="pager__btn" type="button" :disabled="queryPending || !loaded || loadError || page >= pages" aria-label="下一页" @click="page++"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m9.6 6 6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span>跳转</span><input v-model="jump" class="pager__jump" type="text" inputmode="numeric" aria-label="跳转到第几页" @change="jumpTo" @keydown.enter="jumpTo" /><select v-model.number="size" class="pager__size" aria-label="每页条数"><option :value="10">10条/页</option><option :value="30">30条/页</option><option :value="50">50条/页</option></select></div>
        </div>
        <div v-if="!readOnly" class="selbar"><span class="selbar__count">已选 <b>{{ selected.length }}</b>/10：</span><div class="selbar__list"><span v-if="!selected.length" class="selbar__hint">请选择要投放广告的商品，最多可选10款</span><span v-for="p in selected" :key="p.productId" class="sthumb"><button class="sthumb__box" type="button" :title="`查看${p.name}的推荐商品`" :aria-label="`查看${p.name}的推荐商品`" :aria-pressed="recommendationSource === p.qcProductId" :disabled="saving" @click="showRecommendations(p)"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" alt="" @error="broken[p.productId] = true" /><svg v-else class="product-placeholder" viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="7" width="34" height="34" rx="5" /><path d="m8 34 10-10 8 8 6-6 9 9" /></svg></button><button class="sthumb__remove" type="button" :disabled="saving" :aria-label="`移除${p.name}`" @click.stop="emit('remove', p.productId)"><svg class="h-[9px] w-[9px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#create-close" /></svg></button></span></div></div>
      </section>
      <section v-if="analysisOpen" ref="analysisPanel" class="panel analysis" aria-labelledby="panel-analysis">
        <div class="pt-8 sm:pt-9 lg:pt-10"><h2 class="analysis__title" id="panel-analysis">商品智能分析</h2></div>
        <div class="analysis-content" aria-live="polite">
          <div class="analysis-selection" aria-label="已选商品摘要"><span>已选 <b>{{ selected.length }}</b> 款商品</span><div class="analysis-selection__thumbs"><button v-for="p in selected" :key="p.productId" class="sthumb__box" type="button" :title="readOnly ? p.name : `查看${p.name}的推荐商品`" :aria-label="readOnly ? p.name : `查看${p.name}的推荐商品`" :aria-pressed="!readOnly && recommendationSource === p.qcProductId" :disabled="saving || readOnly" @click="showRecommendations(p)"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" alt="" @error="broken[p.productId] = true" /><span v-else>{{ p.name.slice(0, 2) }}</span></button></div><button v-if="!readOnly" class="analysis-adjust" type="button" :disabled="saving" @click="adjustProducts">调整商品</button><button v-else class="analysis-adjust" type="button" :aria-expanded="selectedExpanded" @click="selectedExpanded = !selectedExpanded">{{ selectedExpanded ? '收起商品' : '查看已选商品' }}</button></div>
          <ul v-if="readOnly && selectedExpanded" class="analysis-selected-names"><li v-for="p in selected" :key="p.productId">{{ p.name }}</li></ul>
          <ProductSuggestions v-if="!readOnly && recommendationsOpen" ref="suggestions" :account="account" :selected="selected" :draft-id="draftId" :busy="saving || analyzing" :source-product-id="recommendationSource" @select="chooseSuggested" @clear-focus="recommendationSource = undefined" />
          <aside v-if="basicAnalysis && (!basicAnalysis.canContinue || basicAnalysis.issues.length)" class="basic-analysis" :class="{ 'basic-analysis--blocked': !basicAnalysis.canContinue }" aria-label="商品基础检查">
            <strong>{{ basicAnalysis.canContinue ? '商品基础检查已通过' : '商品基础检查未通过' }}</strong>
            <p>{{ basicAnalysis.canContinue ? '已检查所选商品的信息和可投状态，可继续设置投放目标。主图地址与价格仍需以实际可用情况为准。' : '请先处理以下不可投问题，再继续设置投放目标。' }}</p>
            <ul v-if="basicAnalysis.issues.length"><li v-for="(issue, index) in basicAnalysis.issues" :key="`${issue.productId}:${issue.code}:${index}`" :class="{ 'basic-analysis__error': issue.severity === 'ERROR' }">{{ issue.productName ? `${issue.productName}：` : '' }}{{ issue.message }}</li></ul>
          </aside>
          <p class="analysis-status" :class="{ 'analysis-status--error': analysisStatus === 'FAILED' && !canContinue }" role="status">{{ analysisMessage }}</p>
          <p v-if="analysisStatus === 'FAILED' && basicAnalysis?.canContinue && analysisError" class="analysis-evidence-note">{{ analysisError }}</p>
          <button v-if="!readOnly && ['NONE', 'FAILED', 'SUCCEEDED'].includes(analysisStatus)" class="retry" type="button" :disabled="saving || analyzing" @click="emit('analyze', true)">重新分析</button>

          <ProductPreparationAnalysis :group-decision="groupDecision" @keep="emit('keep')" :products="selected" :analysis="analysis" :busy="saving" :read-only="readOnly" @group="ids => emit('group', ids)" />
        </div>
      </section>
    </div></main>
    <div v-if="!embedded" class="actionbar"><div class="container-page actionbar__inner"><button class="btn-next" type="button" :disabled="!selected.length || saving || analysisOpen && !canContinue" @click="analysisOpen ? emit('next') : confirm()">{{ saving ? '处理中…' : analysisOpen ? '确认商品，设置投放目标' : '分析所选商品' }}<svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><path d="M4.4 12h15.2M13.4 5.8 19.6 12l-6.2 6.2" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" /></svg></button><button class="btn-cancel" type="button" :disabled="saving || readOnly" @click="emit('cancel')">取消投放</button></div></div>
  </div>
</template>

<script setup lang="ts">
import AwemeAccountSummary from '@/components/AwemeAccountSummary.vue';
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import type { CreationProduct as ProductVo, ProductQuery, ProductAnalysis, ProductBasicAnalysis } from '@/features/delivery/creationApi';
import { canContinueProductAnalysis, productAnalysisProgress, productAnalysisInput } from '@/features/delivery/productAnalysisReadiness';
import catalogPolicy from '../../../../../lynx-ad-app/src/main/resources/product-catalog-policy.json' with { type: 'json' };
import { ElMessageBox } from 'element-plus';
import ProductPreparationAnalysis from './ProductPreparationAnalysis.vue';
import ProductSuggestions from './ProductSuggestions.vue';
import ProductPrice from '@/components/ProductPrice.vue';
import RequestFailureNotice from '@/components/RequestFailureNotice.vue';
import type { ApiRequestError } from '@/api/apiErrors';
interface AccountOption { key: string; awemeName: string; awemeId: string; avatar?: string; accountId?: string }
const props = defineProps<{ products: ProductVo[]; selected: ProductVo[]; loaded: boolean; loadError: boolean; loadFailure?: ApiRequestError; saving: boolean; account?: AccountOption; accounts: AccountOption[]; accountChips: string[]; total: number; pagination?: { nextCursor?: string | null; hasMore?: boolean }; cursorHistory?: Record<number, string>; analysisStatus: string; analysisError?: string; analysis?: ProductAnalysis; basicAnalysis?: ProductBasicAnalysis; analysisStartedAt?: number; readOnly?: boolean; embedded?: boolean; initialQuery?: ProductQuery; maxStep?: number; groupDecision?: string; draftId?: string }>();
const tipDismissed = defineModel<boolean>('tipDismissed', { default: false });
const filters = ref<HTMLElement>();
async function dismissTip() {
  tipDismissed.value = true;
  await nextTick();
  filters.value?.querySelector<HTMLButtonElement>('button')?.focus({ preventScroll: true });
}
const emit = defineEmits<{ keep: []; navigate: [step:number]; select: [product: ProductVo]; remove: [id: string]; account: [key: string]; retry: []; next: []; cancel: []; analyze: [refresh?: boolean]; group: [ids: string[]]; query: [query: ProductQuery] }>();
const bannerChips = ['商城+搜索等全域投放', '智能生成素材', '智能出价', '实时监控+优化'];
const steps = ['选择商品', '设置投放目标', '定素材并投放', '查看投放结果'];
const query = ref(props.initialQuery?.keyword ?? ''), page = ref(props.initialQuery?.page ?? 1), size = ref(props.initialQuery?.pageSize ?? 10), jump = ref(String(page.value));
const broken = ref<Record<string, boolean>>({});
const recommendationSource = ref<string>();
const recommendationsOpen = ref(false);
const suggestions = ref<InstanceType<typeof ProductSuggestions>>();
watch(() => [props.account?.key, props.draftId], () => { recommendationSource.value = undefined; recommendationsOpen.value = false; });
watch(() => props.selected.map(p => p.qcProductId), ids => {
  if (recommendationSource.value && !ids.includes(recommendationSource.value)) recommendationSource.value = undefined;
});
const analysisOpen = ref(false), adjusting = ref(false), selectedExpanded = ref(false), accountOpen = ref(false);
const analyzedInput = ref<string>();
const selectionStale = computed(() => adjusting.value && analyzedInput.value !== productAnalysisInput(props.selected));
const analysisPanel = ref<HTMLElement>();
const filtered = computed(() => props.products);
const pages = computed(() => Math.max(1, Math.ceil(props.total / size.value)));
const visible = computed(() => filtered.value);

const tab = ref(props.initialQuery?.tab ?? 'ALL'), unpromoted = ref(props.initialQuery?.unpromoted ?? true);
const tabValues = catalogPolicy.tabs.map(tab=>tab.value);
if(!tabValues.includes(tab.value))tab.value='ALL';
const activeTab = computed(() => catalogPolicy.tabs.find(category => category.value === tab.value)!);
const cursorPaging = computed(() => activeTab.value.pagination === 'CURSOR');
const emptyMessage = computed(() => query.value.trim() ? '没有匹配的商品，换个关键词试试'
  : cursorPaging.value && props.pagination?.hasMore ? '本页暂无商品，可继续查看下一页'
  : tab.value !== 'ALL' ? `千川暂未返回“${activeTab.value.label}”商品，可切换其他分类查看`
  : unpromoted.value ? '当前筛选条件下暂无商品，可取消“只看未投放商品”查看'
  : '该抖音号暂无可投放商品');
const analyzing = computed(() => ['QUEUED','RUNNING'].includes(props.analysisStatus));
const canContinue = computed(() => canContinueProductAnalysis(props.basicAnalysis, props.analysisStatus));
const analysisClock = ref(Date.now());
let analysisTimer: ReturnType<typeof setInterval> | undefined;
watch(analyzing, active => {
  clearInterval(analysisTimer); analysisClock.value = Date.now();
  if (active) analysisTimer = setInterval(() => { analysisClock.value = Date.now(); }, 1000);
}, { immediate: true });
onUnmounted(() => clearInterval(analysisTimer));
const analysisMessage = computed(() => props.analysis?.processing?.inference_error ? '智能分析暂不可用，已保留基础准备信息，可继续设置目标或重新分析。' : productAnalysisProgress(props.basicAnalysis, props.analysisStatus, Math.max(0, analysisClock.value - (props.analysisStartedAt || analysisClock.value))) || (analyzing.value ? '正在准备商品建议，可先继续设置投放目标。' : props.analysisStatus === 'FAILED' ? '智能建议暂不可用，基础检查通过后仍可继续。' : props.analysisStatus === 'SUCCEEDED' ? '投放准备分析已完成，请核对建议及待确认信息。' : '等待商品分析开始'));
watch([() => props.analysisStatus, () => props.analysis], ([status, analysis]) => {
  if (!adjusting.value && (status !== 'NONE' || analysis)) { analysisOpen.value = true; analyzedInput.value = productAnalysisInput(props.selected); }
}, { immediate: true });
const queryPending = ref(false);
let queryTimer: ReturnType<typeof setTimeout> | undefined;
watch([query, page, size, tab, unpromoted], () => { if (props.readOnly) return; queryPending.value = true; clearTimeout(queryTimer); queryTimer = setTimeout(() => { emit('query', { page: page.value, pageSize: size.value, keyword: query.value, tab: tab.value, unpromoted: unpromoted.value && activeTab.value.unpromoted, ...(cursorPaging.value ? { cursor: props.cursorHistory?.[page.value] ?? '0' } : {}) }); queryPending.value = false; }, 250); });
onUnmounted(() => clearTimeout(queryTimer));
watch([query, size, tab, unpromoted], () => { page.value = 1; });
watch(pages, n => { if (!cursorPaging.value) page.value = Math.min(page.value, n); });
watch(page, n => { jump.value = String(n); });
function jumpTo() { const n = Number(jump.value); page.value = Number.isInteger(n) && n > 0 ? Math.min(n, pages.value) : 1; jump.value = String(page.value); }
function isSelected(id: string) { return props.selected.some(p => p.productId === id); }
function choose(p: ProductVo) {
  if (props.readOnly || analysisOpen.value || props.saving || (p.draft && !isSelected(p.productId))) return;
  emit('select', p);
}
async function showRecommendations(p: ProductVo) {
  if (props.readOnly || props.saving) return;
  recommendationSource.value = p.qcProductId; recommendationsOpen.value = true;
  await nextTick(); suggestions.value?.reveal();
}
function chooseSuggested(p: ProductVo) {
  if (props.readOnly || props.saving || analysisOpen.value && analyzing.value) return;
  if (analysisOpen.value) { adjusting.value = true; analysisOpen.value = false; emit('retry'); }
  choose(p);
}
async function adjustProducts() {
  if (props.readOnly || props.saving) return;
  try { await ElMessageBox.confirm('调整商品后需更新分析，相关目标和素材也可能需要重新准备。当前草稿会保留。', '调整商品', { confirmButtonText: '调整商品', cancelButtonText: '保留当前商品', type: 'warning' }); } catch { return; }
  adjusting.value = true; analysisOpen.value = false; emit('retry');
  await nextTick(); filters.value?.querySelector<HTMLButtonElement>('button')?.focus({ preventScroll: true });
}
async function confirm() {
  if (props.readOnly || props.saving || !props.selected.length) return;
  adjusting.value = false; recommendationsOpen.value = false; analysisOpen.value = true; emit('analyze');
  await nextTick(); analysisPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
</script>

<style scoped src="./create-products.css"></style>
