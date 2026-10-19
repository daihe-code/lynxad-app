import assert from 'node:assert/strict';
import test from 'node:test';
import { resetPasswordError } from '../src/pages/login/loginRecovery.ts';

test('重置密码只接受 8–20 位且包含字母和数字', () => {
  for (const password of ['', 'abc1234', 'abcdefgh', '12345678', 'a1' + 'x'.repeat(19)]) {
    assert.notEqual(resetPasswordError(password), '', password);
  }
  for (const password of ['abc12345', 'a1' + 'x'.repeat(17), 'a1' + 'x'.repeat(18), 'Abc123!@']) {
    assert.equal(resetPasswordError(password), '', password);
  }
});
