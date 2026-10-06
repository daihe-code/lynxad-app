import { computed, ref, watch, onUnmounted } from "vue";
import { useRoute } from "vue-router";
import {
  getPlan,
  type PlanVo,
  type PlanListVo,
} from "@/features/delivery/deliveryApi";
import { listAccounts, type AccountVo } from "@/features/identity/oauthApi";
import {
  listActions,
  type AgentActionVo,
} from "@/features/agentops/agentopsApi";
import type { ReportRowVo } from "@/features/insight/insightApi";

export const statusInfo = (status?: string) =>
  ({
    DRAFT: ["草稿", "pending", "doc"],
    SUBMITTING: ["待审核", "pending", "doc"],
    PENDING_AUDIT: ["审核中", "reviewing", "hourglass"],
    REJECTED: ["审核未通过", "bad", "warn-tri"],
    DELIVERING: ["投放中", "running", "play"],
    PAUSED: ["已暂停", "paused", "pause"],
    FINISHED: ["已结束", "stopped", "stop"],
    COMPLETED: ["已完成", "done", "check-done"],
    SCHEDULED: ["待投放", "passed", "check-clock"],
    TERMINATED: ["已终止", "stopped", "stop"],
  })[status || ""] || ["状态待同步", "pending", "doc"];
export function amount(value: unknown, prefix = "") {
  if (value == null || value === "" || !Number.isFinite(Number(value)))
    return "—";
  return (
    prefix +
    Number(value).toLocaleString("zh-CN", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
  );
}
export function dateText(value?: string | null) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? "—"
    : d.toLocaleString("zh-CN", { hour12: false });
}
export function mediaUrl(value?: string | null) {
  if (!value) return "";
  if (/^(https?:|blob:)/.test(value)) return value;
  return (
    (import.meta.env.VITE_API_BASE || "/lynx-ad") +
    value.replace(/^\/lynx-ad/, "")
  );
}

export function usePlanPresentation(id?: () => string) {
  const route = useRoute();
  const plan = ref<(Omit<PlanVo, keyof PlanListVo> & PlanListVo) | null>(null);
  const accounts = ref<AccountVo[]>([]),
    actions = ref<AgentActionVo[]>([]),
    report = ref<ReportRowVo | null>(null);
  const loading = ref(false),
    failed = ref(false);
  let request = 0;
  onUnmounted(() => {
    request++;
  });
  async function load() {
    const key = id ? id() : String(route.params.id || ""),
      seq = ++request;
    if (!key) {
      plan.value = null;
      return;
    }
    loading.value = true;
    failed.value = false;
    try {
      const base = await getPlan(key);
      if (seq !== request) return;
      plan.value = base;
      const results = await Promise.allSettled([
        listAccounts(),
        listActions(),
      ]);
      if (seq !== request) return;
      accounts.value =
        results[0].status === "fulfilled" ? results[0].value : [];
      actions.value =
        results[1].status === "fulfilled"
          ? results[1].value.filter((a) => a.planId === key)
          : [];
      report.value = null;
    } catch {
      if (seq === request) {
        failed.value = true;
        plan.value = null;
      }
    } finally {
      if (seq === request) loading.value = false;
    }
  }
  watch(
    () => (id ? id() : String(route.params.id || "")),
    () => {
      plan.value = null;
      actions.value = [];
      report.value = null;
      void load();
    },
    { immediate: true },
  );
  const account = computed(() =>
    accounts.value.find((a) =>
      plan.value?.accountId ? a.accountId === plan.value.accountId : a.awemes?.some((w) => w.awemeId === plan.value?.awemeId),
    ),
  );
  const shop = computed(
    () =>
      account.value?.awemes.find((a) => a.awemeId === plan.value?.awemeId)
        ?.name ||
      plan.value?.awemeId ||
      "—",
  );
  const hasData = computed(
    () =>
      !!plan.value?.snapshotTs &&
      ![
        "DRAFT",
        "SUBMITTING",
        "PENDING_AUDIT",
        "REJECTED",
        "SCHEDULED",
      ].includes(plan.value?.status || ""),
  );
  const metrics = computed(() => ({
    cost: hasData.value ? plan.value?.costYuan : null,
    gmv: hasData.value ? plan.value?.gmvYuan : null,
    roi: hasData.value ? plan.value?.roi : null,
    orders: hasData.value
      ? plan.value?.convert
      : null,
    impressions: null as number | null,
    clicks: null as number | null,
  }));
  return {
    plan,
    accounts,
    account,
    shop,
    actions,
    report,
    metrics,
    hasData,
    loading,
    failed,
    load,
  };
}
