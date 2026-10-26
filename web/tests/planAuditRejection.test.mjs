import test from 'node:test';
import assert from 'node:assert/strict';
import { auditRejectionIssues } from '../src/features/delivery/planAuditRejection.ts';

const material = (overrides = {}) => ({ id: '9007199254740995123', type: 'VIDEO', name: '手工吐司', auditStatus: 'AUDIT_REJECT', reasons: ['文案存在绝对化用语'], ...overrides });

test('计划拒审意见按实际原因提供资质指引，不生成已修复或自动重提结果', () => {
  const reason = '法式可颂面包缺少食品生产许可证（SC证）';
  const [issue] = auditRejectionIssues({ rejectReason: reason });
  assert.equal(issue.category, 'QUALIFICATION');
  assert.deepEqual(issue.reasons, [reason]);
  assert.match(issue.guidance, /巨量千川/);
  assert.doesNotMatch(issue.guidance, /已修复|已恢复|自动重新提交|1个工作日|无需操作/);
});

test('仅展示明确拒审且未删除的素材，已通过或仍在审核的旧原因不会当作当前拒审项', () => {
  const rows = [material(), material({id:'passed',auditStatus:'PASS'}), material({id:'pending',auditStatus:'AUDITING'}), material({id:'deleted',deleted:true}), material({id:'unknown',auditStatus:'NEW_STATUS'})];
  const issues = auditRejectionIssues({ rejectReason: '审核未通过，请前往千川查看具体原因' }, rows);
  assert.equal(issues.length, 1);
  assert.equal(issues[0].materialId, '9007199254740995123');
  assert.equal(issues[0].category, 'MATERIAL');
  assert.deepEqual(issues[0].reasons, ['文案存在绝对化用语']);
});

test('重复素材按类型和ID合并意见，不把同名素材合并或丢失新增原因', () => {
  const issues = auditRejectionIssues({}, [material(), material({reasons:['文案存在绝对化用语','画面与商品不一致']}), material({id:'another'}), material({type:'IMAGE'})]);
  assert.equal(issues.length, 3);
  assert.deepEqual(issues[0].reasons, ['文案存在绝对化用语','画面与商品不一致']);
});

test('无详细原因仍保留待核对意见，不能把空结果当作审核通过或自动处理完成', () => {
  const [missing] = auditRejectionIssues({});
  assert.equal(missing.category, 'PLATFORM');
  assert.match(missing.reasons[0], /未返回详细拒审原因/);
  const [noDetail] = auditRejectionIssues({}, [material({reasons:[]})]);
  assert.match(noDetail.reasons[0], /详细原因暂未返回/);
  assert.equal(auditRejectionIssues({}, [material({auditStatus:'PASS'})])[0].key, 'plan');
});

test('版权使用授权归素材问题，账户授权问题单独引导，未知原因保留原文', () => {
  assert.equal(auditRejectionIssues({rejectReason:'视频缺少版权使用授权'})[0].category, 'MATERIAL');
  assert.equal(auditRejectionIssues({rejectReason:'广告账户授权已失效'})[0].category, 'AUTHORIZATION');
  const [unknown] = auditRejectionIssues({rejectReason:'平台审核意见：其他限制'});
  assert.equal(unknown.category, 'PLATFORM');
  assert.deepEqual(unknown.reasons,['平台审核意见：其他限制']);
});

test('素材空ID和非文本原因不会制造审核信息或抹掉已取得的原因', () => {
  const issues = auditRejectionIssues({}, [null, material({id:''}), material({reasons:['',null,1,'  文案不符合要求  ','文案不符合要求']})]);
  assert.equal(issues.length,1);
  assert.deepEqual(issues[0].reasons,['文案不符合要求']);
});
