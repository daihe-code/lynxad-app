export type PlanSubmissionPhase = 'SUBMITTING' | 'DELIVERING' | 'UNCERTAIN' | 'FAILED';

interface SubmissionSource {
  status?: string;
  publishStatus?: string;
  name?: string | null;
  smartBidType?: string | null;
  budget?: number | null;
  roi2Goal?: number | null;
  scheduleType?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  products?: readonly unknown[];
  createdAt?: string | null;
  deliveryPlatform?: string | null;
}

export function planSubmissionPhase(source: SubmissionSource): PlanSubmissionPhase | undefined {
  if (source.publishStatus === 'UNCERTAIN' || source.publishStatus === 'FAILED') return source.publishStatus;
  if (source.status === 'DELIVERING') return 'DELIVERING';
  if (source.status === 'SUBMITTING' || ['QUEUED', 'SENDING'].includes(source.publishStatus || '')) return 'SUBMITTING';
  return undefined;
}

export function planSubmissionMessage(phase: PlanSubmissionPhase) {
  return {
    SUBMITTING: {
      title: '正在提交投放计划', description: '我正在将你确认的投放计划提交投放',
      hint: '结果返回后将自动更新，你也可前往投放列表查看进度。', note: '返回列表不影响本次提交。',
    },
    DELIVERING: {
      title: '投放中', description: '投放计划已开始投放，我会持续关注投放表现',
      hint: '投放状态会自动更新，你也可前往投放列表查看进度和效果。', note: '返回列表不影响计划投放。',
    },
    UNCERTAIN: {
      title: '平台创建结果待确认', description: '暂未确认平台创建结果，请先核对千川计划',
      hint: '核对完成后可关联已有的千川计划，避免重复创建。', note: '返回列表不影响结果核查。',
    },
    FAILED: {
      title: '发布失败', description: '本次投放计划未提交成功，请查看原因后处理',
      hint: '当前计划内容已保留，可返回修改后重新提交。', note: '',
    },
  }[phase];
}

function numeric(value: number | null | undefined, currency = false) {
  if (value == null || !Number.isFinite(value)) return '—';
  return `${currency ? '¥' : ''}${value.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
}

function creationTime(value: string | null | undefined) {
  if (!value) return '—';
  // Legacy timestamps without an offset were recorded in Shanghai local time.
  const timestamp = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(value)
    ? `${value.replace(' ', 'T')}+08:00` : value;
  const date = new Date(timestamp);
  if (!Number.isFinite(date.getTime())) return '—';
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(date).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
}

export function planSubmissionSummary(source: SubmissionSource, awemeName?: string | null) {
  const custom = ['CUSTOM', 'SMART_BID_CUSTOM'].includes(source.smartBidType || '');
  const volume = ['CONSERVATIVE', 'SMART_BID_CONSERVATIVE'].includes(source.smartBidType || '');
  const duration = source.scheduleType === 'FROM_NOW' ? '长期投放'
    : source.startDate && source.endDate ? `${source.startDate} 至 ${source.endDate}`
      : source.endDate ? `至 ${source.endDate}` : '—';
  return [
    { title: '投放信息', rows: [
      ['投放平台', source.deliveryPlatform === 'STANDARD' ? '抖音 · 巨量千川（标准投放）' : '抖音 · 巨量千川'],
      ['投放抖音号', awemeName?.trim() || '—'],
      ['投放方式', custom ? '控成本投放' : volume ? '放量投放' : '—'],
      ['计划名称', source.name?.trim() || '—'],
      ['投放商品', source.products ? `${source.products.length}个` : '—'],
    ] },
    { title: '预算与时长', rows: [
      ['日预算', source.budget == null || !Number.isFinite(source.budget) ? '—' : `${numeric(source.budget, true)} / 天`],
      ['净成交ROI目标', custom ? numeric(source.roi2Goal) : volume ? '无需设置' : '—'],
      ['投放时长', duration],
      ['广告计划创建时间', creationTime(source.createdAt)],
    ] },
  ];
}
