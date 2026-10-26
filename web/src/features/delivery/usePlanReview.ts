import { computed, ref, onMounted, onUnmounted, watch } from 'vue';
import { getPlan, refreshPlanAudit, type PlanVo } from './deliveryApi';
import { reviewNotice } from './planReview';

type ReviewSource = Pick<PlanVo, 'planId' | 'status' | 'qcPlanId' | 'review'>;

export function usePlanReview(plan: () => ReviewSource, updated: (plan: PlanVo) => void, autoRead: () => boolean) {
  const busy = ref(false), feedback = ref(''), now = ref(Date.now());
  const notice = computed(() => reviewNotice(plan(), now.value));
  const refreshing = computed(() => busy.value || !!plan().review?.checking);
  let context = 0, operation = 0, nextRead = Date.now() + 60000;
  let timer: ReturnType<typeof setInterval> | undefined;
  watch(() => plan().planId, () => {
    context++; operation++; nextRead = Date.now() + 60000; busy.value = false; feedback.value = '';
  });
  async function refresh() {
    if (refreshing.value || !notice.value || notice.value.cooldown > 0) return;
    const id = plan().planId, seq = context, read = ++operation;
    busy.value = true; feedback.value = '';
    try {
      const result = await refreshPlanAudit(id);
      if (context !== seq || operation !== read || plan().planId !== id) return;
      updated(result); now.value = Date.now();
      feedback.value = result.review?.state === 'CHECK_UNAVAILABLE' ? '本次未取得最终审核结果，系统会继续查询。'
        : result.status === 'PENDING_AUDIT' ? '已读取当前审核进度，最终结果仍待千川确认。' : '平台状态已更新。';
    } catch {
      if (context === seq) feedback.value = '本次查询未完成，已保留当前状态，请稍后刷新；无需重复发布。';
    } finally { if (context === seq) busy.value = false; }
  }
  async function readCached() {
    const id = plan().planId, seq = context, read = ++operation;
    try {
      const result = await getPlan(id);
      if (context === seq && read === operation && plan().planId === id) updated(result);
    } catch { /* Preserve the last known state; explicit refresh remains available. */ }
  }
  onMounted(() => {
    timer = setInterval(() => {
      now.value = Date.now();
      if (autoRead() && notice.value && !busy.value && now.value >= nextRead && document.visibilityState === 'visible') {
        nextRead = now.value + 60000; void readCached();
      }
    }, 1000);
  });
  onUnmounted(() => { context++; clearInterval(timer); });
  return { notice, refreshing, feedback, refresh };
}
