import assert from 'node:assert/strict';
import test from 'node:test';
import { campaignAccountPreferenceKey, resolveCampaignAccount } from '../src/features/delivery/campaignAccountPreference.ts';

const accounts = [
  { accountId: 'own', status: 'ACTIVE', authorizedByUserId: 'current' },
  { accountId: 'other', status: 'ACTIVE', authorizedByUserId: 'another' },
];

test('返回页面优先恢复用户选择的账户，全部账户也是有效选择', () => {
  assert.equal(resolveCampaignAccount(accounts, 'current', 'other'), 'other');
  assert.equal(resolveCampaignAccount(accounts, 'current', ''), '');
});

test('首次进入仍默认选择当前用户授权的有效账户', () => {
  assert.equal(resolveCampaignAccount([...accounts].reverse(), 'current', null), 'own');
  assert.equal(resolveCampaignAccount(accounts, 'unbound', null), 'own');
});

test('已移除的账户不能从偏好中恢复，回退当前可访问的账户', () => {
  assert.equal(resolveCampaignAccount(accounts, 'current', 'removed'), 'own');
  assert.equal(resolveCampaignAccount([{ ...accounts[1], status: 'EXPIRED' }, accounts[0]], 'current', 'removed'), 'own');
  assert.equal(resolveCampaignAccount([], 'current', 'removed'), '');
  assert.equal(resolveCampaignAccount([{ ...accounts[1], status: 'EXPIRED' }], 'current', null), 'other');
});

test('同一浏览器中不同用户或租户的广告账户选择分别保存', () => {
  const preferences = new Map();
  preferences.set(campaignAccountPreferenceKey('tenant', 'current'), 'other');
  preferences.set(campaignAccountPreferenceKey('tenant', 'another'), '');
  assert.equal(resolveCampaignAccount(accounts, 'current', preferences.get(campaignAccountPreferenceKey('tenant', 'current')) ?? null), 'other');
  assert.equal(resolveCampaignAccount(accounts, 'another', preferences.get(campaignAccountPreferenceKey('tenant', 'another')) ?? null), '');
  assert.equal(preferences.get(campaignAccountPreferenceKey('another-tenant', 'current')), undefined);
  assert.notEqual(campaignAccountPreferenceKey('a:b', 'c'), campaignAccountPreferenceKey('a', 'b:c'));
  assert.equal(campaignAccountPreferenceKey(undefined, 'current'), null);
  assert.equal(campaignAccountPreferenceKey('tenant', undefined), null);
});
