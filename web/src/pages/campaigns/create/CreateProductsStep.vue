<template>
  <div class="create-products" @keydown.esc="recommendOpen = false; accountOpen = false">
    <svg aria-hidden="true" class="icon-defs"><defs>
      <symbol id="create-check" viewBox="0 0 24 24"><path d="m5 12.6 4.6 4.4L19 7.4" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round" /></symbol>
      <symbol id="create-close" viewBox="0 0 24 24"><path d="m6.4 6.4 11.2 11.2M17.6 6.4 6.4 17.6" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /></symbol>
      <symbol id="create-caret" viewBox="0 0 24 24"><path d="m6 9.6 6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></symbol>
    </defs></svg>
    <section class="ad-banner" aria-label="投放计划概览">
      <div class="container-page ad-banner__inner">
        <div class="mt-[4px] min-w-0 banner-copy">
          <div class="ad-banner__head"><h1 class="ad-banner__title">灵投广告投放</h1><span class="ad-banner__tag"><svg class="h-[13px] w-[13px]" viewBox="0 0 24 24" aria-hidden="true"><path d="M2.6 19.4 9.4 7.6l4 6.5 2.1-3.2 5.9 8.5H2.6Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>抖音巨量千川 · 商品全域投放</span></div>
          <p class="ad-banner__sub">一次设置，广告自动出现在抖音商城、搜索、推荐等多个流量场景</p>
          <ul class="ad-banner__chips"><li v-for="text in bannerChips" :key="text" class="ad-chip"><svg class="ad-chip__ic" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.9" fill="none" stroke="currentColor" stroke-width="1.7" /><path d="m8.2 12.3 2.5 2.5 5.1-5.3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>{{ text }}</li></ul>
        </div>
        <div v-if="account" class="ad-account" @keydown.esc="accountOpen = false">
          <button class="ad-account__card w-full text-left" type="button" aria-haspopup="listbox" :disabled="saving" :aria-expanded="accountOpen" @click="accountOpen = !accountOpen">
            <span class="ad-account__name" :title="account.awemeName">{{ account.awemeName }}</span><span class="ad-account__id" :title="account.awemeId">抖音号ID：{{ account.awemeId }}</span>
            <span class="ad-account__btns"><span v-for="(chip, i) in accountChips" :key="chip" :class="i ? 'ad-account__btn-warn' : 'ad-account__btn-solid'">{{ chip }}</span></span>
            <span class="ad-account__chevron" aria-hidden="true"><svg class="h-4 w-4" viewBox="0 0 24 24"><use href="#create-caret" /></svg></span>
          </button>
          <div v-if="accountOpen" class="account-menu" role="listbox" aria-label="选择抖音号"><button v-for="opt in accounts" :key="opt.key" type="button" role="option" :aria-selected="opt.key === account.key" @click="accountOpen = false; emit('account', opt.key)">{{ opt.awemeName }}<small>{{ opt.awemeId }}</small></button></div>
          <p class="ad-account__hint">下拉切换需要投放广告的抖音号</p>
        </div>
        <select v-if="account" class="account-mobile" :disabled="saving" :value="account.key" aria-label="选择抖音号" @change="emit('account', ($event.target as HTMLSelectElement).value)"><option v-for="opt in accounts" :key="opt.key" :value="opt.key">{{ opt.awemeName }} · {{ opt.awemeId }}</option></select>
      </div>
    </section>
    <nav class="ad-stepper" aria-label="投放计划进度"><template v-for="(label, i) in steps" :key="label"><span class="step" :aria-current="i === 0 ? 'step' : undefined"><span class="step__dot"><svg v-if="i === 3" viewBox="0 0 24 24" aria-hidden="true"><use href="#create-check" /></svg><template v-else>{{ i + 1 }}</template></span><span class="step__label">{{ label }}</span></span><span v-if="i < 3" class="step__line" aria-hidden="true" /></template></nav>
    <main class="ad-main"><div class="container-page">
      <section class="panel" aria-labelledby="panel-goods">
        <div class="panel__pad">
          <div class="panel__head"><div class="min-w-0"><h2 class="panel__title" id="panel-goods">选择要投放广告的商品<span class="panel__badge">商品全域投放</span></h2><p class="panel__sub">系统将为选中商品自动匹配最佳投放策略</p></div><button v-if="analysisOpen" class="panel__toggle" type="button" :aria-expanded="listExpanded" aria-controls="goods-body" @click="listExpanded = !listExpanded">{{ listExpanded ? '收起' : '展开' }}<svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3.4 8.4 4.3L12 12 3.6 7.7 12 3.4Zm-8.4 8.9 8.4 4.3 8.4-4.3" fill="none" stroke="currentColor" stroke-width="1.7" /></svg></button></div>
          <p class="tip"><svg class="tip__icon h-[17px] w-[17px]" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10" fill="currentColor" /><path d="M12 6.4v7.2M12 17.1h.01" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" /></svg><span class="tip__text">建议相似ROI目标的商品集中在一条计划下投放，效果会更好。<span class="tip__accent">灵投将为您推荐最佳投放组合</span></span></p>
        </div>
        <div v-show="!analysisOpen || listExpanded" class="panel__body" id="goods-body">
          <div class="filters">
            <div class="seg" role="group" aria-label="商品筛选"><button class="seg__item" type="button" aria-pressed="true">全部商品</button><button v-for="name in categories" :key="name" class="seg__item" type="button" aria-pressed="false" disabled title="当前商品接口暂未提供分类数据">{{ name }}</button></div>
            <label class="check" title="当前商品接口暂未提供已投放状态"><input class="checkbox" type="checkbox" disabled /><span class="check__label">只看未投放商品</span></label>
            <div class="filters__right"><span class="filters__hint">（最多可选10个）</span><span class="search"><svg class="search__icon h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.4" fill="none" stroke="currentColor" stroke-width="1.9" /><path d="m15.6 15.6 4 4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" /></svg><input v-model="query" class="search__input" type="search" placeholder="搜索商品名称/ID" aria-label="搜索商品名称或ID" /></span></div>
          </div>
          <p v-if="loadError" class="pgrid__empty" role="alert">商品加载失败，请重试。<button class="retry" type="button" @click="emit('retry')">重新加载</button></p>
          <p v-else-if="!loaded" class="pgrid__empty" role="status">正在加载商品…</p>
          <div v-else-if="filtered.length" class="pgrid" role="listbox" aria-multiselectable="true" aria-label="可投放商品列表">
            <div v-for="p in visible" :key="p.productId" class="pcard" role="option" tabindex="0" :aria-selected="isSelected(p.productId)" :aria-checked="isSelected(p.productId)" :aria-label="p.name" @click="choose(p)" @keydown.enter.prevent="choose(p)" @keydown.space.prevent="choose(p)">
              <span class="pcard__thumb"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" alt="" @error="broken[p.productId] = true" /><svg v-else class="product-placeholder" viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="7" width="34" height="34" rx="5" /><circle cx="18" cy="18" r="3" /><path d="m8 34 10-10 8 8 6-6 9 9" /></svg></span><span class="pcard__check" aria-hidden="true" />
              <p class="pcard__title" :title="p.name">{{ p.name }}</p><div class="pcard__row"><span class="pcard__price"><span class="pcard__now">{{ yuan(p.userPrice ?? p.price) }}</span><span v-if="p.userPrice != null && p.price > p.userPrice" class="pcard__was">{{ yuan(p.price) }}</span></span><span class="pcard__stock" title="暂未提供库存数据">库存 —</span></div><div class="pcard__row2"><span class="pcard__tag">商品全域投放</span><span class="pcard__sales" title="暂未提供月销数据">月销 —</span></div>
            </div>
          </div>
          <p v-else class="pgrid__empty">{{ query.trim() ? '没有匹配的商品，换个关键词试试' : '该抖音号暂无可投放商品' }}</p>
          <div class="pager"><span class="tnum">共 {{ filtered.length }} 条记录</span><button class="pager__btn" type="button" :disabled="page <= 1" aria-label="上一页" @click="page--"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m14.4 6-6 6 6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span class="pager__num" aria-current="page">{{ page }}</span><button class="pager__btn" type="button" :disabled="page >= pages" aria-label="下一页" @click="page++"><svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="m9.6 6 6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" /></svg></button><span>跳转</span><input v-model="jump" class="pager__jump" type="text" inputmode="numeric" aria-label="跳转到第几页" @change="jumpTo" @keydown.enter="jumpTo" /><select v-model.number="size" class="pager__size" aria-label="每页条数"><option :value="10">10条/页</option><option :value="30">30条/页</option><option :value="50">50条/页</option></select></div>
        </div>
        <div class="selbar"><span class="selbar__count">已选 <b>{{ selected.length }}</b>/10：</span><div class="selbar__list"><span v-if="!selected.length" class="selbar__hint">请选择要投放广告的商品，建议3-8款，投放效果更好</span><span v-for="p in selected" :key="p.productId" class="sthumb" :title="p.name"><span class="sthumb__box"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" :alt="p.name" @error="broken[p.productId] = true" /><svg v-else class="product-placeholder" viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="7" width="34" height="34" rx="5" /><path d="m8 34 10-10 8 8 6-6 9 9" /></svg></span><button v-if="!analysisOpen" class="sthumb__remove" type="button" :disabled="saving" :aria-label="`移除${p.name}`" @click="emit('remove', p.productId)"><svg class="h-[9px] w-[9px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#create-close" /></svg></button></span></div><div class="selbar__actions"><button :class="analysisOpen ? 'btn-ghost-brand' : 'btn-brand'" type="button" :disabled="!selected.length || saving" @click="confirm"><svg v-if="analysisOpen" class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true"><path d="M20.2 11.6a8.2 8.2 0 1 0-2.6 6.2M20.4 4.6v6h-6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" /></svg>{{ analysisOpen ? '重新选择商品' : '我选好了' }}</button><span v-if="analysisOpen" class="selbar__note">系统将根据选好商品进行智能分析，请避免频繁修改</span></div></div>
      </section>
      <section v-if="analysisOpen" ref="analysisPanel" class="panel analysis" aria-labelledby="panel-analysis"><div class="pt-8 sm:pt-9 lg:pt-10"><h2 class="analysis__title" id="panel-analysis">商品智能分析</h2></div><div class="an-grid pb-10">
        <article v-for="(card, i) in analysisCards" :key="card.title" :class="['an-card', i === 4 ? 'an-card--12' : i % 2 ? 'an-card--7' : 'an-card--5']"><div class="an-head"><span class="an-head__icon" :style="{ background: card.bg }"><span class="emoji text-[15px]">{{ card.icon }}</span></span><div><h3 class="an-head__title">{{ card.title }}</h3><p class="an-head__sub">{{ card.sub }}</p></div></div><template v-if="i === 0"><div class="an-rows"><div v-for="(label, j) in ['到手价（已选商品均价）','商品成本','平台佣金','物流成本']" :key="label" class="an-row"><span class="an-row__k">{{ label }}</span><span class="an-row__v an-row__v--ink">{{ j ? '—' : meanPrice }}</span></div></div><div class="mini-grid"><div class="mini mini--good"><span class="mini__label">每单预估利润</span><span class="mini__value">—</span></div><div class="mini mini--info"><span class="mini__label">可承受广告成本</span><span class="mini__value">—</span></div></div></template><template v-else-if="i === 1"><div class="metric mt-4"><span class="metric__label">主图质量</span><span class="bar" /><span class="metric__value">—</span></div><p class="analysis-pending">暂无素材评估结果</p><div class="mini-grid"><div class="mini mini--warn"><span class="mini__label">素材制作难度</span><span class="mini__value">—</span></div><div class="mini mini--warn"><span class="mini__label">建议方向</span><span class="mini__value">—</span></div></div></template><template v-else-if="i === 2"><div class="stat-grid"><div v-for="label in ['同类投放数','平均出价','平均ROI']" :key="label" class="stat"><span class="stat__value">—</span><span class="stat__label">{{ label }}</span></div></div><p class="note-block">暂无市场竞争数据</p></template><template v-else-if="i === 3"><div class="analysis-pending">暂无销售趋势及投放时机评估结果</div><p class="note-block note-block--good">投放窗口待评估</p></template><template v-else><div class="score"><span class="score__badge">—</span><div><p class="score__title">已选择 {{ selected.length }} 款商品</p><p class="score__desc">{{ selected.map(p => p.name).join('、') }}</p><p class="score__desc">当前尚未接入智能评估结果，利润、评分及投放建议将在取得数据后展示。</p></div></div></template></article>
      </div></section>
    </div></main>
    <div v-if="recommendOpen && recommendations.length" class="rec-pop" role="dialog" aria-label="商品组合演示" @keydown.esc="recommendOpen = false"><button class="rec-pop__close" type="button" aria-label="关闭推荐提示" @click="recommendOpen = false"><svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#create-close" /></svg></button><div class="rec-pop__head"><p class="rec-pop__title">商品组合演示：<span class="rec-pop__desc">其他可投放商品</span></p></div><div class="rec-pop__thumbs"><span v-for="p in recommendations" :key="p.productId" class="sthumb" :title="p.name"><span class="sthumb__box"><img v-if="p.imageUrl && !broken[p.productId]" :src="p.imageUrl" :alt="p.name" @error="broken[p.productId] = true" /><svg v-else class="product-placeholder" viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="7" width="34" height="34" rx="5" /><path d="m8 34 10-10 8 8 6-6 9 9" /></svg></span></span></div><div class="rec-pop__reason"><p class="rec-pop__reason-text">智能推荐结果尚未接入，当前仅展示其他可选商品，不会自动加入已选。</p></div></div>
    <div class="actionbar"><div class="container-page actionbar__inner"><button class="btn-next" type="button" :disabled="!analysisOpen || !selected.length || saving" @click="emit('next')">{{ saving ? '处理中…' : '下一步' }}<svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><path d="M4.4 12h15.2M13.4 5.8 19.6 12l-6.2 6.2" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" /></svg></button><button class="btn-cancel" type="button" :disabled="saving" @click="emit('cancel')">取消投放</button></div></div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import type { ProductVo } from '@/features/catalog/catalogApi';
import { yuan } from '@/utils/labels';
interface AccountOption { key: string; awemeName: string; awemeId: string }
const props = defineProps<{ products: ProductVo[]; selected: ProductVo[]; loaded: boolean; loadError: boolean; saving: boolean; account?: AccountOption; accounts: AccountOption[]; accountChips: string[] }>();
const emit = defineEmits<{ select: [product: ProductVo]; remove: [id: string]; account: [key: string]; retry: []; next: []; cancel: [] }>();
const bannerChips = ['商城+搜索等全域投放', '智能生成素材', '智能出价', '实时监控+优化'];
const steps = ['选择商品', '设置投放目标', '定素材并投放', '查看投放结果'];
const categories = ['破圈尖货', '跑量优价', '新品', '搜索趋势品'];
const analysisCards = [
  { title: '利润空间评估', sub: '到手价 · 商品成本 · 平台佣金 · 物流成本', icon: '💰', bg: '#f1ecfd' },
  { title: '素材潜力评估', sub: '主图质量 · 卖点可提取度 · 竞品对比', icon: '🍞', bg: '#fdf1e6' },
  { title: '市场竞争评估', sub: '投放密度 · 平均出价 · 平均ROI', icon: '📊', bg: '#e8f2fe' },
  { title: '投放时机评估', sub: '季节性 · 促销节点 · 销售趋势', icon: '📅', bg: '#feeceb' },
  { title: '综合投放潜力', sub: '五维度加权评分', icon: '⭐', bg: '#fdf6e3' },
];
const query = ref(''), page = ref(1), size = ref(30), jump = ref('1');
const broken = ref<Record<string, boolean>>({});
const analysisOpen = ref(false), listExpanded = ref(true), accountOpen = ref(false), recommendOpen = ref(false), recommendShown = ref(false);
const analysisPanel = ref<HTMLElement>();
const filtered = computed(() => { const q = query.value.trim().toLowerCase(); return props.products.filter(p => !q || [p.name, p.productId, p.qcProductId].some(s => s?.toLowerCase().includes(q))); });
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / size.value)));
const visible = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value));
const recommendations = computed(() => props.products.filter(p => !isSelected(p.productId)).slice(0, 4));
const meanPrice = computed(() => props.selected.length ? yuan(props.selected.reduce((sum, p) => sum + (p.userPrice ?? p.price), 0) / props.selected.length) : '—');
watch([query, size], () => { page.value = 1; });
watch(pages, n => { page.value = Math.min(page.value, n); });
watch(page, n => { jump.value = String(n); });
function jumpTo() { const n = Number(jump.value); page.value = Number.isInteger(n) && n > 0 ? Math.min(n, pages.value) : 1; jump.value = String(page.value); }
function isSelected(id: string) { return props.selected.some(p => p.productId === id); }
function choose(p: ProductVo) {
  if (analysisOpen.value || props.saving) return;
  const firstChoice = !recommendShown.value && !isSelected(p.productId) && props.selected.length < 10;
  emit('select', p);
  if (firstChoice) { recommendShown.value = true; recommendOpen.value = true; }
}
async function confirm() {
  analysisOpen.value = !analysisOpen.value; listExpanded.value = !analysisOpen.value; recommendOpen.value = false;
  if (analysisOpen.value) { await nextTick(); analysisPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
}
</script>

<style scoped src="./create-products.css"></style>
