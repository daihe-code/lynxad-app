<template>
  <div class="billing">
    <section class="current" :class="{ trial: !sub?.hasPaidSubscription }">
      <div>
        <p class="eyebrow">CURRENT PLAN</p>
        <h2>{{ sub?.planName || "专业版" }}</h2>
        <p class="price">¥{{ Math.round(sub?.listPriceYuan || 799) }}/月</p>
        <p v-if="!sub?.hasPaidSubscription" class="meta">
          {{ trialLabel }} · 剩余 {{ sub?.remainingDays ?? user.me?.remainingTrialDays ?? 0 }} 天
        </p>
        <template v-else>
          <p v-if="sub.discountLabel" class="meta">{{ sub.discountLabel }}</p>
          <p class="meta">
            计费周期 {{ formatDate(sub.periodStart) }} — {{ formatDate(sub.periodEnd) }} · 剩余
            {{ sub.remainingDays }} 天
          </p>
          <p class="meta">
            自动续费{{ sub.autoRenew ? "已开启" : "已关闭" }} · 支付方式微信支付
          </p>
        </template>
      </div>
      <div class="ops">
        <el-button v-if="sub?.hasPaidSubscription" @click="toggleRenew">{{
          sub.autoRenew ? "关闭自动续费" : "开启自动续费"
        }}</el-button>
        <el-button type="primary" @click="scrollPlans">变更套餐</el-button>
      </div>
    </section>

    <section ref="plansRef" class="plans">
      <h3>套餐方案</h3>
      <div class="cards">
        <article v-for="p in plans" :key="p.code" class="card" :class="{ current: p.current }">
          <p v-if="p.current" class="badge">当前套餐</p>
          <h4>{{ p.name }}</h4>
          <p class="amount">¥{{ Math.round(p.listPriceYuan) }}<span>/月</span></p>
          <p class="suit">{{ p.suitableFor }}</p>
          <ul>
            <li v-for="b in p.benefits" :key="b">{{ b }}</li>
          </ul>
          <el-button :disabled="p.action === 'CURRENT'" type="primary" plain @click="onPlan(p)">
            {{ actionLabel(p) }}
          </el-button>
        </article>
      </div>
    </section>

    <section class="bills">
      <div class="bill-head">
        <h3>账单记录</h3>
        <el-button @click="exportCsv">导出账单</el-button>
      </div>
      <el-table :data="bills" empty-text="暂无账单">
        <el-table-column prop="billDate" label="日期" width="140" />
        <el-table-column prop="title" label="项目" min-width="220" />
        <el-table-column label="金额" width="140">
          <template #default="{ row }">
            ¥{{ row.amountYuan }}
            <span v-if="row.amountNote" class="note">{{ row.amountNote }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="payMethod" label="支付方式" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ row.status === "PAID" ? "已支付" : "待扣款" }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button v-if="row.canDownloadInvoice" text type="primary" @click="onInvoice">下载发票</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="payOpen" title="确认支付" width="420px">
      <p>应付 ¥{{ pendingYuan }}（演示支付，不会产生真实扣款）</p>
      <template #footer>
        <el-button @click="confirmPay(false)">支付失败</el-button>
        <el-button type="primary" :loading="paying" @click="confirmPay(true)">确认支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useUserStore } from "@/stores/user";
import {
  createOrder,
  createPayment,
  getSubscription,
  listBills,
  listPlans,
  mockComplete,
  patchAutoRenew,
  type BillItem,
  type PlanItem,
  type SubscriptionView,
} from "@/features/billing/billingApi";

const user = useUserStore();
const sub = ref<SubscriptionView | null>(null);
const plans = ref<PlanItem[]>([]);
const bills = ref<BillItem[]>([]);
const plansRef = ref<HTMLElement | null>(null);
const payOpen = ref(false);
const paying = ref(false);
const pendingYuan = ref(0);
let pendingPaymentId = "";
let pendingNonce = "";

const trialLabel = computed(() => user.me?.trialStatusLabel || (user.trialActive ? "试用中" : "试用已结束"));

function formatDate(raw?: string | null) {
  if (!raw) return "—";
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  return d.toLocaleDateString("zh-CN");
}

function actionLabel(p: PlanItem) {
  if (p.action === "CURRENT") return "当前使用";
  if (p.action === "UPGRADE") return `升级至${p.name}`;
  if (p.action === "DOWNGRADE") return `降级至${p.name}`;
  return `订阅${p.name}`;
}

async function reload() {
  const [s, p, b] = await Promise.all([getSubscription(), listPlans(), listBills()]);
  sub.value = s;
  plans.value = p.items;
  bills.value = b.items;
  await user.loadMe();
}

function scrollPlans() {
  plansRef.value?.scrollIntoView({ behavior: "smooth" });
}

async function toggleRenew() {
  if (!sub.value?.hasPaidSubscription) return;
  await patchAutoRenew(!sub.value.autoRenew);
  await reload();
}

async function onPlan(p: PlanItem) {
  if (p.action === "CURRENT") return;
  if (p.action === "DOWNGRADE") {
    await ElMessageBox.confirm("本周期仍用当前档，到期后不再自动按高档续。不退款。", "确认降级");
    await createOrder(p.code);
    ElMessage.success("已预约降级");
    await reload();
    return;
  }
  const order = await createOrder(p.code);
  const pay = await createPayment(order.orderId);
  pendingPaymentId = pay.paymentId;
  pendingNonce = pay.mockNonce || "";
  pendingYuan.value = order.payableYuan;
  payOpen.value = true;
}

async function confirmPay(success: boolean) {
  if (!pendingNonce) {
    ElMessage.error("缺少支付确认");
    return;
  }
  paying.value = true;
  try {
    await mockComplete(pendingPaymentId, success, pendingNonce);
    payOpen.value = false;
    pendingNonce = "";
    ElMessage.success(success ? "支付成功" : "支付未完成，订单仍待支付");
    await reload();
  } finally {
    paying.value = false;
  }
}

function onInvoice() {
  ElMessage.info("发票开具后续提供");
}

function exportCsv() {
  if (!bills.value.length) {
    ElMessage.info("暂无可导出账单");
    return;
  }
  const header = "日期,项目,金额,备注,支付方式,状态";
  const rows = bills.value.map((b) =>
    [b.billDate, b.title, b.amountYuan, b.amountNote || "", b.payMethod, b.status].join(","),
  );
  const blob = new Blob(["\uFEFF" + [header, ...rows].join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "bills.csv";
  a.click();
  URL.revokeObjectURL(url);
}

onMounted(reload);
</script>

<style scoped>
.billing {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.current {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(135deg, #5b4dff, #7a6cff);
}
.current.trial {
  background: linear-gradient(135deg, #6b7280, #9ca3af);
}
.eyebrow {
  letter-spacing: 0.12em;
  font-size: 12px;
  opacity: 0.8;
}
.price {
  font-size: 22px;
  margin: 8px 0;
}
.meta {
  margin: 4px 0;
  opacity: 0.9;
}
.cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.card {
  position: relative;
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}
.card.current {
  border-color: #5b4dff;
  box-shadow: 0 0 0 1px #5b4dff;
}
.badge {
  position: absolute;
  top: 12px;
  right: 12px;
  color: #5b4dff;
  font-size: 12px;
}
.amount {
  font-size: 28px;
  margin: 8px 0;
}
.amount span {
  font-size: 14px;
  color: #6b7280;
}
.suit {
  color: #6b7280;
  min-height: 40px;
}
.bill-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.note {
  margin-left: 6px;
  color: #6b7280;
  font-size: 12px;
}
</style>
