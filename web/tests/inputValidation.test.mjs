import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import { budgetInputError, roiInputError, planNameError, scheduleInputError, titlesInputError, platformTitleLength } from '../src/features/delivery/inputValidation.ts';
import { libraryUploadPolicy, libraryFileError, assetFileRules, carouselAudioPolicy, carouselAudioError } from '../src/features/creative/assetUploadTypes.ts';
const cases=JSON.parse(fs.readFileSync(new URL('../../lynx-ad-app/src/test/resources/delivery-input-cases.json',import.meta.url),'utf8'));
test('前后端共用边界案例：创建、转投与修改使用对应规则，300 元保持原值',()=>{
  for (const c of cases) assert.equal(!budgetInputError(c.budget ?? '',c.mode,c.profile) && !roiInputError(c.roi ?? '',c.mode,c.profile),c.valid,JSON.stringify(c));
  for (const amount of ['NaN','Infinity','3e2','-300','300,00','300.']) assert.ok(budgetInputError(amount,'CUSTOM'));
});
test('名称与真实日历日期提前校验，允许同日投放及有效闰日',()=>{
  assert.equal(planNameError('  计划  '),'');assert.ok(planNameError(' '.repeat(50)));assert.ok(planNameError('中'.repeat(31)));
  assert.ok(scheduleInputError('custom','2028-02-30','2028-03-01','2028-01-01'));
  assert.equal(scheduleInputError('custom','2028-02-29','2028-02-29','2028-01-01'),'');
  assert.ok(scheduleInputError('custom','2028-02-29','2028-02-28','2028-01-01'));
});
test('标题在编辑时就检查发布边界，汉字、ASCII及扩展汉字一致计数',()=>{
  assert.equal(platformTitleLength('折叠自行车123'),13);
  assert.equal(platformTitleLength('𠮷'),2);
  assert.ok(titlesInputError(['abc']));assert.equal(titlesInputError(['  折叠自行车  ']),'');
  assert.equal(titlesInputError(['1234567890']),'');assert.ok(titlesInputError(['中'.repeat(56)]));
  assert.ok(titlesInputError(Array(30).fill('商品实拍展示')));
});
test('五种素材库的可选扩展名与服务端文件规则一致，WAV 仍可仅入库',()=>{
  for (const kind of ['VIDEO','IMAGE','AUDIO','TEXT','CAROUSEL']) {
    const wanted=assetFileRules.files.filter(r=>kind === 'CAROUSEL' ? r.kind !== 'VIDEO' : r.kind === kind).map(r=>r.extension);
    assert.deepEqual(libraryUploadPolicy(kind).extensions,wanted);
    for (const ext of wanted) assert.equal(libraryFileError(`文件.${ext.toUpperCase()}`,kind),'');
  }
  assert.ok(libraryFileError('视频.mp4','IMAGE'));assert.equal(libraryFileError('配乐.wav','CAROUSEL'),'');
});

test('图文音频只接受 MP3/M4A，20M 边界可上传，空文件与超限文件提前拦截',()=>{
  assert.deepEqual(carouselAudioPolicy.extensions, ['mp3', 'm4a']);
  for (const name of ['audio.mp3', 'audio.M4A']) {
    assert.equal(carouselAudioError({ name, size: 20 * 1024 ** 2 }), '');
    assert.ok(carouselAudioError({ name, size: 20 * 1024 ** 2 + 1 }));
    assert.ok(carouselAudioError({ name, size: 0 }));
  }
  for (const name of ['audio.wav', 'audio.aac', 'audio.mp3.exe'])
    assert.ok(carouselAudioError({ name, size: 100 }));
});
