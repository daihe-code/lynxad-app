import test from 'node:test';
import assert from 'node:assert/strict';
import { hasVideoMetadata, videoMetadataLabel, readVideoMetadata } from '../src/features/delivery/videoMetadata.ts';

test('每条视频直接使用各自记录的时长和尺寸，区分竖屏、横屏和方屏',()=>{
  for(const [source,label] of [
    [{width:1080,height:1920,durationSec:18},'18s · 竖屏'],
    [{width:1920,height:1080,durationSec:22.5},'22.5s · 横屏'],
    [{width:1080,height:1080,durationSec:15.04},'15s · 方屏'],
  ]) {assert.equal(hasVideoMetadata(source),true);assert.equal(videoMetadataLabel(source),label);}
});
test('文件读取结果补齐旧记录，不根据封面图片猜测视频方向',()=>{
  assert.equal(hasVideoMetadata({durationSec:18}),false);
  assert.equal(videoMetadataLabel({durationSec:18}), '18s · 画面方向读取中');
  assert.equal(videoMetadataLabel({}, {width:1920,height:1080,duration:17.2}),'17.2s · 横屏');
  assert.equal(videoMetadataLabel({width:1080,height:1920,durationSec:20},{width:1920,height:1080,duration:21}),'21s · 横屏');
});
test('无效时长和零尺寸不显示成0秒或方屏，读取失败明确显示信息缺失',()=>{
  for(const value of [0,-1,NaN,Infinity]) {
    assert.equal(hasVideoMetadata({width:value,height:value,durationSec:value}),false);
    assert.equal(videoMetadataLabel({width:value,height:value,durationSec:value},undefined,true),'时长暂不可用 · 画面方向暂不可用');
  }
});
test('已经取消的元数据读取不创建播放器或发出视频请求',async()=>{
  const controller=new AbortController();controller.abort();
  assert.equal(await readVideoMetadata('https://example.test/video.mp4',controller.signal),undefined);
});
