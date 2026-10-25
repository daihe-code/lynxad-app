import assert from 'node:assert/strict';
import test from 'node:test';
import { creationReachedStep, validReachedStep, creationGoalChanged } from '../src/features/delivery/creationProgress.ts';

const draft = () => ({step:1,reachedStep:3,budget:300,roi2Goal:4.77,smartBidType:'CUSTOM',scheduleType:'FROM_NOW',generationStatus:'NONE',materials:[],publishStatus:'NONE'});
const inputs = () => ({mode:'CUSTOM',budget:'300',roi:'4.77',duration:'long',start:'2026-10-25',end:'2026-11-08'});

test('返回前面查看不会降低草稿或已投放计划的点亮进度', () => {
  assert.equal(creationReachedStep(draft()),3);
  assert.equal(creationReachedStep({...draft(),qcPlanId:'123'}),4);
  assert.equal(creationReachedStep({...draft(),publishStatus:'CREATED'}),4);
  assert.equal(creationReachedStep({...draft(),reachedStep:2}),2);
});

test('修改商品熄灭后续步骤，修改目标仅熄灭素材和投放结果', () => {
  assert.equal(validReachedStep(4),4);
  assert.equal(validReachedStep(4,true,true),1);
  assert.equal(validReachedStep(4,false,true),2);
  assert.equal(validReachedStep(1,false,true),1);
});

test('按实际设置判断修改，格式和长期投放的无效日期不重置进度', () => {
  assert.equal(creationGoalChanged(draft(),inputs()),false);
  assert.equal(creationGoalChanged(draft(),{...inputs(),budget:'300.00',roi:'4.770'}),false);
  for (const changes of [{budget:'301'},{roi:'4.78'},{mode:'CONSERVATIVE'},{duration:'custom'},{budget:''}]) {
    assert.equal(creationGoalChanged(draft(),{...inputs(),...changes}),true);
  }
  const dated={...draft(),scheduleType:'DATE_RANGE',startDate:'2026-10-25',endDate:'2026-11-08'};
  assert.equal(creationGoalChanged(dated,{...inputs(),duration:'custom'}),false);
  assert.equal(creationGoalChanged(dated,{...inputs(),duration:'custom',end:'2026-11-09'}),true);
  assert.equal(creationGoalChanged({...draft(),smartBidType:'CONSERVATIVE'},{...inputs(),mode:'CONSERVATIVE',roi:''}),false);
});

test('历史草稿以已有工作兼容恢复，新草稿不会提前点亮未来步骤', () => {
  assert.equal(creationReachedStep({...draft(),reachedStep:undefined}),3);
  assert.equal(creationReachedStep({...draft(),reachedStep:undefined,budget:undefined,step:2}),2);
  assert.equal(creationReachedStep({...draft(),reachedStep:undefined,budget:undefined,step:1}),1);
});
