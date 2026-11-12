<template>
  <div class="campaign-reference">
    <main class="ad-main mg-main">
      <div class="container-page">
        <router-link class="vback" to="/campaigns"
          ><ReferenceIcon name="arrow-left" />返回投放计划列表</router-link
        >
        <div class="vhead">
          <div class="vhead__l">
            <h1 class="vhead__t">
              {{ editing ? "修改投放计划" : "创建投放计划" }}
            </h1>
            <p class="vhead__meta">
              {{
                editing
                  ? form.name
                  : "设置推广商品、预算与投放目标，保存后进入素材准备"
              }}
            </p>
          </div>
          <div v-if="editing" class="vhead__ops">
            <router-link class="btn-prev" :to="`/campaigns/${route.params.id}`"
              >查看计划</router-link
            ><router-link
              class="btn-next-primary"
              :to="`/campaigns/${route.params.id}/analysis`"
              >投放效果分析</router-link
            >
          </div>
        </div>
        <div v-if="editing" class="fempty">
          <div>
            <p class="fempty__t">正在修改「{{ form.name || "加载中…" }}」</p>
            <p class="fempty__d">
              {{
                writable
                  ? "草稿设置将在保存后生效，完成素材准备后可提交审核。"
                  : "当前计划已提交平台，暂不支持在线修改；可复制设置创建新计划。"
              }}
            </p>
          </div>
        </div>
        <p v-if="failed" class="merr">加载失败，请返回列表重试。</p>
        <form
          v-if="!failed"
          class="mg-panel"
          novalidate
          @submit.prevent="save(false)"
        >
          <div class="mg-panel__pad">
            <div class="fsec">
              <div class="fsec__hd">
                <h2 class="fsec__t">基础信息</h2>
                <span class="fsec__d">计划名称可留空，系统将自动生成</span>
              </div>
              <div class="frow">
                <label class="frow__k" for="manage-name">计划名称</label>
                <div class="frow__v">
                  <input
                    id="manage-name"
                    v-model="form.name"
                    class="finput w-full max-w-[420px]"
                    maxlength="30"
                    :disabled="busy || !writable"
                    placeholder="留空自动生成，如 20261005_坤境_投放计划"
                  />
                  <p v-if="errors.name" class="merr">{{ errors.name }}</p>
                </div>
              </div>
              <div class="fgrid">
                <div class="frow">
                  <span class="frow__k">投放平台</span>
                  <div class="frow__v">
                    <input
                      class="finput"
                      disabled
                      value="抖音 · 巨量千川 · 商品全域投放"
                    />
                    <p class="frow__h">当前支持千川商品全域投放</p>
                  </div>
                </div>
                <div class="frow">
                  <label class="frow__k" for="manage-aweme">抖音号</label>
                  <div class="frow__v">
                    <select
                      id="manage-aweme"
                      v-model="awemeKey"
                      class="finput"
                      :disabled="editing || busy"
                      @change="changeAccount"
                    >
                      <option value="">请选择已授权抖音号</option>
                      <option v-for="a in awemes" :key="a.key" :value="a.key">
                        {{ a.name }}（ID：{{ a.awemeId }}）
                      </option>
                    </select>
                    <p class="frow__h">从已绑定的抖音号中选择</p>
                    <p v-if="errors.account" class="merr">
                      {{ errors.account }}
                    </p>
                  </div>
                </div>
              </div>
              <div class="frow">
                <span class="frow__k"><em>*</em>投放模式</span>
                <div class="frow__v">
                  <div class="fradio" role="radiogroup" aria-label="投放模式">
                    <button
                      v-for="m in modes"
                      :key="m.value"
                      class="frad"
                      type="button"
                      role="radio"
                      :aria-checked="form.mode === m.value"
                      :disabled="busy || !writable"
                      @click="form.mode = m.value"
                    >
                      <span class="frad__t">{{ m.name }}</span
                      ><span class="frad__d">{{ m.description }}</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <div class="fsec">
              <div class="fsec__hd">
                <h2 class="fsec__t">推广商品</h2>
                <span class="fsec__d"
                  >最多 10 款 · 全域投放将为每个商品探索流量</span
                >
              </div>
              <div class="frow">
                <span class="frow__k"><em>*</em>选择商品</span>
                <div class="frow__v">
                  <div v-if="picked.length" class="pgoods">
                    <article
                      v-for="p in picked"
                      :key="p.productId"
                      class="pgood"
                    >
                      <span class="pgood__thumb"
                        ><img
                          v-if="p.imageUrl"
                          :src="mediaUrl(p.imageUrl)"
                          :alt="p.name" /><ReferenceIcon
                          v-else
                          name="cart" /></span
                      ><span class="pgood__n">{{ p.name }}</span
                      ><span class="pgood__p"><ProductPrice :price-info="p.priceInfo" :price="p.price" :user-price="p.userPrice" /></span
                      ><button
                        v-if="!editing"
                        type="button"
                        aria-label="移除商品"
                        :disabled="busy"
                        @click="
                          picked = picked.filter(
                            (x) => x.productId !== p.productId,
                          )
                        "
                      >
                        <ReferenceIcon name="close" />
                      </button>
                    </article>
                  </div>
                  <div v-else class="fempty">
                    <div>
                      <p class="fempty__t">尚未选择推广商品</p>
                      <p class="fempty__d">
                        从抖音号商品库中选择 1–10
                        款商品，坤境将结合商品历史表现给出建议
                      </p>
                    </div>
                    <button
                      class="btn-next-primary"
                      type="button"
                      :disabled="!currentAccount || busy"
                      @click="openPicker"
                    >
                      去选择商品
                    </button>
                  </div>
                  <button
                    v-if="picked.length && !editing"
                    class="fadd"
                    type="button"
                    :disabled="busy"
                    @click="openPicker"
                  >
                    <ReferenceIcon name="plus" />添加商品
                  </button>
                  <p class="frow__h">
                    已选择 {{ picked.length }} 款商品{{
                      editing ? "；已绑定商品暂不支持在修改页替换" : ""
                    }}
                  </p>
                  <p v-if="errors.products" class="merr">
                    {{ errors.products }}
                  </p>
                </div>
              </div>
            </div>
            <div class="fsec">
              <div class="fsec__hd">
                <h2 class="fsec__t">投放设置</h2>
                <span class="fsec__d">设定每天的预算和净成交ROI目标</span>
              </div>
              <div class="fgrid">
                <div class="frow">
                  <label class="frow__k" for="manage-budget"
                    ><em>*</em>日预算</label
                  >
                  <div class="frow__v">
                    <div class="frow__v--inline">
                      <input
                        id="manage-budget"
                        v-model="form.budget"
                        class="finput finput--num"
                        :class="{ 'finput--error': errors.budget }"
                        inputmode="decimal"
                        :disabled="busy || !writable"
                        placeholder="请输入"
                      /><span class="funit">元 / 日</span>
                    </div>
                    <p class="frow__h">{{ form.mode === 'SMART_BID_CUSTOM' ? COST_DAILY_BUDGET_HINT : '预算需要在 300–999,999,999.99 之间' }}</p>
                    <p v-if="errors.budget" class="merr">{{ errors.budget }}</p>
                  </div>
                </div>
                <div class="frow">
                  <label class="frow__k" for="manage-roi"
                    ><em v-if="form.mode === 'SMART_BID_CUSTOM'">*</em
                    >净成交ROI目标</label
                  >
                  <div class="frow__v">
                    <input
                      id="manage-roi"
                      v-model="form.roi"
                      class="finput finput--num"
                      :class="{ 'finput--error': errors.roi }"
                      inputmode="decimal"
                      :disabled="
                        busy || !writable || form.mode !== 'SMART_BID_CUSTOM'
                      "
                      :placeholder="
                        form.mode === 'SMART_BID_CUSTOM'
                          ? '请输入'
                          : '放量投放由平台智能出价'
                      "
                    />
                    <p class="frow__h">支持范围 0.01–100，最多两位小数</p>
                    <p v-if="errors.roi" class="merr">{{ errors.roi }}</p>
                  </div>
                </div>
              </div>
              <div class="fgrid">
                <div class="frow">
                  <span class="frow__k">转化目标</span>
                  <div class="frow__v">
                    <input class="finput" value="净成交" disabled />
                    <p class="frow__h">净成交 = 整体成交 − 秒退订单成交额</p>
                  </div>
                </div>
                <div class="frow">
                  <span class="frow__k">成本保障</span>
                  <div class="frow__v">
                    <label class="fsw"
                      ><input
                        class="fsw__i"
                        type="checkbox"
                        disabled
                        :checked="form.mode === 'SMART_BID_CUSTOM'"
                      />按抖音平台成本保障规则执行</label
                    >
                  </div>
                </div>
              </div>
            </div>
            <div class="fsec">
              <div class="fsec__hd">
                <h2 class="fsec__t">投放时间</h2>
                <span class="fsec__d">当前支持即日起长期投放</span>
              </div>
              <div class="fgrid">
                <div class="frow">
                  <span class="frow__k">起始日期</span>
                  <div class="frow__v">
                    <input
                      class="finput"
                      type="date"
                      :value="form.start"
                      disabled
                    />
                    <p class="frow__h">采用当前计划起始日期</p>
                  </div>
                </div>
                <div class="frow">
                  <span class="frow__k">结束日期</span>
                  <div class="frow__v">
                    <input
                      class="finput"
                      :type="form.end ? 'date' : 'text'"
                      :value="form.end || '不限'"
                      disabled
                    />
                    <p class="frow__h">自定义投放日期暂未支持提交</p>
                  </div>
                </div>
              </div>
              <div class="frow">
                <span class="frow__k">投放时段</span>
                <div class="frow__v">
                  <input
                    class="finput"
                    value="全时段（00:00 - 24:00）"
                    disabled
                  />
                </div>
              </div>
            </div>
            <div class="fsec">
              <div class="fbar justify-end">
                <router-link class="btn-prev" to="/campaigns">取消</router-link
                ><button
                  class="btn-prev"
                  type="button"
                  :disabled="busy || !writable"
                  @click="save(true)"
                >
                  {{ busy ? "保存中…" : "保存草稿" }}</button
                ><button
                  class="btn-next-primary"
                  type="submit"
                  :disabled="busy || !writable"
                >
                  {{
                    busy
                      ? "提交中…"
                      : editing
                        ? "保存修改并准备素材"
                        : "提交并准备素材"
                  }}
                </button>
              </div>
            </div>
          </div>
        </form>
      </div>
    </main>
    <ReferenceFooter /><ReferenceModal
      v-if="pickerOpen"
      title="选择推广商品"
      :busy="busy"
      @close="pickerOpen = false"
      ><input
        v-model="search"
        class="finput"
        placeholder="搜索商品名称 / 商品ID"
        aria-label="搜索推广商品"
      />
      <p class="picker-count">已选 {{ selection.length }}/10 <button class="btn-prev" :disabled="loadingProducts" @click="loadProducts(true)">刷新价格</button></p>
      <div v-if="loadingProducts">正在加载商品库…</div>
      <div v-else-if="productsError" class="workspace-empty">
        <p>商品库加载失败</p>
        <button class="btn-prev" @click="loadProducts()">重试</button>
      </div>
      <div v-else class="picker-grid">
        <label
          v-for="p in filteredProducts"
          :key="p.productId"
          class="picker-item"
          ><input
            type="checkbox"
            :checked="selection.includes(p.productId)"
            :disabled="
              !selection.includes(p.productId) && selection.length >= 10
            "
            @change="toggleSelection(p.productId)"
          /><img v-if="p.imageUrl" :src="mediaUrl(p.imageUrl)" alt="" /><span
            >{{ p.name }}<small><ProductPrice :price-info="p.priceInfo" :price="p.price" :user-price="p.userPrice" /></small></span
          ></label
        >
        <p v-if="!filteredProducts.length">暂无匹配商品</p>
      </div>
      <template #footer
        ><button class="btn-prev" @click="pickerOpen = false">取消</button
        ><button
          class="btn-next-primary"
          :disabled="!selection.length || loadingProducts || productsError"
          @click="confirmProducts"
        >
          我选好了
        </button></template
      ></ReferenceModal
    >
  </div>
</template>
<script setup lang="ts">
import ProductPrice from "@/components/ProductPrice.vue";
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { listAccounts, type AccountVo } from "@/features/identity/oauthApi";
import {
  listProducts,
  selectProducts,
  type ProductVo,
} from "@/features/catalog/catalogApi";
import {
  createDraft,
  updateDraft,
  getPlan,
  listPlans,
  type PlanListVo,
} from "@/features/delivery/deliveryApi";
import { generateCreatives } from "@/features/creative/creativeApi";
import { useWizardStore } from "@/stores/wizard";
import { useUserStore } from "@/stores/user";
import ReferenceIcon from "./ReferenceIcon.vue";
import ReferenceFooter from "./ReferenceFooter.vue";
import ReferenceModal from "./ReferenceModal.vue";
import { amount, mediaUrl } from "./planPresentation";
import { COST_DAILY_BUDGET_MINIMUM, COST_DAILY_BUDGET_HINT } from '@/features/delivery/budgetValidation';
import "./reference.css";
const route = useRoute(),
  router = useRouter(),
  wizard = useWizardStore(),
  user = useUserStore(),
  editing = computed(() => !!route.params.id),
  busy = ref(false),
  failed = ref(false),
  loaded = ref(false),
  status = ref("DRAFT"),
  source = ref("LINGTOU"),
  draftId = ref(""),
  accounts = ref<AccountVo[]>([]),
  awemeKey = ref(""),
  picked = ref<ProductVo[]>([]),
  products = ref<ProductVo[]>([]),
  pickerOpen = ref(false),
  loadingProducts = ref(false),
  productsError = ref(false),
  selection = ref<string[]>([]),
  search = ref("");
const form = reactive({
    name: "",
    budget: "",
    roi: "",
    mode: "SMART_BID_CUSTOM",
    start: new Date().toLocaleDateString("sv-SE"),
    end: "",
  }),
  errors = reactive({
    name: "",
    account: "",
    products: "",
    budget: "",
    roi: "",
  });
const modes = [
  {
    value: "SMART_BID_CUSTOM",
    name: "控成本投放",
    description: "按净成交ROI目标控制成本，适合稳定获客与利润保障",
  },
  {
    value: "SMART_BID_CONSERVATIVE",
    name: "放量投放",
    description: "由系统智能出价，优先探索流量并扩大投放规模",
  },
];
const awemes = computed(() =>
  accounts.value
    .filter((a) => a.status === "ACTIVE")
    .flatMap((a) =>
      a.awemes.map((w) => ({
        ...w,
        accountId: a.accountId,
        key: `${a.accountId}::${w.awemeId}`,
      })),
    ),
);
const currentAccount = computed(() =>
  awemes.value.find((a) => a.key === awemeKey.value),
);
const writable = computed(
  () =>
    loaded.value &&
    user.trialActive &&
    source.value !== "EXTERNAL" &&
    (!editing.value || ["DRAFT", "REJECTED"].includes(status.value)),
);
const filteredProducts = computed(() =>
  products.value.filter((p) =>
    `${p.name} ${p.qcProductId}`
      .toLowerCase()
      .includes(search.value.toLowerCase()),
  ),
);
function changeAccount() {
  picked.value = [];
  products.value = [];
}
async function loadProducts(refreshPrices = false) {
  if (!currentAccount.value) return;
  loadingProducts.value = true;
  productsError.value = false;
  try {
    products.value = await listProducts(currentAccount.value.awemeId, undefined, refreshPrices);
  } catch {
    productsError.value = true;
  } finally {
    loadingProducts.value = false;
  }
}
function openPicker() {
  selection.value = picked.value.map((p) => p.productId);
  pickerOpen.value = true;
  void loadProducts();
}
function toggleSelection(id: string) {
  selection.value = selection.value.includes(id)
    ? selection.value.filter((x) => x !== id)
    : [...selection.value, id].slice(0, 10);
}
function confirmProducts() {
  picked.value = products.value.filter((p) =>
    selection.value.includes(p.productId),
  );
  pickerOpen.value = false;
}
function validNumber(s: string, min: number, max: number) {
  return (
    /^\d+(\.\d{1,2})?$/.test(s.trim()) && Number(s) >= min && Number(s) <= max
  );
}
function validate() {
  errors.name = form.name.trim().length > 30 ? "计划名称不能超过 30 字" : "";
  errors.account = currentAccount.value ? "" : "请选择已授权抖音号";
  errors.products =
    picked.value.length >= 1 && picked.value.length <= 10
      ? ""
      : "请选择 1–10 款商品";
  errors.budget = validNumber(form.budget, form.mode === 'SMART_BID_CUSTOM' ? COST_DAILY_BUDGET_MINIMUM : 300, 999999999.99)
    ? ""
    : `日预算需为 ${form.mode === 'SMART_BID_CUSTOM' ? COST_DAILY_BUDGET_MINIMUM : 300}–999,999,999.99 之间的数字，最多两位小数`;
  errors.roi =
    form.mode !== "SMART_BID_CUSTOM" || validNumber(form.roi, 0.01, 100)
      ? ""
      : "ROI目标需在 0.01–100 之间，最多两位小数";
  return !Object.values(errors).some(Boolean);
}
async function save(draftOnly: boolean) {
  if (busy.value || !writable.value || !validate()) return;
  busy.value = true;
  try {
    const account = currentAccount.value!;
    if (!form.name.trim())
      form.name =
        `${new Date().toLocaleDateString("sv-SE").replace(/-/g, "")}_坤境_投放计划_${new Date().toLocaleTimeString("zh-CN", { hour12: false })}`.slice(
          0,
          30,
        );
    await selectProducts(
      account.awemeId,
      picked.value.map((p) => p.qcProductId),
    );
    const body = {
      accountId: account.accountId,
      awemeId: account.awemeId,
      name: form.name.trim(),
      smartBidType: form.mode,
      dailyBudget: Number(form.budget),
      roi2Goal: form.mode === "SMART_BID_CUSTOM" ? Number(form.roi) : undefined,
      productIds: picked.value.map((p) => p.qcProductId),
    };
    if (draftId.value) await updateDraft(draftId.value, body);
    else draftId.value = (await createDraft(body)).plan.planId;
    if (draftOnly) {
      ElMessage.success("草稿已保存");
      await router.push(`/campaigns/${draftId.value}`);
      return;
    }
    wizard.reset();
    Object.assign(wizard, {
      accountId: account.accountId,
      awemeId: account.awemeId,
      awemeName: account.name,
      planId: draftId.value,
      planName: body.name,
      smartBidType: body.smartBidType,
      dailyBudget: body.dailyBudget,
      roi2Goal: body.roi2Goal,
      products: [...picked.value],
      productIds: picked.value.map((p) => p.productId),
    });
    const result = await generateCreatives(draftId.value, body.productIds);
    wizard.taskId = result.taskId;
    wizard.step = 3;
    await router.push("/campaigns/create");
  } catch {
    /* Keep form and draft ID so retry cannot create a duplicate draft. */
  } finally {
    busy.value = false;
  }
}
async function initialize() {
  loaded.value = false;
  failed.value = false;
  draftId.value = "";
  status.value = "DRAFT";
  source.value = "LINGTOU";
  picked.value = [];
  Object.assign(form, {
    name: "",
    budget: "",
    roi: "",
    mode: "SMART_BID_CUSTOM",
    start: new Date().toLocaleDateString("sv-SE"),
    end: "",
  });
  try {
    accounts.value = await listAccounts();
    const id = String(route.params.id || route.query.copy || "");
    if (id) {
      const base = await getPlan(id);
      let meta: PlanListVo | undefined;
      for (let page = 1; page <= 100; page++) {
        const rows = await listPlans(page, 100);
        meta = rows.records.find((p) => p.planId === id);
        if (meta || page * 100 >= rows.total) break;
      }
      const account = awemes.value.find((a) => a.awemeId === base.awemeId);
      awemeKey.value = account?.key || "";
      Object.assign(form, {
        name: editing.value
          ? base.name
          : (base.name.slice(0, 20) +
            "_副本_" +
            new Date().toLocaleTimeString("zh-CN", { hour12: false })).slice(0, 30),
        budget: String(base.budget ?? ""),
        roi: meta?.roi2Goal == null ? "" : String(meta.roi2Goal),
        mode: base.smartBidType || "SMART_BID_CUSTOM",
        start: meta?.startDate || form.start,
        end: meta?.endDate || "",
      });
      if (account) {
        await loadProducts();
        const ids = meta?.products?.map((p) => p.id) || [];
        picked.value = products.value.filter((p) =>
          ids.includes(p.qcProductId),
        );
      }
      if (editing.value) {
        draftId.value = id;
        status.value = base.status;
        source.value = base.source;
      }
    } else awemeKey.value = awemes.value[0]?.key || "";
  } catch {
    failed.value = true;
  } finally {
    loaded.value = true;
  }
}
watch(
  () => [route.params.id, route.query.copy],
  () => void initialize(),
  { immediate: true },
);
</script>
<style scoped>
.picker-count {
  margin: 16px 0;
  color: #7c7f99;
}
.picker-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  max-height: 45vh;
  overflow: auto;
}
.picker-item {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #e6e8f2;
  border-radius: 10px;
  padding: 12px;
  font-size: 13px;
}
.picker-item img {
  width: 44px;
  height: 44px;
  object-fit: cover;
  border-radius: 8px;
}
.picker-item small {
  display: block;
  color: #7c7f99;
}
.pgood {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
}
.pgood__thumb img {
  width: 44px;
  height: 44px;
  object-fit: cover;
  border-radius: 8px;
}
.pgood svg {
  width: 16px;
  height: 16px;
}
.pgood button {
  margin-left: auto;
  border: 0;
  background: none;
  color: #7c7f99;
}
.finput--error {
  border-color: #f54a45 !important;
}
.fbar {
  gap: 12px;
}
.finput:disabled {
  color: #7c7f99;
}
.vhead__meta {
  overflow-wrap: anywhere;
}
@media (max-width: 639px) {
  .fgrid {
    grid-template-columns: 1fr;
  }
  .frow {
    display: block;
  }
  .frow__k {
    display: block;
    margin-bottom: 10px;
  }
  .fradio,
  .picker-grid {
    grid-template-columns: 1fr;
  }
  .fbar {
    flex-wrap: wrap;
  }
  .fsec__hd {
    display: block;
  }
  .fsec__d {
    display: block;
    margin-top: 10px;
  }
}
</style>
