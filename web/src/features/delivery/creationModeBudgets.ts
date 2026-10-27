import { computed, ref, type Ref } from 'vue';

export type CreationBidMode = 'CUSTOM' | 'CONSERVATIVE';
export type ModeBudgets = Partial<Record<CreationBidMode, string>>;

/** Older drafts only have the active budget; never copy it to the other mode. */
export function restoreModeBudgets(mode: CreationBidMode, budget: string, remembered?: ModeBudgets): ModeBudgets {
  const values: ModeBudgets = {};
  for (const key of ['CUSTOM', 'CONSERVATIVE'] as const) {
    if (remembered?.[key] !== undefined) values[key] = remembered[key];
  }
  if (budget !== '' || values[mode] !== undefined) values[mode] = budget;
  return values;
}

export function useCreationModeBudgets(mode: Ref<CreationBidMode>) {
  const budgets = ref<ModeBudgets>({});
  const budget = computed({
    get: () => budgets.value[mode.value] ?? '',
    set: (value: string) => { budgets.value[mode.value] = value; },
  });
  function restore(value: string, remembered?: ModeBudgets) {
    budgets.value = restoreModeBudgets(mode.value, value, remembered);
  }
  function applyRecommendation(value: string) {
    if (mode.value === 'CONSERVATIVE' && budgets.value.CONSERVATIVE === undefined) budget.value = value;
  }
  return { budget, budgets, restore, applyRecommendation, reset: () => { budgets.value = {}; } };
}
