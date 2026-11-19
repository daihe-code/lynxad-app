import assert from 'node:assert/strict';
import test from 'node:test';
import { accountNavigationForRole, pointRechargePath } from '../src/features/tenant/accountNavigation.ts';

test('主账户的积分充值作为独立菜单显示', () => {
  const menus = accountNavigationForRole('OWNER');
  const recharge = menus.find(item => item.key === 'recharge');
  assert.equal(recharge?.to, pointRechargePath);
  assert.equal(recharge?.label, '积分充值');
  assert.notEqual(recharge?.to, menus.find(item => item.key === 'billing')?.to);
});

test('子账户即使有财务查看权限也没有充值入口', () => {
  for (const role of ['FINANCE', 'OPERATOR', 'MEMBER', 'VIEWER']) {
    assert.equal(accountNavigationForRole(role, ['FINANCE']).some(item => item.key === 'recharge'), false);
  }
});
