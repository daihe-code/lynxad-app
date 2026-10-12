export const CONTROL_TASK_STATUS: Record<string, string> = {
  PROCESSING: '调控中', DISABLE: '已暂停', OFFLINE_TIME: '到达投放时间',
  OFFLINE_BUDGET: '预算已花完', OFFLINE_BALANCE: '账户余额不足', ROI2_DISABLE: '全域投放已暂停',
  FROZEN: '调控结束', DELETED: '已删除', LIVE_ROOM_OFF: '直播间已关闭', MOCK: '模拟任务',
};
export const taskStatusText = (status?: string) => CONTROL_TASK_STATUS[status || ''] || status || '平台未返回状态';
export const CONTROL_TASK_METRICS = [
  { key: 'show_cnt_for_roi2_assist', label: '调控展示次数', kind: 'count' },
  { key: 'click_cnt_for_roi2_assist', label: '调控点击次数', kind: 'count' },
  { key: 'ctr_for_roi2_assist', label: '调控点击率', kind: 'rate' },
  { key: 'convert_rate_for_roi2_assist', label: '调控转化率', kind: 'rate' },
  { key: 'stat_cost_for_roi2_assist', label: '调控消耗（元）', kind: 'money' },
  { key: 'total_pay_order_count_for_roi2_assist', label: '调控成交订单数', kind: 'count' },
  { key: 'total_pay_order_gmv_include_coupon_for_roi2_assist', label: '调控整体成交金额（元）', kind: 'money' },
  { key: 'total_prepay_and_pay_order_roi2_assist', label: '调控支付 ROI', kind: 'number' },
  { key: 'total_order_settle_amount_for_roi2_1h_assist', label: '调控净成交金额（元）', kind: 'money' },
  { key: 'total_prepay_and_pay_settle_roi2_1h_assist', label: '调控净成交 ROI', kind: 'number' },
  { key: 'total_order_settle_amount_rate_for_roi2_1h_assist', label: '调控净成交金额结算率', kind: 'rate' },
  { key: 'total_refund_order_gmv_for_roi2_1h_rate_assist', label: '调控1小时内退款率', kind: 'rate' },
];
export const CONTROL_REPORT_METRICS = [...CONTROL_TASK_METRICS, { key: 'total_pay_order_gmv_for_roi2_assist', label: '调控用户实际支付金额（元）', kind: 'money' }];
export function taskActions(status?: string): ('PAUSE' | 'RESUME' | 'END' | 'DELETE')[] {
  if (status === 'PROCESSING') return ['PAUSE', 'END', 'DELETE'];
  if (status === 'DISABLE') return ['RESUME', 'END', 'DELETE'];
  if (['OFFLINE_BALANCE', 'ROI2_DISABLE', 'LIVE_ROOM_OFF'].includes(status || '')) return ['END', 'DELETE'];
  if (['FROZEN', 'OFFLINE_TIME', 'OFFLINE_BUDGET', 'MOCK'].includes(status || '')) return ['DELETE'];
  return [];
}
