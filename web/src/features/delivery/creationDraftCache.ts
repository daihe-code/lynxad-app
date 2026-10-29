import { restoreModeBudgets, type ModeBudgets } from './creationModeBudgets.ts';

/** Local input recovery only. Tokens, product URLs, media and AI results are never cached. */
export interface DraftScope {
  tenantId: string; userId: string; accountId: string; awemeId: string; planId: string | null;
}
export interface DraftInputs {
  productIds: string[]; name: string; budget: string; roi: string;
  roiSource?: 'PLATFORM' | 'MANUAL';
  modeBudgets?: ModeBudgets;
  mode: 'CUSTOM' | 'CONSERVATIVE'; duration: 'long' | 'custom'; start: string; end: string; step: number;
}
export interface DraftBaseline { revision: number | null; inputs: DraftInputs }
export interface CachedCreationDraft {
  schema: 1; scope: DraftScope; baseline: DraftBaseline; inputs: DraftInputs; savedAt: number; writer: string;
}
export type DraftStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
export const DRAFT_TTL_MS = 7 * 24 * 3600 * 1000;
const PREFIX = 'lynx.creation.inputs.v1:';
const identifier = (value: unknown): value is string => typeof value === 'string' && value.length > 0 && value.length <= 128;
export const DRAFT_INPUT_FIELDS: (keyof DraftInputs)[] = ['productIds','name','budget','roi','roiSource','mode','duration','start','end','step','modeBudgets'];

function validScope(scope: DraftScope): boolean {
  return !!scope && [scope.tenantId,scope.userId,scope.accountId,scope.awemeId].every(identifier)
    && (scope.planId === null || identifier(scope.planId));
}
export function draftKey(scope: DraftScope): string {
  if (!validScope(scope)) throw new Error('Missing draft identity');
  return PREFIX + encodeURIComponent(JSON.stringify([scope.tenantId,scope.userId,scope.accountId,scope.awemeId,scope.planId]));
}
function sameScope(a: DraftScope, b: DraftScope): boolean { return validScope(a) && draftKey(a) === draftKey(b); }
function cleanScope(scope: DraftScope): DraftScope {
  return { tenantId: scope.tenantId, userId: scope.userId, accountId: scope.accountId, awemeId: scope.awemeId, planId: scope.planId };
}
function validInputs(value: unknown): value is DraftInputs {
  if (!value || typeof value !== 'object') return false;
  const x = value as DraftInputs;
  return Array.isArray(x.productIds) && x.productIds.length <= 10 && x.productIds.every(id => typeof id === 'string' && /^[1-9][0-9]{0,18}$/.test(id))
    && new Set(x.productIds).size === x.productIds.length
    && typeof x.name === 'string' && x.name.length <= 200
    && typeof x.budget === 'string' && x.budget.length <= 64 && typeof x.roi === 'string' && x.roi.length <= 64
    && (x.roiSource === undefined || ['PLATFORM','MANUAL'].includes(x.roiSource))
    && (x.modeBudgets === undefined || !!x.modeBudgets && typeof x.modeBudgets === 'object'
      && !Array.isArray(x.modeBudgets) && ['CUSTOM','CONSERVATIVE'].every(key => {
        const value = x.modeBudgets![key as keyof ModeBudgets];
        return value === undefined || typeof value === 'string' && value.length <= 64;
      }))
    && ['CUSTOM','CONSERVATIVE'].includes(x.mode) && ['long','custom'].includes(x.duration)
    && [x.start,x.end].every(date => typeof date === 'string' && (date === '' || /^\d{4}-\d{2}-\d{2}$/.test(date)))
    && Number.isInteger(x.step) && x.step >= 1 && x.step <= 3;
}
function cleanInputs(x: DraftInputs): DraftInputs {
  return { productIds: [...x.productIds], name: x.name, budget: x.budget, roi: x.roi, ...(x.roiSource ? { roiSource:x.roiSource } : {}),
    ...(x.modeBudgets ? { modeBudgets:restoreModeBudgets(x.mode,x.budget,x.modeBudgets) } : {}), mode: x.mode,
    duration: x.duration, start: x.start, end: x.end, step: x.step };
}
function numericText(text: string): string {
  if (!/^\d+(\.\d{1,2})?$/.test(text.trim())) return text;
  const [integer,decimal=''] = text.trim().split('.');
  const tail = decimal.replace(/0+$/, '');
  return integer.replace(/^0+(?=\d)/, '') + (tail ? '.' + tail : '');
}
function budgetMemory(inputs: DraftInputs): (string | null)[] {
  const values = restoreModeBudgets(inputs.mode,inputs.budget,inputs.modeBudgets);
  return (['CUSTOM','CONSERVATIVE'] as const).map(mode => values[mode] === undefined ? null : numericText(values[mode]));
}
export function changedDraftFields(inputs: DraftInputs, baseline: DraftInputs): (keyof DraftInputs)[] {
  return DRAFT_INPUT_FIELDS.filter(field => field === 'budget' || field === 'roi'
    ? numericText(inputs[field]) !== numericText(baseline[field])
    : field === 'modeBudgets' ? !!(inputs.modeBudgets || baseline.modeBudgets) &&
      JSON.stringify(budgetMemory(inputs)) !== JSON.stringify(budgetMemory(baseline))
    : JSON.stringify(inputs[field]) !== JSON.stringify(baseline[field]));
}

/** Worker progress changes the revision without changing the user's plan inputs. */
export function rebaseDraftAfterBackgroundUpdate(cached: CachedCreationDraft, baseline: DraftBaseline): CachedCreationDraft | undefined {
  const serverChanges = changedDraftFields(baseline.inputs,cached.baseline.inputs);
  const remainingChanges = changedDraftFields(baseline.inputs,cached.inputs);
  if (serverChanges.some(field => field !== 'step' && remainingChanges.includes(field))) return;
  return { ...cached, baseline: { revision: baseline.revision, inputs: cleanInputs(baseline.inputs) },
    inputs: cleanInputs({ ...cached.inputs, step: cached.inputs.step !== cached.baseline.inputs.step ? cached.inputs.step : baseline.inputs.step }) };
}

export function parseCachedDraft(raw: string | null, scope: DraftScope, now=Date.now()): CachedCreationDraft | undefined {
  if (!raw || raw.length > 16384) return;
  try {
    const x = JSON.parse(raw) as CachedCreationDraft;
    if (x.schema !== 1 || !sameScope(x.scope,scope) || !identifier(x.writer) || !validInputs(x.inputs)
      || !x.baseline || !validInputs(x.baseline.inputs)
      || (x.baseline.revision !== null && (!Number.isSafeInteger(x.baseline.revision) || x.baseline.revision < 1))
      || (scope.planId === null) !== (x.baseline.revision === null)
      || !Number.isFinite(x.savedAt) || x.savedAt > now + 300000 || now - x.savedAt > DRAFT_TTL_MS) return;
    return { schema: 1, scope: cleanScope(scope), writer: x.writer, savedAt: x.savedAt,
      inputs: cleanInputs(x.inputs), baseline: { revision: x.baseline.revision, inputs: cleanInputs(x.baseline.inputs) } };
  } catch { return; }
}

export type CacheResult = 'saved' | 'clean' | 'conflict' | 'unavailable';
/** Compare before writes so a stale tab stops instead of silently replacing a newer local draft. */
export class CreationDraftCache {
  scope?: DraftScope;
  baseline?: DraftBaseline;
  cached?: CachedCreationDraft;
  private expected: string | null = null;
  private storage: DraftStorage;
  readonly writer: string;
  constructor(storage: DraftStorage, writer: string) { this.storage = storage; this.writer = writer; }

  open(scope: DraftScope, baseline: DraftBaseline): CachedCreationDraft | undefined {
    this.scope = cleanScope(scope); this.baseline = { revision: baseline.revision, inputs: cleanInputs(baseline.inputs) };
    this.expected = this.storage.getItem(draftKey(scope));
    this.cached = parseCachedDraft(this.expected,scope);
    return this.cached;
  }
  acknowledge(revision: number | null, inputs: DraftInputs, acknowledged: (keyof DraftInputs)[]): void {
    if (!this.baseline) return;
    this.baseline.revision = revision;
    for (const key of acknowledged) Object.assign(this.baseline.inputs, { [key]: structuredClone(inputs[key]) });
  }
  recover(cached: CachedCreationDraft, force=false): void {
    if (!force) this.baseline = { revision: cached.baseline.revision, inputs: cleanInputs(cached.baseline.inputs) };
  }
  save(inputs: DraftInputs, now=Date.now()): CacheResult {
    if (!this.scope || !this.baseline || !validInputs(inputs)) return 'unavailable';
    try {
      const key = draftKey(this.scope);
      if (this.storage.getItem(key) !== this.expected) return 'conflict';
      if (!changedDraftFields(inputs,this.baseline.inputs).length) {
        this.storage.removeItem(key); this.expected = null; this.cached = undefined; return 'clean';
      }
      const record: CachedCreationDraft = { schema: 1, scope: { ...this.scope }, baseline: structuredClone(this.baseline),
        inputs: cleanInputs(inputs), savedAt: now, writer: this.writer };
      const raw = JSON.stringify(record);
      if (raw.length > 16384) return 'unavailable';
      this.storage.setItem(key,raw); this.expected = raw; this.cached = record; return 'saved';
    } catch { return 'unavailable'; }
  }
  discard(): CacheResult {
    if (!this.scope) return 'clean';
    try {
      const key = draftKey(this.scope);
      if (this.storage.getItem(key) !== this.expected) return 'conflict';
      this.storage.removeItem(key); this.expected = null; this.cached = undefined; return 'clean';
    } catch { return 'unavailable'; }
  }
}

export function rememberDraftAccount(storage: DraftStorage, scope: DraftScope): void {
  storage.setItem(PREFIX + 'account:' + encodeURIComponent(JSON.stringify([scope.tenantId,scope.userId])),
    JSON.stringify({ accountId: scope.accountId, awemeId: scope.awemeId, savedAt: Date.now() }));
}
export function recalledDraftAccount(storage: DraftStorage, tenantId: string, userId: string, now=Date.now()): string | undefined {
  try {
    const x = JSON.parse(storage.getItem(PREFIX + 'account:' + encodeURIComponent(JSON.stringify([tenantId,userId]))) || 'null');
    if (x && identifier(x.accountId) && identifier(x.awemeId) && Number.isFinite(x.savedAt)
      && now - x.savedAt >= -300000 && now - x.savedAt <= DRAFT_TTL_MS) return `${x.accountId}:${x.awemeId}`;
  } catch { /* Recovery remains optional when browser storage is unavailable. */ }
}
