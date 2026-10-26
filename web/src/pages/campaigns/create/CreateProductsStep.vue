<template>
  <div class="create-products">
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
        <AwemeAccountPicker v-if="account" :account="account" :accounts="accounts" :chips="accountChips" :disabled="saving || readOnly" @change="emit('account', $event)" />
      </div>
    </section>
    <nav class="ad-stepper" aria-label="投放计划进度"><template v-for="(label, i) in steps" :key="label"><button type="button" :disabled="saving || i === 3 || i+1 > (maxStep ?? 1)" @click="emit('navigate', i+1)" :aria-label="label" class="step" :class="{ 'step--done': i > 0 && i + 1 <= (reachedStep ?? 1) }" :aria-current="i === 0 ? 'step' : undefined"><span class="step__dot"><svg v-if="i === 3" viewBox="0 0 24 24" aria-hidden="true"><use href="#create-check" /></svg><template v-else>{{ i + 1 }}</template></span><span class="step__label">{{ label }}</span></button><span v-if="i < 3" class="step__line" aria-hidden="true" /></template></nav>
    </template><main class="ad-main"><div class="container-page">
      <ProductSelectionNotice :message="selectionNotice" /><p v-if="selectionLocked" class="analysis-stale" role="status">素材正在生成或处理中，暂不能新增、删除或替换商品，请完成后再调整。</p>
      <section v-if="!analysisOpen" class="panel" aria-labelledby="panel-goods">
        <div class="panel__pad">
          <div class="panel__head"><div class="min-w-0"><h2 class="panel__title" id="panel-goods" tabindex="-1">{{ readOnly ? '投放商品' : '选择要投放广告的商品' }}</h2><p class="panel__sub">{{ readOnly ? `当前计划已选择 ${selected.length} 款商品` : '确认商品组合，为下一步准备投放素材' }}</p></div></div>
          <p v-if="selectionStale" class="analysis-stale" role="status">商品已调整，需更新分析。原草稿已保留，旧分析不会用于后续素材与预算准备。</p>
          <p v-if="!readOnly && !recommendationsOpen && !tipDismissed" class="tip" role="note" aria-label="商品投放建议">
            <svg class="tip__icon h-[17px] w-[17px]" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10" fill="currentColor" /><path d="M12 6.4v7.2M12 17.1h.01" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" /></svg>
            <span class="tip__text">建议相似ROI目标的商品集中在一条计划下投放，效果会更好。<span class="tip__accent">灵投将为您推荐最佳投放组合</span></span>
            <button class="tip__close" type="button" aria-label="关闭商品投放建议" title="关闭提示" @click="dismissTip"><svg viewBox="0 0 24 24" aria-hidden="true"><use href="#create-close" /></svg></button>
          </p>
        </div>
        <div class="panel__body" id="goods-body">
          <div v-if="!readOnly && !recommendationsOpen" ref="filters" class="filters">
            <div class="seg" role="group" aria-label="商品筛选"><button v-for="category in catalogPolicy.tabs" :key="category.value" class="seg__item" type="button" :aria-pressed="tab === category.value" @click="tab = category.value">{{ category.label }}</button></div>
            <label class="check" :title="activeTab.unpromoted ? undefined : '该分类不支持未投放商品筛选'"><input :checked="unpromoted && activeTab.unpromoted" class="checkbox" type="checkbox" :disabled="!activeTab.unpromoted" @change="unpromoted = ($event.target as HTMLInputElement).checked" /><span class="check__label">只看未投放商品</span></label>
            <div class="filters__right"><span class="filters__hint">（最多可选10个）</span><span class="search"><svg class="search__icon h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.4" fill="none" stroke="currentColor" stroke-width="1.9" /><path d="m15.6 15.6 4 4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /></svg><input v-model="query" class="search__input" type="search" placeholder="搜索商品名称/ID" aria-label="搜索商品名称或ID" /></span></div>
          </div>
          <div v-if="recommendationsOpen && !suggestionsLoading && !suggestionsFailure && filtered.length" class="similar-toolbar">
            <p>相似商品 <span :title="suggestionSource?.name">{{ suggestionSource?.name }}</span></p>
            <button class="catalog-return" type="button" @click="showAllProducts">返回查看全部商品</button>
          </div>
          <div v-if="recommendationsOpen && (suggestionsLoading || suggestionsFailure || !filtered.length)" class="pgrid__empty similar-empty" :role="suggestionsFailure ? 'alert' : 'status'" :aria-busy="suggestionsLoading">
            <template v-if="suggestionsLoading"><p>正在查找相似商品…</p></template>
            <template v-else><h3>商品列表为空</h3><p>{{ suggestionsFailure ? '推荐商品暂时获取失败，请重试或查看全部商品。' : '暂未找到相似的可投放商品' }}</p></template>
            <div class="similar-empty__actions"><button v-if="suggestionsFailure" class="retry" type="button" :disabled="saving" @click="loadSuggestions(true)">重试推荐</button><button class="catalog-return" type="button" @click="showAllProducts">返回查看全部商品</button></div>
          </div>
          <RequestFailureNotice v-else-if="!recommendationsOpen && !queryPending && loadError && loadFailure" :context="`${activeTab.label}加载失败`" :failure="loadFailure" :busy="!loaded" @retry="emit('retry')" />
          <p v-else-if="!recommendationsOpen && !queryPending && loadError" class="pgrid__empty" role="alert">商品加载失败，请重试。<button class="retry" type="button" @click="emit('retry')">重新加载</button></p>
          <p v-else-if="!recommendationsOpen && (queryPending || !loaded)" class="pgrid__empty" role="status">正在加载商品…</p>
          <div v-else-if="filtered.length" class="pgrid" role="listbox" aria-multiselectable="true" aria-label="可投放商品列表">
            <div v-for="p in visible" :key="p.productId" class="pcard" role="option" :tabindex="productDisabled(p) ? -1 : 0" :aria-disabled="productDisabled(p)" :class="{ 'pcard--occupied': !!p.draft, 'pcard--unavailable': !readOnly && !isSelected(p.productId) && !!productUnavailableReason(p) }" :aria-selected="isSelected(p.productId)" :aria-checked="isSelected(p.productId)" :aria-label="p.name" @click="choose(p)" @keydown.enter.prevent="choose(p)" @keydown.space.prevent="choose(p)">
              <span class="pcard__thumb"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" alt="" @error="broken[p.productId] = true" /><svg v-else class="product-placeholder" viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="7" width="34" height="34" rx="5" /><circle cx="18" cy="18" r="3" /><path d="m8 34 10-10 8 8 6-6 9 9" /></svg></span><span class="pcard__check" aria-hidden="true" />
              <p class="pcard__title" :title="p.name">{{ p.name }}</p>
              <div class="pcard__row"><span class="pcard__price"><ProductPrice :price-info="p.priceInfo" :price="p.price" :user-price="p.userPrice" /></span></div>
              <div class="pcard__stats">
                <span class="pcard__stock" :title="p.stock == null ? '暂未提供库存数据' : `库存 ${p.stock}`">库存 {{ p.stock ?? '—' }}</span>
                <span class="pcard__sales" :title="p.sales == null ? '平台暂未提供销量数据' : `销量 ${p.sales}`">销量 {{ p.sales ?? '—' }}</span>
              </div>
              <p v-if="!readOnly && productUnavailableReason(p)" class="pcard__unavailable" :title="productUnavailableReason(p)">{{ productUnavailableReason(p) }}</p>
              <p v-if="p.draft" class="pcard__draft" :title="`已在草稿「${p.draft.planName}」中`">已在草稿中 · <router-link :to="{ path: '/campaigns/' + p.draft.planId + '/edit', query: { step: '1' } }" aria-disabled="false" @click.stop @keydown.stop>继续编辑</router-link></p>
            </div>
          </div>
          <p v-else class="pgrid__empty">{{ emptyMessage }}</p>
          <div v-if="!readOnly && !recommendationsOpen && cursorPaging" class="pager"><span>第 {{ page }} 页 · 本页 {{ products.length }} 款商品</span><button class="pager__btn" type="button" :disabled="queryPending || !loaded || loadError || page <= 1 || !cursorHistory?.[page - 1]" aria-label="上一页" @click="page--"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m14.4 6-6 6 6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span class="pager__num" aria-current="page">{{ page }}</span><button class="pager__btn" type="button" :disabled="queryPending || !loaded || loadError || !pagination?.hasMore || !pagination?.nextCursor" aria-label="下一页" @click="page++"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m9.6 6 6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button></div>
          <div v-if="!readOnly && !recommendationsOpen && !cursorPaging" class="pager"><span class="tnum">共 {{ total }} 条记录</span><button class="pager__btn" type="button" :disabled="queryPending || !loaded || loadError || page <= 1" aria-label="上一页" @click="page--"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m14.4 6-6 6 6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span class="pager__num" aria-current="page">{{ page }}</span><button class="pager__btn" type="button" :disabled="queryPending || !loaded || loadError || page >= pages" aria-label="下一页" @click="page++"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m9.6 6 6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span>跳转</span><input v-model="jump" class="pager__jump" type="text" inputmode="numeric" aria-label="跳转到第几页" @change="jumpTo" @keydown.enter="jumpTo" /><select v-model.number="size" class="pager__size" aria-label="每页条数"><option :value="10">10条/页</option><option :value="30">30条/页</option><option :value="50">50条/页</option></select></div>
        </div>
        <div v-if="!readOnly" class="selbar"><span class="selbar__count">已选 <b>{{ selected.length }}</b>/10：</span><div class="selbar__list"><span v-if="!selected.length" class="selbar__hint">请选择要投放广告的商品，最多可选10款</span><span v-for="p in selected" :key="p.productId" class="sthumb"><button class="sthumb__box" type="button" :title="`查看${p.name}的推荐商品`" :aria-label="`查看${p.name}的推荐商品`" :aria-pressed="recommendationSource === p.qcProductId" :disabled="saving" @click="showRecommendations(p)"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" alt="" @error="broken[p.productId] = true" /><svg v-else class="product-placeholder" viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="7" width="34" height="34" rx="5" /><path d="m8 34 10-10 8 8 6-6 9 9" /></svg></button><button class="sthumb__remove" type="button" :disabled="saving || selectionLocked" :aria-label="`移除${p.name}`" @click.stop="emit('remove', p.productId)"><svg class="h-[9px] w-[9px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#create-close" /></svg></button></span></div></div>
      </section>
      <section v-if="analysisOpen" ref="analysisPanel" class="panel analysis" aria-labelledby="panel-analysis">
        <div class="analysis-heading"><h2 class="analysis__title" id="panel-analysis">商品智能分析</h2></div>
        <div class="analysis-content" aria-live="polite">
          <div class="analysis-selection" aria-label="已选商品摘要">
            <span>已选 <b>{{ selected.length }}</b> 款商品</span>
            <div class="analysis-selection__thumbs">
              <el-popover v-for="p in selected" :key="p.productId" placement="bottom-start" width="min(320px, calc(100vw - 32px))" :trigger="['hover', 'focus']" :show-after="150" :hide-after="100" popper-class="selected-product-popover">
                <template #reference>
                  <button class="sthumb__box" type="button" :aria-label="readOnly ? `查看${p.name}的商品信息` : `查看${p.name}的推荐商品`" :aria-pressed="readOnly ? undefined : recommendationSource === p.qcProductId" :disabled="saving" @click="!readOnly && showRecommendations(p)"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" alt="" @error="broken[p.productId] = true" /><span v-else>{{ p.name.slice(0, 2) }}</span></button>
                </template>
                <div class="selected-product-info" :aria-label="`${p.name}的商品信息`">
                  <strong class="selected-product-info__name">{{ p.name }}</strong>
                  <dl>
                    <div><dt>价格</dt><dd class="selected-product-info__price">{{ productPriceText(p.priceInfo, p.price) }}<small v-if="p.priceInfo?.stale">上次价格</small></dd></div>
                    <div><dt>销量</dt><dd>{{ p.sales == null ? '暂未获取' : p.sales.toLocaleString('zh-CN') }}</dd></div>
                    <div><dt>库存</dt><dd>{{ p.stock == null ? '暂未获取' : p.stock.toLocaleString('zh-CN') }}</dd></div>
                  </dl>
                </div>
              </el-popover>
            </div>
            <button v-if="!readOnly || allowAdjustment" class="analysis-adjust" type="button" :disabled="saving || selectionLocked || !!adjustmentBlockedReason" :title="adjustmentBlockedReason" @click="adjustProducts"><PreparationIcon name="refresh"/>调整商品</button>
          </div>
          <aside v-if="basicAnalysis && (!basicAnalysis.canContinue || basicAnalysis.issues.length)" class="basic-analysis" :class="{ 'basic-analysis--blocked': !basicAnalysis.canContinue }" aria-label="商品基础检查">
            <strong>{{ basicAnalysis.canContinue ? '商品基础检查已通过' : '商品基础检查未通过' }}</strong>
            <p>{{ basicAnalysis.canContinue ? '已检查所选商品的信息和可投状态。主图地址与价格仍需以实际可用情况为准。' : '请先处理以下不可投问题，再继续设置投放目标。' }}</p>
            <ul v-if="basicAnalysis.issues.length"><li v-for="(issue, index) in basicAnalysis.issues" :key="`${issue.productId}:${issue.code}:${index}`" :class="{ 'basic-analysis__error': issue.severity === 'ERROR' }">{{ issue.productName ? `${issue.productName}：` : '' }}{{ issue.message }}</li></ul>
          </aside>
          <p v-if="analysisStatus === 'FAILED' && basicAnalysis?.canContinue && analysisError" class="analysis-evidence-note">{{ analysisError }}</p>

          <ProductPreparationAnalysis v-if="presentation === 'ready'" :group-decision="groupDecision" @keep="emit('keep')" :products="selected" :analysis="analysis" :busy="saving" :read-only="readOnly" @group="ids => emit('group', ids)" />
          <div v-else class="analysis-result-empty" :aria-busy="analyzing">
            <strong>{{ presentation === 'pending' ? '正在生成商品智能分析' : presentation === 'failed' ? '尚未生成有效的智能分析结果' : presentation === 'outdated' ? '商品分析需要更新' : '等待分析所选商品' }}</strong>
            <p>{{ presentation === 'pending' ? '我正在逐商品分析需求场景、素材方向和待确认信息，完成后在这里展示。' : presentation === 'failed' ? '请重新分析。已选商品和基础检查结果已保留。' : presentation === 'outdated' ? '当前保存的是旧版分析，尚无本页面所需的逐商品报告。请重新分析以获取最新结果。' : '开始分析后，将展示商品组合建议和逐商品素材方向。' }}</p>
            <small v-if="readOnly && ['failed', 'outdated'].includes(presentation)">请在编辑计划时重新分析商品。</small>
          </div>
        </div>
      </section>
    </div></main>
    <CreationFooter :plan-status="planStatus" :save-label="contentEditing ? '暂存修改' : '保存草稿'" v-if="!embedded" :plan-name="planName" :busy="saving" :save-disabled="draftSaveDisabled || readOnly" @back="emit('cancel')" @save="emit('saveDraft')"><button class="btn-next" type="button" :disabled="!selected.length || saving || analysisOpen && !canContinue" @click="analysisOpen ? emit('next') : confirm()">{{ saving ? '处理中…' : analysisOpen ? '下一步' : '分析所选商品' }}<svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><path d="M4.4 12h15.2M13.4 5.8 19.6 12l-6.2 6.2" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" /></svg></button></CreationFooter>
  </div>
</template>

<script setup lang="ts">
import AwemeAccountPicker from '@/components/AwemeAccountPicker.vue';
import CreationFooter from './CreationFooter.vue';
import PreparationIcon from '@/components/PreparationIcon.vue';
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import type { CreationProduct as ProductVo, ProductQuery, ProductAnalysis, ProductBasicAnalysis } from '@/features/delivery/creationApi';
import { canContinueProductAnalysis, productAnalysisInput, productAnalysisPresentation } from '@/features/delivery/productAnalysisReadiness';
import catalogPolicy from '../../../../../lynx-ad-app/src/main/resources/product-catalog-policy.json' with { type: 'json' };
import ProductSelectionNotice from './ProductSelectionNotice.vue';
import ProductPreparationAnalysis from './ProductPreparationAnalysis.vue';
import { useProductSuggestions } from '@/features/delivery/useProductSuggestions';
import ProductPrice from '@/components/ProductPrice.vue';
import { productPriceText } from '@/features/catalog/productPrice';
import RequestFailureNotice from '@/components/RequestFailureNotice.vue';
import type { ApiRequestError } from '@/api/apiErrors';
interface AccountOption { key: string; awemeName: string; awemeId: string; avatar?: string; accountId?: string }
const props = defineProps<{ products: ProductVo[]; selected: ProductVo[]; loaded: boolean; loadError: boolean; loadFailure?: ApiRequestError; saving: boolean; account?: AccountOption; accounts: AccountOption[]; accountChips: string[]; total: number; pagination?: { nextCursor?: string | null; hasMore?: boolean }; cursorHistory?: Record<number, string>; analysisStatus: string; analysisError?: string; analysis?: ProductAnalysis; basicAnalysis?: ProductBasicAnalysis; analysisStartedAt?: number; readOnly?: boolean; allowAdjustment?: boolean; adjustmentBlockedReason?: string; selectionLocked?: boolean; selectionNotice?: string; contentEditing?:boolean; planStatus?:string; embedded?: boolean; initialQuery?: ProductQuery; maxStep?: number; reachedStep?: number; groupDecision?: string; draftId?: string; planName?: string; draftSaveDisabled?: boolean }>();
const tipDismissed = defineModel<boolean>('tipDismissed', { default: false });
const filters = ref<HTMLElement>();
async function dismissTip() {
  tipDismissed.value = true;
  await nextTick();
  filters.value?.querySelector<HTMLButtonElement>('button')?.focus({ preventScroll: true });
}
const emit = defineEmits<{ adjust: []; keep: []; navigate: [step:number]; select: [product: ProductVo]; remove: [id: string]; account: [key: string]; catalog: []; retry: []; next: []; cancel: []; saveDraft: []; analyze: [refresh?: boolean]; group: [ids: string[]]; query: [query: ProductQuery] }>();
const bannerChips = ['商城+搜索等全域投放', '智能生成素材', '智能出价', '实时监控+优化'];
const steps = ['选择商品', '设置投放目标', '定素材并投放', '查看投放结果'];
const query = ref(props.initialQuery?.keyword ?? ''), page = ref(props.initialQuery?.page ?? 1), size = ref(props.initialQuery?.pageSize ?? 10), jump = ref(String(page.value));
const broken = ref<Record<string, boolean>>({});
const recommendationSource = ref<string>();
const recommendationsOpen = ref(false);
function closeRecommendations(){recommendationsOpen.value=false;recommendationSource.value=undefined;}
watch(() => [props.account?.key, props.draftId], closeRecommendations);
watch(() => props.selected.map(p => p.qcProductId), ids => {
  if (recommendationSource.value && !ids.includes(recommendationSource.value)) closeRecommendations();
});
const analysisOpen = ref(false), adjusting = ref(false);
const analyzedInput = ref<string>();
const selectionStale = computed(() => adjusting.value && analyzedInput.value !== productAnalysisInput(props.selected));
const analysisPanel = ref<HTMLElement>();
const { sourceProduct: suggestionSource, products: suggestedProducts, failure: suggestionsFailure, loading: suggestionsLoading, load: loadSuggestions } = useProductSuggestions({
  account: () => props.readOnly ? undefined : props.account,
  selected: () => props.selected,
  draftId: () => props.draftId,
  sourceProductId: () => recommendationsOpen.value ? recommendationSource.value : undefined,
});
const filtered = computed(() => recommendationsOpen.value ? suggestedProducts.value : props.products);
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
const presentation = computed(() => productAnalysisPresentation(props.analysis, props.analysisStatus, props.selected));
const canContinue = computed(() => canContinueProductAnalysis(props.basicAnalysis, props.analysisStatus, props.analysis, props.selected));
watch([() => props.analysisStatus, () => props.analysis], ([status, analysis]) => {
  if (!adjusting.value && (status !== 'NONE' || analysis)) { analysisOpen.value = true; analyzedInput.value = productAnalysisInput(props.selected); }
}, { immediate: true });
watch([analysisOpen, recommendationsOpen, () => props.account?.key], ([analysis, recommendations, key]) => {
  if (!props.readOnly && key && !analysis && !recommendations) emit('catalog');
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
function productUnavailableReason(p: ProductVo) {
  return p.unavailableReasons?.filter(Boolean).join('；') || (p.stock != null && p.stock <= 0 ? '库存不足，暂不可投放' : '');
}
function productDisabled(p: ProductVo) {
  return !!(props.readOnly || props.saving || props.selectionLocked || (p.draft || productUnavailableReason(p)) && !isSelected(p.productId)
    || recommendationsOpen.value && (analyzing.value || props.selected.length >= 10 && !isSelected(p.productId)));
}
function choose(p: ProductVo) {
  if (analysisOpen.value || productDisabled(p)) return;
  emit('select', p);
}
async function showRecommendations(p: ProductVo) {
  if (props.readOnly || props.saving || !props.selected.some(selected=>selected.qcProductId===p.qcProductId)) return;
  if (analysisOpen.value) { adjusting.value = true; analysisOpen.value = false; }
  recommendationSource.value = p.qcProductId; recommendationsOpen.value = true;
  await nextTick(); revealCatalog();
}
function revealCatalog() {
  const heading = document.getElementById('panel-goods');
  heading?.focus({ preventScroll: true });
  heading?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
async function showAllProducts() {
  closeRecommendations();
  const changed = tab.value !== 'ALL' || query.value !== '' || page.value !== 1 || unpromoted.value;
  tab.value = 'ALL'; query.value = ''; page.value = 1; unpromoted.value = false;
  if (!changed) emit('retry');
  await nextTick(); revealCatalog();
}
async function adjustProducts() {
  if (props.saving || props.selectionLocked || props.adjustmentBlockedReason) return;
  if (props.readOnly) { if (props.allowAdjustment) emit('adjust'); return; }
  adjusting.value = true; analysisOpen.value = false; emit('retry');
  await nextTick(); filters.value?.querySelector<HTMLButtonElement>('button')?.focus({ preventScroll: true });
}
async function confirm() {
  if (props.readOnly || props.saving || !props.selected.length) return;
  adjusting.value = false; closeRecommendations(); emit('analyze');
  await nextTick(); analysisPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
defineExpose({ adjustProducts });
</script>

<style scoped src="./create-products.css"></style>
