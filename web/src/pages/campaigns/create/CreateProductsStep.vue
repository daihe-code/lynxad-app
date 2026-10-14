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
            <span class="ad-account__name" :title="account.awemeName">{{ account.awemeName }}</span><span class="ad-account__id" :title="account.awemeId">抖音号ID：{{ account.awemeId }}</span>
            <span class="ad-account__btns"><span v-for="(chip, i) in accountChips" :key="chip" :class="i ? 'ad-account__btn-warn' : 'ad-account__btn-solid'">{{ chip }}</span></span>
            <span class="ad-account__chevron" aria-hidden="true"><svg class="h-4 w-4" viewBox="0 0 24 24"><use href="#create-caret" /></svg></span>
          </button>
          <div v-if="accountOpen" class="account-menu" role="listbox" aria-label="选择抖音号"><button v-for="opt in accounts" :key="opt.key" type="button" role="option" :aria-selected="opt.key === account.key" @click="accountOpen = false; emit('account', opt.key)">{{ opt.awemeName }}<small>{{ opt.awemeId }}</small></button></div>
          <p class="ad-account__hint">下拉切换需要投放广告的抖音号</p>
        </div>
        <select v-if="account" class="account-mobile" :disabled="saving || readOnly" :value="account.key" aria-label="选择抖音号" @change="emit('account', ($event.target as HTMLSelectElement).value)"><option v-for="opt in accounts" :key="opt.key" :value="opt.key">{{ opt.awemeName }} · {{ opt.awemeId }}</option></select>
      </div>
    </section>
    <nav class="ad-stepper" aria-label="投放计划进度"><template v-for="(label, i) in steps" :key="label"><span class="step" :aria-current="i === 0 ? 'step' : undefined"><span class="step__dot"><svg v-if="i === 3" viewBox="0 0 24 24" aria-hidden="true"><use href="#create-check" /></svg><template v-else>{{ i + 1 }}</template></span><span class="step__label">{{ label }}</span></span><span v-if="i < 3" class="step__line" aria-hidden="true" /></template></nav>
    </template><main class="ad-main"><div class="container-page">
      <section class="panel" aria-labelledby="panel-goods">
        <div class="panel__pad">
          <div class="panel__head"><div class="min-w-0"><h2 class="panel__title" id="panel-goods">{{ readOnly ? '投放商品' : '选择要投放广告的商品' }}<span class="panel__badge">商品全域投放</span></h2><p class="panel__sub">{{ readOnly ? `当前计划已选择 ${selected.length} 款商品` : '系统将为选中商品自动匹配最佳投放策略' }}</p></div><button v-if="analysisOpen" class="panel__toggle" type="button" :aria-expanded="listExpanded" aria-controls="goods-body" @click="listExpanded = !listExpanded">{{ listExpanded ? '收起' : '展开' }}<svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3.4 8.4 4.3L12 12 3.6 7.7 12 3.4Zm-8.4 8.9 8.4 4.3 8.4-4.3" fill="none" stroke="currentColor" stroke-width="1.7" /></svg></button></div>
          <p v-if="!readOnly" class="tip"><svg class="tip__icon h-[17px] w-[17px]" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10" fill="currentColor" /><path d="M12 6.4v7.2M12 17.1h.01" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" /></svg><span class="tip__text">建议相似ROI目标的商品集中在一条计划下投放，效果会更好。<span class="tip__accent">灵投将为您推荐最佳投放组合</span></span></p>
        </div>
        <div v-show="!analysisOpen || listExpanded" class="panel__body" id="goods-body">
          <div v-if="!readOnly" class="filters">
            <div class="seg" role="group" aria-label="商品筛选"><button v-for="(name, i) in ['全部商品', ...categories]" :key="name" class="seg__item" type="button" :aria-pressed="tab === tabValues[i]" :disabled="tabValues[i] === 'NEW_PRODUCT'" @click="tab = tabValues[i]!">{{ name }}</button></div>
            <label class="check"><input v-model="unpromoted" class="checkbox" type="checkbox" :disabled="!['ALL','GOOD_BOOST'].includes(tab)" /><span class="check__label">只看未投放商品</span></label>
            <div class="filters__right"><button class="retry" type="button" :disabled="!loaded || saving" @click="emit('refresh-prices')">刷新价格</button><span class="filters__hint">（最多可选10个）</span><span class="search"><svg class="search__icon h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.4" fill="none" stroke="currentColor" stroke-width="1.9" /><path d="m15.6 15.6 4 4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /></svg><input v-model="query" class="search__input" type="search" placeholder="搜索商品名称/ID" aria-label="搜索商品名称或ID" /></span></div>
          </div>
          <p v-if="loadError" class="pgrid__empty" role="alert">商品加载失败，请重试。<button class="retry" type="button" @click="emit('retry')">重新加载</button></p>
          <p v-else-if="!loaded" class="pgrid__empty" role="status">正在加载商品…</p>
          <div v-else-if="filtered.length" class="pgrid" role="listbox" aria-multiselectable="true" aria-label="可投放商品列表">
            <div v-for="p in visible" :key="p.productId" class="pcard" role="option" :tabindex="readOnly ? -1 : 0" :aria-disabled="readOnly" :aria-selected="isSelected(p.productId)" :aria-checked="isSelected(p.productId)" :aria-label="p.name" @click="choose(p)" @keydown.enter.prevent="choose(p)" @keydown.space.prevent="choose(p)">
              <span class="pcard__thumb"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" alt="" @error="broken[p.productId] = true" /><svg v-else class="product-placeholder" viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="7" width="34" height="34" rx="5" /><circle cx="18" cy="18" r="3" /><path d="m8 34 10-10 8 8 6-6 9 9" /></svg></span><span class="pcard__check" aria-hidden="true" />
              <p class="pcard__title" :title="p.name">{{ p.name }}</p><div class="pcard__row"><span class="pcard__price"><ProductPrice :price-info="p.priceInfo" :price="p.price" :user-price="p.userPrice" /></span><span class="pcard__stock" :title="p.stock == null ? '暂未提供库存数据' : `库存 ${p.stock}`">库存 {{ p.stock ?? '—' }}</span></div><div class="pcard__row2"><span class="pcard__tag">商品全域投放</span><span class="pcard__sales" title="暂未提供月销数据">月销 —</span></div>
            </div>
          </div>
          <p v-else class="pgrid__empty">{{ query.trim() ? '没有匹配的商品，换个关键词试试' : '该抖音号暂无可投放商品' }}</p>
          <div v-if="!readOnly" class="pager"><span class="tnum">共 {{ total }} 条记录</span><button class="pager__btn" type="button" :disabled="page <= 1" aria-label="上一页" @click="page--"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m14.4 6-6 6 6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span class="pager__num" aria-current="page">{{ page }}</span><button class="pager__btn" type="button" :disabled="page >= pages" aria-label="下一页" @click="page++"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m9.6 6 6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span>跳转</span><input v-model="jump" class="pager__jump" type="text" inputmode="numeric" aria-label="跳转到第几页" @change="jumpTo" @keydown.enter="jumpTo" /><select v-model.number="size" class="pager__size" aria-label="每页条数"><option :value="10">10条/页</option><option :value="30">30条/页</option><option :value="50">50条/页</option></select></div>
        </div>
        <div v-if="!readOnly" class="selbar"><span class="selbar__count">已选 <b>{{ selected.length }}</b>/10：</span><div class="selbar__list"><span v-if="!selected.length" class="selbar__hint">请选择要投放广告的商品，建议3-8款，投放效果更好</span><span v-for="p in selected" :key="p.productId" class="sthumb" :title="p.name"><span class="sthumb__box"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" :alt="p.name" @error="broken[p.productId] = true" /><svg v-else class="product-placeholder" viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="7" width="34" height="34" rx="5" /><path d="m8 34 10-10 8 8 6-6 9 9" /></svg></span><button v-if="!analysisOpen" class="sthumb__remove" type="button" :disabled="saving || readOnly" :aria-label="`移除${p.name}`" @click="emit('remove', p.productId)"><svg class="h-[9px] w-[9px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#create-close" /></svg></button></span></div><div class="selbar__actions"><button :class="analysisOpen ? 'btn-ghost-brand' : 'btn-brand'" type="button" :disabled="!selected.length || saving || analyzing" @click="confirm"><svg v-if="analysisOpen" class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="M20.2 11.6a8.2 8.2 0 1 0-2.6 6.2M20.4 4.6v6h-6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg>{{ analyzing ? '智能分析中…' : analysisOpen ? '重新选择商品' : '我选好了' }}</button><span v-if="analysisOpen" class="selbar__note">系统将根据选好商品进行智能分析，请避免频繁修改</span></div></div>
      </section>
      <section v-if="analysisOpen" ref="analysisPanel" class="panel analysis" aria-labelledby="panel-analysis">
        <div class="pt-8 sm:pt-9 lg:pt-10"><h2 class="analysis__title" id="panel-analysis">商品智能分析</h2></div>
        <div class="analysis-content" aria-live="polite" :aria-busy="analyzing">
          <p class="analysis-status" :class="{ 'analysis-status--error': analysisStatus === 'FAILED' }" role="status">{{ analysisMessage }}</p>
          <button v-if="!readOnly && analysisStatus === 'FAILED'" class="retry" type="button" :disabled="saving || readOnly" @click="emit('analyze')">重新分析</button>
          <div class="an-grid">
            <article v-for="card in analysisCards" :key="card.key" class="an-card">
              <div class="an-head"><span class="an-head__icon" :style="{ background: card.bg }" aria-hidden="true">{{ card.icon }}</span><div><h3 class="an-head__title">{{ card.title }}</h3><p class="an-head__sub">{{ card.sub }}</p></div></div>
              <div class="dimension-metric"><strong>{{ card.value }}</strong><span v-if="card.value !== '—'" class="dimension-unit">{{ card.unit }}</span><span v-if="card.isEstimate" class="dimension-estimate">估算</span></div>
              <p class="dimension-conclusion">{{ card.conclusion }}</p>
              <p v-if="card.method" class="dimension-method">分析依据：{{ card.method }}</p>
            </article>
          </div>
          <div v-if="analysisStatus === 'SUCCEEDED' && (conclusion || recommendations.length)" class="analysis-summary">
            <h3>整体分析与建议</h3><p v-if="conclusion">{{ conclusion }}</p>
            <ul v-if="recommendations.length"><li v-for="(item, i) in recommendations" :key="i">{{ item }}</li></ul>
          </div>
        </div>
      </section>
    </div></main>
    <div v-if="!embedded" class="actionbar"><div class="container-page actionbar__inner"><button class="btn-next" type="button" :disabled="!analysisOpen || analysisStatus !== 'SUCCEEDED' || !selected.length || saving" @click="emit('next')">{{ saving ? '处理中…' : '下一步' }}<svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><path d="M4.4 12h15.2M13.4 5.8 19.6 12l-6.2 6.2" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" /></svg></button><button class="btn-cancel" type="button" :disabled="saving || readOnly" @click="emit('cancel')">取消投放</button></div></div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import type { CreationProduct as ProductVo, ProductQuery, ProductAnalysis, ProductAnalysisDimensionKey } from '@/features/delivery/creationApi';
import ProductPrice from '@/components/ProductPrice.vue';
interface AccountOption { key: string; awemeName: string; awemeId: string }
const props = defineProps<{ products: ProductVo[]; selected: ProductVo[]; loaded: boolean; loadError: boolean; saving: boolean; account?: AccountOption; accounts: AccountOption[]; accountChips: string[]; total: number; analysisStatus: string; analysisError?: string; analysis?: ProductAnalysis; readOnly?: boolean; embedded?: boolean; initialQuery?: ProductQuery }>();
const emit = defineEmits<{ select: [product: ProductVo]; remove: [id: string]; account: [key: string]; retry: []; 'refresh-prices': []; next: []; cancel: []; analyze: []; query: [query: ProductQuery] }>();
const bannerChips = ['商城+搜索等全域投放', '智能生成素材', '智能出价', '实时监控+优化'];
const steps = ['选择商品', '设置投放目标', '定素材并投放', '查看投放结果'];
const categories = ['破圈尖货', '跑量优价', '新品', '搜索趋势品'];
const dimensionDefinitions: { key: ProductAnalysisDimensionKey; title: string; sub: string; unit: string; icon: string; bg: string }[] = [
  { key: 'competitor_count', title: '竞品数量', sub: '所选商品集的同类市场竞争情况', unit: '个', icon: '📊', bg: '#e8f2fe' },
  { key: 'search_heat_7d', title: '近7天搜索热度', sub: '搜索热度指数 · 0–100分', unit: '分', icon: '🔍', bg: '#fdf1e6' },
  { key: 'audience_match', title: '人群匹配度', sub: '所选商品集与目标人群的匹配情况', unit: '%', icon: '👥', bg: '#f1ecfd' },
  { key: 'estimated_roi', title: '预估ROI', sub: '投放前的收益效率预估', unit: '倍', icon: '📈', bg: '#e8f6ef' },
];
function displayMetric(value: unknown): string | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value.toLocaleString('zh-CN', { maximumFractionDigits: 2 });
  if (typeof value === 'string' && value.trim() && value.trim().toLowerCase() !== 'unknown') return value;
}
const analysisCards = computed(() => dimensionDefinitions.map(definition => {
  const dimension = props.analysisStatus === 'SUCCEEDED' ? props.analysis?.dimensions?.[definition.key] : undefined;
  const range = dimension?.value_range;
  const rangeText = Array.isArray(range) && range.length === 2 && range.every(value => typeof value === 'number' && Number.isFinite(value))
    ? `${displayMetric(range[0])}–${displayMetric(range[1])}` : undefined;
  const value = displayMetric(dimension?.value) ?? rangeText ?? '—';
  return { ...definition, value, isEstimate: dimension?.is_estimate === true,
    conclusion: dimension?.conclusion?.trim() || (analyzing.value ? '正在分析，请稍候…' : props.analysisStatus === 'SUCCEEDED' ? (value === '—' ? '暂无可靠数据' : '暂无进一步分析说明') : '等待分析结果'),
    method: dimension?.method?.trim(),
  };
}));
const query = ref(props.initialQuery?.keyword ?? ''), page = ref(props.initialQuery?.page ?? 1), size = ref(props.initialQuery?.pageSize ?? 30), jump = ref(String(page.value));
const broken = ref<Record<string, boolean>>({});
const analysisOpen = ref(false), listExpanded = ref(true), accountOpen = ref(false);
const analysisPanel = ref<HTMLElement>();
const filtered = computed(() => props.products);
const pages = computed(() => Math.max(1, Math.ceil(props.total / size.value)));
const visible = computed(() => filtered.value);

const tab = ref(props.initialQuery?.tab ?? 'ALL'), unpromoted = ref(props.initialQuery?.unpromoted ?? true);
const tabValues = ['ALL','BREAKTHROUGH_PRODUCT','GOOD_BOOST','NEW_PRODUCT','SEARCH_TREND'];
const analyzing = computed(() => ['QUEUED','RUNNING'].includes(props.analysisStatus));
const analysisMessage = computed(() => analyzing.value ? '正在分析所选商品，请稍候…' : props.analysisStatus === 'FAILED' ? props.analysisError || '商品分析失败，请重试' : props.analysisStatus === 'SUCCEEDED' ? `已完成 ${props.selected.length} 款商品的整体四维分析；估算指标以实际投放效果为准` : '等待商品分析开始');
const conclusion = computed(() => props.analysis?.overall_conclusion?.trim() || '');
const recommendations = computed(() => (props.analysis?.recommendations || []).filter(item => typeof item === 'string' && item.trim()));
watch(() => props.analysisStatus, status => { if (status !== 'NONE') analysisOpen.value = true; }, { immediate: true });
let queryTimer: ReturnType<typeof setTimeout> | undefined;
watch([query, page, size, tab, unpromoted], () => { if (props.readOnly) return; clearTimeout(queryTimer); queryTimer = setTimeout(() => emit('query', { page: page.value, pageSize: size.value, keyword: query.value, tab: tab.value, unpromoted: unpromoted.value && ['ALL','GOOD_BOOST'].includes(tab.value) }), 250); });
onUnmounted(() => clearTimeout(queryTimer));
watch([query, size, tab, unpromoted], () => { page.value = 1; });
watch(pages, n => { page.value = Math.min(page.value, n); });
watch(page, n => { jump.value = String(n); });
function jumpTo() { const n = Number(jump.value); page.value = Number.isInteger(n) && n > 0 ? Math.min(n, pages.value) : 1; jump.value = String(page.value); }
function isSelected(id: string) { return props.selected.some(p => p.productId === id); }
function choose(p: ProductVo) {
  if (props.readOnly || analysisOpen.value || props.saving || analyzing.value) return;
  emit('select', p);
}
async function confirm() {
  if (props.readOnly) return;
  if (analysisOpen.value) { analysisOpen.value = false; listExpanded.value = true; return; }
  analysisOpen.value = true; listExpanded.value = false; emit('analyze');
  if (analysisOpen.value) { await nextTick(); analysisPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
}
</script>

<style scoped src="./create-products.css"></style>
