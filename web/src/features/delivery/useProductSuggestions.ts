import { computed, onUnmounted, ref, watch } from 'vue';
import { creationProductSuggestions, type CreationProduct, type ProductSuggestions } from './creationApi';
import { type ApiRequestError, requestFailure } from '@/api/apiErrors';

export function useProductSuggestions(input: {
  account: () => { accountId?: string; awemeId: string } | undefined;
  selected: () => CreationProduct[];
  draftId: () => string | undefined;
  sourceProductId: () => string | undefined;
}) {
  const result = ref<ProductSuggestions>();
  const failure = ref<ApiRequestError>();
  const loading = ref(false);
  const sourceProduct = computed(() => input.selected().find(p => p.qcProductId === input.sourceProductId()));
  const scope = computed(() => {
    const account = input.account();
    return account?.accountId && account.awemeId && sourceProduct.value
      ? JSON.stringify([account.accountId, account.awemeId, input.draftId() || '', sourceProduct.value.qcProductId, input.selected().map(p => p.qcProductId).sort()]) : '';
  });
  const products = computed(() => result.value?.items.map(item => item.product) || []);
  let request: AbortController | undefined, sequence = 0, disposed = false, requestScope = '';

  async function load(refresh = false) {
    if (!scope.value || disposed) return;
    // Repeated clicks on the same anchor share the running request instead of cancelling it.
    if (loading.value && requestScope === scope.value) return;
    request?.abort();
    request = new AbortController();
    const current = ++sequence, key = scope.value, account = input.account()!, ids = [sourceProduct.value!.qcProductId];
    requestScope = key;
    loading.value = true; failure.value = undefined; result.value = undefined;
    try {
      const next = await creationProductSuggestions(account.accountId!, account.awemeId, ids, input.draftId(), refresh, request.signal, input.selected().map(p => p.qcProductId));
      if (disposed || current !== sequence || key !== scope.value) return;
      if (next.accountId !== account.accountId || next.awemeId !== account.awemeId || JSON.stringify([...next.sourceProductIds].sort()) !== JSON.stringify(ids)) {
        throw new Error('推荐结果与当前选品不一致，请重试');
      }
      const seen = new Set(input.selected().map(p => p.qcProductId));
      result.value = { ...next, items: next.items.filter(({ product }) => {
        if (seen.has(product.qcProductId) || product.draft || product.unavailableReasons?.length || product.stock != null && product.stock <= 0) return false;
        seen.add(product.qcProductId);
        return true;
      }) };
    } catch (error) {
      if (!disposed && current === sequence && key === scope.value) {
        const next = requestFailure(error, { method: 'GET' });
        if (next.kind !== 'CANCELLED') failure.value = next;
      }
    } finally {
      if (!disposed && current === sequence) loading.value = false;
    }
  }
  // Context changes invalidate requests; only an explicit user action starts a new recommendation.
  watch(scope, () => {
    request?.abort(); sequence++; result.value = undefined; failure.value = undefined; loading.value = false;
  });
  onUnmounted(() => { disposed = true; sequence++; request?.abort(); });
  return { sourceProduct, products, result, failure, loading, load };
}
