<template>
  <div class="wizard">
    <section class="banner" aria-label="投放计划概览">
      <div class="banner__copy">
        <div class="banner__head">
          <h1>灵投广告投放</h1>
          <span class="banner__tag">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.6 19.4 9.4 7.6l4 6.5 2.1-3.2 5.9 8.5H2.6Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>
            抖音巨量千川 · 商品全域投放
          </span>
        </div>
        <p class="banner__sub">一次设置，广告自动出现在抖音商城、搜索、推荐等多个流量场景</p>
        <ul class="banner__chips">
          <li v-for="item in BANNER_CHIPS" :key="item">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12.6 4.6 4.4L19 7.4" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round" /></svg>
            {{ item }}
          </li>
        </ul>
      </div>

      <div v-if="currentAweme" class="account-wide">
        <button type="button" class="account-card" :disabled="!canSwitch" @click="accountOpen = canSwitch && !accountOpen">
          <span class="account-card__name">{{ currentAweme.awemeName }}</span>
          <span class="account-card__id">抖音号ID：{{ currentAweme.awemeId }}</span>
          <span class="account-card__chips">
            <span v-for="chip in accountChips" :key="chip" class="account-chip">{{ chip }}</span>
          </span>
          <span v-if="canSwitch" class="account-card__chevron" aria-hidden="true">▾</span>
        </button>
        <ul v-if="accountOpen && canSwitch" class="account-menu" role="listbox">
          <li v-for="opt in awemeOptions" :key="opt.key">
            <button type="button" @click="chooseAweme(opt.key)">{{ opt.awemeName }} · {{ opt.awemeId }}</button>
          </li>
        </ul>
        <p v-if="canSwitch" class="account-card__hint">下拉切换需要投放广告的抖音号</p>
      </div>
      <div v-if="currentAweme" class="account-compact">
        <el-select v-model="awemeKey" :disabled="!canSwitch" placeholder="选择抖音号" @change="onAwemeChange">
          <el-option v-for="opt in awemeOptions" :key="opt.key" :label="`${opt.awemeName} · ${opt.awemeId}`" :value="opt.key" />
        </el-select>
      </div>
    </section>

    <nav class="stepper" aria-label="投放计划进度">
      <template v-for="(label, i) in STEP_LABELS" :key="label">
        <span class="step" :class="{ current: wizard.step === i + 1, done: wizard.step > i + 1 }" :aria-current="wizard.step === i + 1 ? 'step' : undefined">
          <span class="step__dot">
            <svg v-if="wizard.step > i + 1" viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12.6 4.6 4.4L19 7.4" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" /></svg>
            <template v-else>{{ i + 1 }}</template>
          </span>
          <span class="step__label">{{ label }}</span>
        </span>
        <span v-if="i < STEP_LABELS.length - 1" class="step__line" :class="{ on: wizard.step > i + 1 }" aria-hidden="true" />
      </template>
    </nav>

    <section v-if="wizard.step === 1" class="panel">
      <div class="panel__pad">
        <div class="panel__head">
          <div>
            <h2>选择要投放广告的商品<span class="panel__badge">商品全域投放</span></h2>
            <p class="panel__sub">系统将为选中商品自动匹配最佳投放策略</p>
          </div>
          <button v-if="analysisOpen" type="button" class="text-btn" @click="listExpanded = !listExpanded">
            {{ listExpanded ? "收起" : "展开" }}
          </button>
        </div>
        <p class="tip">建议相似ROI目标的商品集中在一条计划下投放，效果会更好。</p>
      </div>

      <div v-show="!analysisOpen || listExpanded" class="panel__body">
        <div class="filters">
          <div class="seg" role="group" aria-label="商品筛选">
            <button type="button" class="seg__item on" aria-pressed="true">全部商品</button>
            <button v-for="name in LOCKED_SEGS" :key="name" type="button" class="seg__item" disabled :title="SEG_NOTE">{{ name }}</button>
          </div>
          <p class="filters__note">{{ SEG_NOTE }}</p>
          <label class="check">
            <input type="checkbox" disabled />
            <span>只看未投放商品</span>
          </label>
          <p class="filters__note">暂无法区分已投放商品</p>
          <div class="filters__right">
            <span>最多可选 20 个</span>
            <el-input v-model="keyword" clearable placeholder="搜索商品名称/ID" @change="loadProducts" @keyup.enter="loadProducts" />
          </div>
        </div>

        <el-empty v-if="productsLoaded && products.length === 0" :description="keyword.trim() ? '没有匹配的商品' : '该抖音号暂无商品'" />
        <div v-else class="grid" role="listbox" aria-multiselectable="true" aria-label="可投放商品列表">
          <article
            v-for="p in products"
            :key="p.productId"
            :class="['prod', { on: wizard.productIds.includes(p.productId) }]"
            role="option"
            :aria-selected="wizard.productIds.includes(p.productId)"
            @click="toggleProduct(p)"
          >
            <img v-if="p.imageUrl" class="thumb" :src="p.imageUrl" alt="" />
            <div v-else class="thumb thumb--empty" aria-hidden="true" />
            <strong>{{ p.name }}</strong>
            <p class="price">{{ cardPrice(p) }}</p>
          </article>
        </div>
        <p v-if="productsLoaded" class="total">共 {{ products.length }} 条</p>
      </div>

      <div class="selbar">
        <span class="selbar__count">已选 <b>{{ wizard.productIds.length }}</b> / 20</span>
        <div class="selbar__list">
          <span v-if="wizard.products.length === 0" class="muted">请选择要投放广告的商品</span>
          <span v-for="p in wizard.products" :key="p.productId" class="chip">
            {{ p.name }}
            <button v-if="!analysisOpen" type="button" aria-label="移除" @click="removeProduct(p.productId)">×</button>
          </span>
        </div>
        <button type="button" class="btn-brand" :disabled="wizard.productIds.length === 0" @click="confirmGoods">
          {{ analysisOpen ? "重新选择商品" : "我选好了" }}
        </button>
      </div>

      <section v-if="analysisOpen" class="analysis" aria-label="商品智能分析">
        <h2>商品智能分析</h2>
        <div class="an-grid">
          <article v-for="title in ANALYSIS_TITLES" :key="title" class="an-card">
            <h3>{{ title }}</h3>
            <p>演示评估，暂无实测数据</p>
            <p>已选 {{ wizard.productIds.length }} 个：{{ selectedNames }}</p>
          </article>
        </div>
      </section>

      <div v-if="recommendOpen" class="recommend" role="dialog" aria-label="演示推荐">
        <div class="recommend__card">
          <button type="button" class="recommend__close" aria-label="关闭" @click="recommendOpen = false">×</button>
          <h3>演示推荐</h3>
          <p>推荐商品目标ROI和已选商品相似、商品类目相同，投放后流量互补</p>
          <div class="recommend__thumbs">
            <button v-for="p in recommendItems" :key="p.productId" type="button" class="recommend__thumb" @click.prevent>
              <img v-if="p.imageUrl" :src="p.imageUrl" alt="" />
              <span v-else class="thumb thumb--empty" />
              <span>{{ p.name }}</span>
            </button>
          </div>
          <p class="recommend__note">演示推荐，不会自动加入已选</p>
        </div>
      </div>

      <div class="footer">
        <button type="button" class="btn-ghost" @click="$router.push('/campaigns')">取消投放</button>
        <button type="button" class="btn-brand" :disabled="wizard.productIds.length === 0 || saving" @click="toStep2">下一步</button>
      </div>
    </section>

    <section v-else-if="wizard.step === 2" class="panel">
      <div class="panel__pad">
        <h2>设置投放目标</h2>
        <p class="panel__sub">系统智能托管模式，自动优化出价和投放节奏，你只需设定预算和时间</p>
        <p class="tip">涉及投放成本和目标效果，需重点关注</p>
        <p v-if="selectWarn" class="warn-banner">{{ selectWarn }}</p>
      </div>
      <div class="panel__body">
        <p class="field-label">投放方式</p>
        <div class="bid" role="radiogroup" aria-label="投放方式">
          <button type="button" :class="['bid-card', { on: wizard.smartBidType === 'SMART_BID_CUSTOM' }]" role="radio" :aria-checked="wizard.smartBidType === 'SMART_BID_CUSTOM'" @click="pickMode('SMART_BID_CUSTOM')">
            <span class="bid-card__row"><span class="radio" />控成本投放<span class="badge badge--rec">推荐</span></span>
            <span class="bid-card__sub">守住单价，少亏钱，适合测款</span>
          </button>
          <button type="button" :class="['bid-card', { on: wizard.smartBidType === 'SMART_BID_CONSERVATIVE' }]" role="radio" :aria-checked="wizard.smartBidType === 'SMART_BID_CONSERVATIVE'" @click="pickMode('SMART_BID_CONSERVATIVE')">
            <span class="bid-card__row"><span class="radio" />放量投放<span class="badge badge--fast">起量快</span></span>
            <span class="bid-card__sub">预算范围内，多推流量多接单，前期成本可能偏高</span>
          </button>
        </div>

        <div class="goal-grid">
          <div>
            <label for="in-budget">日预算</label>
            <div class="field-input">
              <span>¥</span>
              <input id="in-budget" v-model="budgetText" inputmode="decimal" autocomplete="off" />
              <span>元/天</span>
            </div>
            <p class="hint">已填入系统推荐目标值，可调整</p>
            <p v-if="budgetErrorText" class="err">{{ budgetErrorText }}</p>
            <p v-else-if="budgetWarnText" class="warn">{{ budgetWarnText }}</p>
          </div>

          <div v-if="wizard.smartBidType === 'SMART_BID_CUSTOM'">
            <label for="in-roi">净成交ROI目标</label>
            <div class="field-input">
              <input id="in-roi" v-model="roiText" inputmode="decimal" autocomplete="off" />
            </div>
            <p class="hint">净成交ROI=（整体成交金额-秒退订单成交额）/ 消耗</p>
            <p v-if="roiErrorText" class="err">{{ roiErrorText }}</p>
          </div>

          <div v-else class="estimate">
            <p class="estimate__title">连续投放7天预估带来 <span class="badge badge--demo">演示预估</span></p>
            <p><span>7日净成交ROI</span> <b>不低于 0.19</b> <span class="badge badge--demo">演示预估</span></p>
            <p><span>7日净成交订单量</span> <b>{{ orderLo }} ～ {{ orderHi }}单</b></p>
          </div>
        </div>

        <p class="field-label">投放时长</p>
        <div class="dur" role="radiogroup" aria-label="投放时长">
          <button type="button" :class="['dur__btn', { on: durationMode === 'long' }]" role="radio" :aria-checked="durationMode === 'long'" @click="durationMode = 'long'">
            <span class="badge badge--rec">推荐</span>
            长期投放
            <small>随时暂停</small>
          </button>
          <button type="button" :class="['dur__btn', { on: durationMode === 'custom' }]" role="radio" :aria-checked="durationMode === 'custom'" @click="durationMode = 'custom'">
            自定日期
          </button>
        </div>
        <div v-if="durationMode === 'custom'" class="dates">
          <label>开始日 <input v-model="customStart" type="date" /></label>
          <span>至</span>
          <label>结束日 <input v-model="customEnd" type="date" /></label>
        </div>
        <p class="hint">投放期间可随时暂停或调整，到期后自动停止</p>
        <p class="hint">建议至少投放 2 周及以上，覆盖完整的冷启动 → 稳定期 → 放量期</p>
        <p v-if="durationMode === 'custom'" class="err">演示计划本版不保存自定日期，请改回长期投放</p>
      </div>
      <div class="footer">
        <button type="button" class="btn-ghost" @click="backToProducts">上一步</button>
        <button type="button" class="btn-brand" :disabled="durationMode === 'custom' || saving" @click="toStep3">下一步</button>
      </div>
    </section>

    <section v-else-if="wizard.step === 3" class="page-card panel">
      <h2>确定投放素材</h2>
      <el-alert
        v-if="task?.status === 'RUNNING' || task?.status === 'PENDING'"
        type="info"
        show-icon
        :closable="false"
        title="标准短视频素材生成中，每 2 秒查询一次本系统任务，不会轮询千川。"
      />
      <el-alert v-else-if="task?.status === 'FAILED'" type="error" :title="task.errorMsg || '素材生成失败'" />
      <el-alert v-else-if="confirmed" type="success" title="素材包已确认，可填写计划名称并发布。" />
      <ul class="steps-log">
        <li v-for="(s, i) in task?.steps || []" :key="i">{{ s }}</li>
      </ul>
      <div v-if="task?.items?.length" class="preview-grid">
        <article v-for="item in task.items" :key="item.productId" class="preview-card">
          <video v-if="item.playUrl" :src="abs(item.playUrl)" controls poster="" />
          <img v-else-if="item.posterUrl" :src="abs(item.posterUrl)" alt="" />
          <p>{{ item.productId }}</p>
          <el-upload :show-file-list="false" :auto-upload="false" accept="video/*,image/*" :on-change="pickReplace(item.productId)">
            <el-button size="small">替换素材</el-button>
          </el-upload>
        </article>
      </div>
      <el-form label-position="top">
        <el-form-item label="投放计划名称（1-30 字）">
          <el-input v-model="wizard.planName" maxlength="30" show-word-limit />
        </el-form-item>
      </el-form>
      <div class="footer">
        <el-button @click="backToGoal">上一步</el-button>
        <el-button v-if="task?.packageId && !confirmed" type="primary" :loading="saving" @click="onConfirm">确认素材</el-button>
        <el-button v-else type="primary" :disabled="!confirmed" :loading="saving" @click="onPublish">发布投放</el-button>
      </div>
    </section>

    <section v-else class="page-card panel result">
      <template v-if="wizard.publishStatus === 'PENDING_AUDIT'">
        <h2>计划已提交，正在审核</h2>
        <p>这是演示计划</p>
      </template>
      <template v-else-if="wizard.publishStatus === 'REJECTED'">
        <h2>审核未通过</h2>
        <p>请回到列表查看拒审原因后重新创建。</p>
      </template>
      <template v-else>
        <h2>演示计划已发布</h2>
        <p>投放期间灵投只盯盘告警，不会自动改价。</p>
      </template>
      <p>{{ planIdLabel }}{{ planIdValue }}</p>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="投放方式">{{ BID_TYPE[wizard.smartBidType] }}</el-descriptions-item>
        <el-descriptions-item label="日预算">{{ yuan(wizard.dailyBudget) }}</el-descriptions-item>
        <el-descriptions-item label="计划名称">{{ wizard.planName }}</el-descriptions-item>
        <el-descriptions-item label="投放商品">{{ wizard.productIds.length }} 个</el-descriptions-item>
      </el-descriptions>
      <div class="footer">
        <el-button @click="again">再建一条计划</el-button>
        <el-button type="primary" @click="$router.push('/campaigns')">查看所有广告计划</el-button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { listAccounts, type AwemeVo } from "@/features/identity/oauthApi";
import { listProducts, selectProducts, type ProductVo } from "@/features/catalog/catalogApi";
import { confirmPackage, generateCreatives, getCreativeTask, replaceItem, type TaskVo } from "@/features/creative/creativeApi";
import { createDraft, publishPlan, updateDraft } from "@/features/delivery/deliveryApi";
import { useWizardStore } from "@/stores/wizard";
import { BID_TYPE, yuan } from "@/utils/labels";
import "./create/create-theme.css";

const STEP_LABELS = ["选择商品", "设置投放目标", "定素材并投放", "查看投放结果"];
const BANNER_CHIPS = ["商城+搜索等全域投放", "智能生成素材", "智能出价", "实时监控+优化"];
const LOCKED_SEGS = ["破圈尖货", "跑量优价", "新品", "搜索趋势品"];
const SEG_NOTE = "当前商品数据没有该分类";
const ANALYSIS_TITLES = ["利润空间评估", "素材潜力评估", "市场竞争评估", "投放时机评估", "综合投放潜力"];
const SCENE_CHIPS = ["自运营", "合作达人", "合作机构", "联盟"];

interface AwemeOpt {
  key: string;
  accountId: string;
  awemeId: string;
  awemeName: string;
  isPrimary: boolean;
  type: string;
  sceneHint?: string;
}

const wizard = useWizardStore();
const accounts = ref<Awaited<ReturnType<typeof listAccounts>>>([]);
const products = ref<ProductVo[]>([]);
const productsLoaded = ref(false);
const keyword = ref("");
const awemeKey = ref("");
const saving = ref(false);
const task = ref<TaskVo | null>(null);
const confirmed = ref(false);
const analysisOpen = ref(false);
const listExpanded = ref(true);
const recommendOpen = ref(false);
const recommendShown = ref(false);
const recommendItems = ref<ProductVo[]>([]);
const accountOpen = ref(false);
const selectWarn = ref("");
const budgetText = ref("600");
const roiText = ref("5.12");
const durationMode = ref<"long" | "custom">("long");
const customStart = ref(shiftDate(1));
const customEnd = ref(shiftDate(31));
let pollTimer: number | undefined;

const awemeOptions = computed<AwemeOpt[]>(() =>
  accounts.value.flatMap((a) =>
    (a.awemes || []).map((aw: AwemeVo) => ({
      key: `${a.accountId}::${aw.awemeId}`,
      accountId: a.accountId,
      awemeId: aw.awemeId,
      awemeName: aw.name,
      isPrimary: aw.isPrimary,
      type: aw.type,
      sceneHint: aw.sceneHint,
    })),
  ),
);

const currentAweme = computed(() => awemeOptions.value.find((o) => o.key === awemeKey.value));
const canSwitch = computed(() => wizard.step <= 2);
const accountChips = computed(() => {
  const aw = currentAweme.value;
  if (!aw) return [];
  const chips: string[] = [];
  if (aw.isPrimary || aw.type === "OFFICIAL") chips.push("官方账号");
  if (aw.sceneHint && SCENE_CHIPS.includes(aw.sceneHint)) chips.push(aw.sceneHint);
  return chips;
});
const selectedNames = computed(() => wizard.products.map((p) => p.name).join("、"));
const budgetErrorText = computed(() => budgetError(budgetText.value));
const budgetWarnText = computed(() => budgetWarn(budgetText.value));
const roiErrorText = computed(() => (wizard.smartBidType === "SMART_BID_CUSTOM" ? roiError(roiText.value) : ""));
const estimateBudget = computed(() => {
  const n = Number(budgetText.value);
  return Number.isFinite(n) && n > 0 ? n : 0;
});
const orderLo = computed(() => Math.max(1, Math.round(estimateBudget.value * 0.015)));
const orderHi = computed(() => Math.max(orderLo.value + 1, Math.round(estimateBudget.value * 0.02)));
const planIdLabel = computed(() => {
  const id = wizard.qcPlanId;
  if (!id) return "";
  return id.startsWith("mock_") ? "演示计划编号：" : "计划编号：";
});
const planIdValue = computed(() => wizard.qcPlanId || "生成中");

watch(
  () => wizard.step,
  (step, prev) => {
    if (prev === 3 && step !== 3) stopPoll();
    if (step > 2) accountOpen.value = false;
  },
);

function shiftDate(days: number) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function budgetError(raw: string) {
  const text = raw.trim();
  if (!text) return "请输入日预算金额";
  const n = Number(text);
  if (!Number.isFinite(n) || n <= 0) return "日预算需为大于 0 的数字";
  if (n > 100000) return "日预算超出单计划上限（¥100,000/天）";
  return "";
}

function budgetWarn(raw: string) {
  const n = Number(raw.trim());
  if (Number.isFinite(n) && n > 0 && n < 100) return "低于系统建议值 ¥100/天，冷启动期可能起量困难";
  return "";
}

function roiError(raw: string) {
  const text = raw.trim();
  if (!text) return "请输入净成交ROI目标";
  const n = Number(text);
  if (!Number.isFinite(n) || n <= 0) return "净成交ROI目标需为大于 0 的数字";
  if (n < 0.1 || n > 50) return "净成交ROI目标建议在 0.1 ~ 50 之间";
  return "";
}

function cardPrice(p: ProductVo) {
  return yuan(p.userPrice != null ? p.userPrice : p.price);
}

function defaultName() {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_商品全域投放_${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`.slice(0, 30);
}

function chooseAweme(key: string) {
  accountOpen.value = false;
  awemeKey.value = key;
  onAwemeChange(key);
}

function onAwemeChange(key: string) {
  if (wizard.step > 2) return;
  const opt = awemeOptions.value.find((o) => o.key === key);
  if (!opt) return;
  const changed = wizard.awemeId !== opt.awemeId || wizard.accountId !== opt.accountId;
  wizard.accountId = opt.accountId;
  wizard.awemeId = opt.awemeId;
  wizard.awemeName = opt.awemeName;
  if (!changed) return;
  stopPoll();
  wizard.productIds = [];
  wizard.products = [];
  wizard.planId = "";
  wizard.taskId = "";
  wizard.packageId = "";
  wizard.step = 1;
  analysisOpen.value = false;
  listExpanded.value = true;
  recommendOpen.value = false;
  recommendShown.value = false;
  task.value = null;
  confirmed.value = false;
  selectWarn.value = "";
  loadProducts();
}

async function loadProducts() {
  if (!wizard.awemeId) return;
  productsLoaded.value = false;
  try {
    products.value = await listProducts(wizard.awemeId, keyword.value.trim() || undefined);
  } finally {
    productsLoaded.value = true;
  }
}

function removeProduct(productId: string) {
  wizard.productIds = wizard.productIds.filter((id) => id !== productId);
  wizard.products = wizard.products.filter((p) => p.productId !== productId);
}

function toggleProduct(p: ProductVo) {
  const i = wizard.productIds.indexOf(p.productId);
  if (i >= 0) {
    removeProduct(p.productId);
    return;
  }
  if (wizard.productIds.length >= 20) {
    ElMessage.warning("最多选择 20 个商品");
    return;
  }
  wizard.productIds.push(p.productId);
  wizard.products.push(p);
  if (wizard.productIds.length === 1 && !recommendShown.value) {
    recommendShown.value = true;
    const others = products.value.filter((x) => x.productId !== p.productId);
    if (others.length > 0) {
      recommendItems.value = others.slice(0, 4);
      recommendOpen.value = true;
    }
  }
}

function confirmGoods() {
  if (wizard.productIds.length === 0) return;
  if (analysisOpen.value) {
    analysisOpen.value = false;
    listExpanded.value = true;
    return;
  }
  analysisOpen.value = true;
  listExpanded.value = false;
}

async function toStep2() {
  if (wizard.productIds.length === 0) return;
  saving.value = true;
  try {
    const res = await selectProducts(wizard.awemeId, [...wizard.productIds]);
    selectWarn.value = res.warn || "";
    if (res.warn) ElMessage.warning(res.warn);
    wizard.step = 2;
  } catch {
    /* 接口已提示，留在本步 */
  } finally {
    saving.value = false;
  }
}

function backToProducts() {
  analysisOpen.value = false;
  listExpanded.value = true;
  wizard.step = 1;
}

function pickMode(mode: "SMART_BID_CUSTOM" | "SMART_BID_CONSERVATIVE") {
  if (wizard.smartBidType === mode) return;
  wizard.smartBidType = mode;
  if (mode === "SMART_BID_CUSTOM") {
    wizard.dailyBudget = 600;
    wizard.roi2Goal = 5.12;
    budgetText.value = "600";
    roiText.value = "5.12";
    ElMessage.success("已切换为控成本投放，日预算与目标已回填系统推荐值");
  } else {
    wizard.dailyBudget = 200;
    budgetText.value = "200";
    ElMessage.success("已切换为放量投放，日预算与目标已回填系统推荐值");
  }
}

function draftBody() {
  return {
    accountId: wizard.accountId,
    awemeId: wizard.awemeId,
    name: wizard.planName || defaultName(),
    smartBidType: wizard.smartBidType,
    dailyBudget: wizard.dailyBudget,
    roi2Goal: wizard.smartBidType === "SMART_BID_CUSTOM" ? wizard.roi2Goal : undefined,
    productIds: [...wizard.productIds],
    packageId: wizard.packageId || undefined,
  };
}

async function toStep3() {
  if (durationMode.value === "custom") return;
  if (budgetErrorText.value) return;
  if (wizard.smartBidType === "SMART_BID_CUSTOM" && roiErrorText.value) return;
  wizard.dailyBudget = Number(budgetText.value);
  if (wizard.smartBidType === "SMART_BID_CUSTOM") wizard.roi2Goal = Number(roiText.value);
  saving.value = true;
  try {
    if (!wizard.planName) wizard.planName = defaultName();
    const res = await createDraft(draftBody());
    wizard.planId = res.plan.planId;
    wizard.step = 3;
    await startGenerate();
  } catch {
    /* 接口已提示，留在本步。不回写草稿里的预算建议 */
  } finally {
    saving.value = false;
  }
}

function backToGoal() {
  stopPoll();
  wizard.step = 2;
}

async function startGenerate() {
  const res = await generateCreatives(wizard.planId, [...wizard.productIds]);
  wizard.taskId = res.taskId;
  stopPoll();
  pollTimer = window.setInterval(pollTask, 2000);
  await pollTask();
}

async function pollTask() {
  if (!wizard.taskId) return;
  const next = await getCreativeTask(wizard.taskId);
  task.value = next;
  if (next.packageId) wizard.packageId = next.packageId;
  if (next.status === "SUCCEEDED" || next.status === "FAILED") stopPoll();
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

function abs(url: string) {
  if (!url) return url;
  if (url.startsWith("http") || url.startsWith("blob:")) return url;
  return (import.meta.env.VITE_API_BASE || "/lynx-ad") + url.replace(/^\/lynx-ad/, "");
}

function pickReplace(productId: string) {
  return (file: { raw?: File }) => onReplace(productId, file.raw);
}

async function onReplace(productId: string, file?: File) {
  if (!file || !wizard.packageId) return;
  saving.value = true;
  try {
    await replaceItem(wizard.packageId, productId, file);
    await pollTask();
    ElMessage.success("素材已替换");
  } finally {
    saving.value = false;
  }
}

async function onConfirm() {
  if (!wizard.packageId) return;
  saving.value = true;
  try {
    await confirmPackage(wizard.packageId);
    confirmed.value = true;
    ElMessage.success("素材已确认");
  } finally {
    saving.value = false;
  }
}

async function onPublish() {
  if (wizard.planName.length < 1 || wizard.planName.length > 30) {
    ElMessage.warning("计划名称须为 1–30 字");
    return;
  }
  saving.value = true;
  try {
    await updateDraft(wizard.planId, draftBody());
    const key = crypto.randomUUID();
    const res = await publishPlan(wizard.planId, key);
    wizard.qcPlanId = res.qcPlanId;
    wizard.publishStatus = res.status;
    stopPoll();
    wizard.step = 4;
  } finally {
    saving.value = false;
  }
}

function again() {
  stopPoll();
  wizard.reset();
  confirmed.value = false;
  task.value = null;
  analysisOpen.value = false;
  listExpanded.value = true;
  recommendOpen.value = false;
  recommendShown.value = false;
  durationMode.value = "long";
  budgetText.value = "600";
  roiText.value = "5.12";
  selectWarn.value = "";
  accountOpen.value = false;
  if (awemeOptions.value[0]) {
    awemeKey.value = awemeOptions.value[0].key;
    onAwemeChange(awemeKey.value);
  }
}

onMounted(async () => {
  accounts.value = await listAccounts();
  const first = awemeOptions.value[0];
  if (first) {
    awemeKey.value = first.key;
    onAwemeChange(first.key);
  }
});

onUnmounted(stopPoll);
</script>

<style scoped>
.wizard {
  background: var(--create-page);
  margin: -20px -24px -40px;
  padding: 20px 24px 48px;
  color: #1a1b2e;
}
.banner {
  background: var(--create-banner);
  color: #fff;
  border-radius: 20px;
  padding: 28px 32px;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
}
.banner h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}
.banner__head,
.banner__tag,
.banner__chips,
.banner__chips li,
.account-card__chips {
  display: flex;
  align-items: center;
  gap: 8px;
}
.banner__head {
  flex-wrap: wrap;
}
.banner__tag {
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 13px;
}
.banner__tag svg,
.banner__chips svg,
.step__dot svg {
  width: 14px;
  height: 14px;
}
.banner__sub {
  margin: 10px 0;
  opacity: 0.92;
}
.banner__chips {
  list-style: none;
  padding: 0;
  margin: 0;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 14px;
}
.account-wide {
  position: relative;
  min-width: 280px;
}
.account-compact {
  display: none;
  min-width: 220px;
}
.account-card {
  width: 100%;
  text-align: left;
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 16px;
  padding: 14px 16px;
  cursor: pointer;
}
.account-card:disabled {
  cursor: default;
}
.account-card__name {
  display: block;
  font-weight: 700;
}
.account-card__id,
.account-card__hint {
  display: block;
  margin-top: 4px;
  font-size: 13px;
  opacity: 0.9;
}
.account-card__hint {
  margin: 8px 4px 0;
}
.account-chip {
  display: inline-block;
  margin-top: 8px;
  margin-right: 6px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
}
.account-menu {
  position: absolute;
  z-index: 5;
  left: 0;
  right: 0;
  margin: 6px 0 0;
  padding: 6px;
  list-style: none;
  background: #fff;
  color: #1a1b2e;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(26, 27, 46, 0.12);
}
.account-menu button {
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 8px;
  cursor: pointer;
}
.stepper {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 0;
  background: #fff;
  border-radius: 16px;
  padding: 14px 18px;
}
.step {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #7c7f99;
  padding: 6px 10px;
}
.step.current {
  background: #eeeefe;
  color: var(--create-primary);
  border-radius: 999px;
}
.step__dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #e6e8f2;
  font-size: 12px;
}
.step.current .step__dot,
.step.done .step__dot {
  background: var(--create-primary);
  color: #fff;
}
.step__line {
  flex: 1;
  height: 1px;
  background: #e6e8f2;
}
.step__line.on {
  background: var(--create-primary);
}
.panel {
  background: #fff;
  border-radius: 24px;
  padding: 8px 8px 20px;
}
.panel__pad,
.panel__body,
.selbar,
.analysis,
.footer {
  padding: 16px 20px 0;
}
.panel h2 {
  margin: 0;
  font-size: 20px;
}
.panel__badge {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--create-primary);
  background: #eeeefe;
  border-radius: 999px;
  padding: 2px 8px;
  vertical-align: middle;
}
.panel__sub,
.hint,
.muted,
.filters__note,
.total {
  color: #7c7f99;
  font-size: 13px;
}
.panel__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.tip,
.warn-banner {
  background: #ebecff;
  color: #4e5069;
  border-radius: 12px;
  padding: 10px 12px;
}
.warn-banner,
.warn {
  color: #c2522a;
}
.err {
  color: #e5484d;
  margin: 6px 0 0;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
}
.seg {
  display: flex;
  gap: 6px;
  background: #f4f5f9;
  border-radius: 12px;
  padding: 4px;
}
.seg__item {
  border: 0;
  background: transparent;
  border-radius: 8px;
  padding: 6px 10px;
  color: #4e5069;
}
.seg__item.on {
  background: #fff;
  color: var(--create-primary);
}
.seg__item:disabled {
  color: #c4c5cd;
  cursor: not-allowed;
}
.check {
  display: flex;
  gap: 6px;
  align-items: center;
  color: #7c7f99;
}
.filters__right {
  margin-left: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}
.grid,
.an-grid,
.preview-grid,
.goal-grid,
.bid {
  display: grid;
  gap: 12px;
}
.grid,
.preview-grid {
  grid-template-columns: repeat(4, 1fr);
  margin-top: 16px;
}
.prod,
.an-card,
.bid-card,
.dur__btn {
  border: 1px solid #eeeff4;
  border-radius: 14px;
  background: #fff;
  text-align: left;
}
.prod {
  padding: 10px;
  cursor: pointer;
}
.prod.on,
.bid-card.on,
.dur__btn.on {
  border-color: var(--create-primary);
  background: #f7f8fe;
  box-shadow: 0 0 0 1px var(--create-primary);
}
.thumb {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 10px;
  background: #f7f8fa;
  display: block;
  margin-bottom: 8px;
}
.price {
  color: #c2522a;
  font-weight: 700;
  margin: 4px 0 0;
}
.selbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.selbar__list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  flex: 1;
}
.chip {
  background: #eeeefe;
  color: var(--create-primary);
  border-radius: 999px;
  padding: 4px 8px;
}
.chip button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.an-grid {
  grid-template-columns: repeat(2, 1fr);
  margin-top: 12px;
}
.an-card {
  padding: 16px;
}
.an-card h3 {
  margin: 0 0 8px;
}
.recommend {
  position: fixed;
  inset: 0;
  background: rgba(26, 27, 46, 0.28);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
}
.recommend__card {
  width: min(520px, calc(100% - 32px));
  background: #fff;
  border-radius: 20px;
  padding: 20px;
  position: relative;
}
.recommend__close {
  position: absolute;
  top: 12px;
  right: 12px;
  border: 0;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
}
.recommend__thumbs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.recommend__thumb {
  border: 0;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: default;
}
.recommend__thumb img,
.recommend__thumb .thumb {
  height: 72px;
}
.recommend__note {
  color: var(--create-primary);
  font-weight: 600;
}
.footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}
.btn-brand,
.btn-ghost {
  border-radius: 10px;
  padding: 10px 18px;
  cursor: pointer;
}
.btn-brand {
  border: 0;
  background: var(--create-primary);
  color: #fff;
}
.btn-brand:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.btn-ghost,
.text-btn {
  border: 1px solid #e6e8f2;
  background: #fff;
  color: #4e5069;
}
.bid,
.goal-grid {
  grid-template-columns: 1fr 1fr;
  margin-top: 12px;
}
.bid-card,
.dur__btn {
  padding: 14px;
  cursor: pointer;
}
.bid-card__row,
.dur {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bid-card__sub {
  display: block;
  margin-top: 8px;
  color: #7c7f99;
  font-size: 13px;
}
.radio {
  width: 14px;
  height: 14px;
  border: 2px solid #c4c5cd;
  border-radius: 50%;
  display: inline-block;
}
.bid-card.on .radio {
  border-color: var(--create-primary);
  box-shadow: inset 0 0 0 3px var(--create-primary);
}
.badge {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
}
.badge--rec {
  background: #eeeefe;
  color: var(--create-primary);
}
.badge--fast {
  background: #fff3e4;
  color: #c2522a;
}
.badge--demo {
  background: #ebecff;
  color: var(--create-primary);
}
.field-input {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e6e8f2;
  border-radius: 10px;
  padding: 8px 12px;
  margin-top: 8px;
}
.field-input input {
  border: 0;
  outline: none;
  flex: 1;
  font-size: 16px;
}
.field-label {
  margin: 18px 0 0;
  font-weight: 600;
}
.estimate {
  background: #f7f8fe;
  border-radius: 14px;
  padding: 14px;
}
.dates {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 12px;
}
.dur {
  margin-top: 8px;
}
.dur__btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.dur__btn small {
  color: #7c7f99;
}
.steps-log {
  color: #6b7280;
}
.preview-card video,
.preview-card img {
  width: 100%;
  height: 160px;
  object-fit: cover;
  border-radius: 8px;
  background: #f3f4f6;
}
@media (max-width: 1279px) {
  .account-wide {
    display: none;
  }
  .account-compact {
    display: block;
  }
  .banner {
    flex-direction: column;
  }
}
@media (max-width: 900px) {
  .grid,
  .bid,
  .goal-grid,
  .an-grid,
  .preview-grid {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 639px) {
  .step__label {
    display: none;
  }
  .step.current .step__label {
    display: inline;
  }
  .grid,
  .an-grid,
  .preview-grid,
  .recommend__thumbs {
    grid-template-columns: 1fr;
  }
}
</style>
