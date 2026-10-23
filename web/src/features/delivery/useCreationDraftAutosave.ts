import { computed, onBeforeUnmount, onMounted, ref, toRaw, watch } from 'vue';
import type { CreationState } from './creationApi';
import { creationScheduleDefaults } from './creationSchedule';
import { CreationDraftCache, changedDraftFields, draftKey, rememberDraftAccount,
  type CachedCreationDraft, type DraftInputs, type DraftScope } from './creationDraftCache';

const goalFields: (keyof DraftInputs)[] = ['name','budget','roi','mode','duration','start','end'];
export { goalFields as draftGoalFields };
export function serverDraftInputs(state: CreationState | undefined, today: string): DraftInputs {
  const schedule = creationScheduleDefaults(today, state?.startDate, state?.endDate);
  return { productIds: state?.products.map(p => p.qcProductId) || [], name: state?.name || '',
    budget: state?.budget == null ? '' : String(state.budget), roi: state?.roi2Goal == null ? '' : String(state.roi2Goal),
    mode: state?.smartBidType || 'CUSTOM', duration: state?.scheduleType === 'DATE_RANGE' ? 'custom' : 'long',
    ...schedule, step: Math.min(3,state?.step || 1) };
}

export function useCreationDraftAutosave(options: {
  scope: () => DraftScope | undefined; server: () => CreationState | undefined; today: () => string;
  capture: () => DraftInputs; restore: (inputs: DraftInputs) => Promise<string | undefined>;
  reconnect: () => void; refreshServer: () => Promise<void>;
}) {
  const status = ref(''), pending = ref<CachedCreationDraft>(), restoring = ref(false), online = ref(navigator.onLine);
  const paused = ref(false);
  let cache: CreationDraftCache | undefined, active = false, timer: ReturnType<typeof setTimeout> | undefined;
  const writer = crypto.randomUUID();
  let unsavedConflict = false;
  const dirty = () => !!cache?.baseline && changedDraftFields(options.capture(),cache.baseline.inputs).length > 0;
  const currentScope = () => {
    const scope = options.scope();
    return scope && cache?.scope && draftKey(scope) === draftKey(cache.scope);
  };
  const serverBaseline = () => ({ revision: options.server()?.revision ?? null, inputs: serverDraftInputs(options.server(),options.today()) });
  function editable() {
    const s = options.server();
    return !s || (!s.qcPlanId && ['NONE','FAILED'].includes(s.publishStatus)
      && !['QUEUED','SUBMITTING','RUNNING','IMPORTING','REVISING','UNCERTAIN'].includes(s.generationStatus));
  }
  function snapshot(): CachedCreationDraft | undefined {
    if (!cache?.scope || !cache.baseline) return;
    return { schema: 1, scope: { ...cache.scope }, baseline: structuredClone(cache.baseline),
      inputs: structuredClone(options.capture()), savedAt: Date.now(), writer };
  }
  function conflict(message: string) {
    pending.value = snapshot(); paused.value = true; unsavedConflict = true; status.value = message;
  }
  function flush(): boolean {
    clearTimeout(timer);
    if (!active || !currentScope() || restoring.value) return true;
    if (paused.value) return !unsavedConflict;
    if (!editable()) return true;
    const result = cache!.save(options.capture());
    if (result === 'conflict') { conflict('另一个窗口更新了本机草稿，自动保存已暂停，请选择要保留的内容。'); return false; }
    if (result === 'unavailable') { status.value = '本机自动保存不可用，请使用“保存草稿”保存到服务器。'; return false; }
    status.value = '';
    try { if (cache?.scope) rememberDraftAccount(localStorage,cache.scope); } catch { /* Main input cache has already been saved. */ }
    return true;
  }
  async function restore(cached: CachedCreationDraft, force: boolean) {
    cached = toRaw(cached);
    if (!cache || !currentScope() || restoring.value) return;
    if (!editable()) { pending.value = cached; paused.value = true; status.value = '草稿正在生成或发布，本机内容已保留，请结束当前任务后再核对。'; return; }
    restoring.value = true; paused.value = true;
    try {
      // Re-read the cache before an explicit choice, so a newer tab write cannot be erased silently.
      if (force) { await options.refreshServer(); cache.open(options.scope()!,serverBaseline()); }
      if (!editable()) { pending.value = cached; status.value = '服务器草稿当前不可编辑，本机内容已保留。'; return; }
      const warning = await options.restore(cached.inputs);
      cache.recover(cached,force);
      pending.value = undefined; paused.value = false; unsavedConflict = false;
      status.value = warning || '';
    } catch {
      pending.value = cached;
      status.value = '暂时无法核验恢复商品，本机内容仍保留，请联网后重试恢复。';
    } finally { restoring.value = false; }
  }
  async function activate(retirePrevious=false) {
    const previous = cache;
    active = false; clearTimeout(timer); status.value = ''; pending.value = undefined; paused.value = false; unsavedConflict = false;
    const scope = options.scope();
    if (!scope) { status.value = ''; return; }
    try {
      // Resolve storage lazily: even when the browser blocks localStorage itself,
      // retain the baseline in memory so leaving with unsaved input can be warned.
      cache = new CreationDraftCache({ getItem: key => localStorage.getItem(key),
        setItem: (key,value) => localStorage.setItem(key,value), removeItem: key => localStorage.removeItem(key) },writer);
      const cached = cache.open(scope,serverBaseline());
      active = true;
      if (!editable()) { status.value = ''; return; }
      if (cached && changedDraftFields(cached.inputs,cached.baseline.inputs).length) {
        if (cached.baseline.revision !== (options.server()?.revision ?? null)) {
          pending.value = cached; paused.value = true;
          status.value = '发现本机未提交内容，但服务器草稿已经更新，未自动覆盖，请核对后选择。';
        } else await restore(cached,false);
      }
      if (retirePrevious && previous?.scope?.planId === null && previous.scope && draftKey(previous.scope) !== draftKey(scope) && flush()) previous.discard();
    } catch { active = true; status.value = '本机自动保存不可用，请使用“保存草稿”保存到服务器。'; }
  }
  function acknowledge(fields: (keyof DraftInputs)[], revision = options.server()?.revision ?? null, inputs=options.capture()) {
    if (active && currentScope() && !paused.value) cache?.acknowledge(revision,inputs,fields);
  }
  function observeServer(incoming: CreationState) {
    if (!active || !currentScope() || paused.value || !cache?.baseline || incoming.revision === cache.baseline.revision) return false;
    if (dirty()) { conflict('服务器草稿在其他位置更新了，本机输入已保留，请核对版本后再提交。'); return false; }
    return true;
  }
  function suspend() { flush(); active = false; clearTimeout(timer); }
  function discard() {
    active = false; clearTimeout(timer); pending.value = undefined; paused.value = false; unsavedConflict = false;
    cache?.discard(); status.value = '';
  }
  async function keepServer() {
    if (!cache || restoring.value) return;
    restoring.value = true;
    try {
      await options.refreshServer();
      if (!editable()) { status.value = '服务器草稿当前不可编辑，本机内容已保留。'; return; }
      await options.restore(serverDraftInputs(options.server(),options.today()));
      cache.open(options.scope()!,serverBaseline());
      cache.discard(); pending.value = undefined; paused.value = false; unsavedConflict = false; status.value = '';
    } catch { status.value = '暂时无法读取服务器商品，本机内容仍保留，请稍后重试。'; }
    finally { restoring.value = false; }
  }
  const retryRestore = () => pending.value ? restore(pending.value,true) : Promise.resolve();
  watch(options.capture, () => {
    if (!active || paused.value || restoring.value || !currentScope()) return;
    clearTimeout(timer); timer = setTimeout(flush,300);
  }, { deep: true, flush: 'sync' });
  function connectionChanged() { online.value = navigator.onLine; flush(); if (online.value) options.reconnect(); }
  function storageChanged(event: StorageEvent) {
    if (active && cache?.scope && event.key === draftKey(cache.scope))
      conflict('另一个窗口更新了本机草稿，自动保存已暂停，请选择要保留的内容。');
  }
  function beforeUnload(event: BeforeUnloadEvent) {
    if (!flush() && dirty()) { event.preventDefault(); event.returnValue = ''; }
  }
  onMounted(() => {
    window.addEventListener('online',connectionChanged); window.addEventListener('offline',connectionChanged);
    window.addEventListener('storage',storageChanged); window.addEventListener('pagehide',flush);
    window.addEventListener('beforeunload',beforeUnload);
  });
  onBeforeUnmount(() => {
    flush(); active = false; clearTimeout(timer);
    window.removeEventListener('online',connectionChanged); window.removeEventListener('offline',connectionChanged);
    window.removeEventListener('storage',storageChanged); window.removeEventListener('pagehide',flush);
    window.removeEventListener('beforeunload',beforeUnload);
  });
  return { status, pending, restoring, online, paused, unresolved: computed(() => !!pending.value),
    activate, flush, suspend, discard, acknowledge, observeServer, retryRestore, keepServer };
}
