import { defineStore } from "pinia";
import { ref } from "vue";
import type { ProductVo } from "@/features/catalog/catalogApi";

export const useWizardStore = defineStore("wizard", () => {
  const step = ref(1);
  const accountId = ref("");
  const awemeId = ref("");
  const awemeName = ref("");
  const productIds = ref<string[]>([]);
  const products = ref<ProductVo[]>([]);
  const smartBidType = ref("SMART_BID_CUSTOM");
  const dailyBudget = ref(600);
  const roi2Goal = ref<number | undefined>(5.12);
  const planId = ref("");
  const planName = ref("");
  const taskId = ref("");
  const packageId = ref("");
  const aiProductIds = ref<string[]>([]);
  const aiSelectionConfirmed = ref(false);
  const publishStatus = ref("");
  const qcPlanId = ref("");

  function reset() {
    step.value = 1;
    productIds.value = [];
    products.value = [];
    smartBidType.value = "SMART_BID_CUSTOM";
    dailyBudget.value = 600;
    roi2Goal.value = 5.12;
    planId.value = "";
    planName.value = "";
    taskId.value = "";
    packageId.value = "";
    aiProductIds.value = [];
    aiSelectionConfirmed.value = false;
    publishStatus.value = "";
    qcPlanId.value = "";
  }

  return {
    step,
    accountId,
    awemeId,
    awemeName,
    productIds,
    products,
    smartBidType,
    dailyBudget,
    roi2Goal,
    planId,
    planName,
    taskId,
    packageId,
    aiProductIds,
    aiSelectionConfirmed,
    publishStatus,
    qcPlanId,
    reset,
  };
});
