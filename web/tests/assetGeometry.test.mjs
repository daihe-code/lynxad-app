import test from 'node:test';
import assert from 'node:assert/strict';
import { assetGeometry } from '../src/features/creative/assetGeometry.ts';

test('素材按各自的真实尺寸区分方向，并保留非标准比例', () => {
  for (const [width,height,label,ratio] of [[720,1280,'竖屏','9:16'],[1920,1080,'横屏','16:9'],[1080,1080,'方形','1:1'],[1080,1350,'竖屏','4:5']]) {
    const value=assetGeometry({type:'VIDEO',width,height});
    assert.equal(value.label,label); assert.equal(value.ratioLabel,ratio); assert.equal(value.ratioValue,width/height);
  }
});
test('千川方向标记仅提供展示比例，不能冒充实际分辨率', () => {
  for (const [imageMode,label,ratio] of [['VIDEO_VERTICAL','竖屏','9:16'],['VIDEO_HORIZONTAL','横屏','16:9'],['SQUARE','方形','1:1']]) {
    const value=assetGeometry({type:'VIDEO',imageMode});
    assert.equal(value.label,label); assert.equal(value.ratioLabel,ratio); assert.equal(value.width,undefined); assert.equal(value.height,undefined);
  }
  assert.equal(assetGeometry({type:'VIDEO',width:1920,height:1080,imageMode:'VIDEO_VERTICAL'}).label,'横屏');
});
test('旧记录使用视频文件的完整尺寸，不能混用不同来源的宽高', () => {
  const source={type:'VIDEO',width:1920,height:1080};
  assert.equal(assetGeometry(source,{width:720,height:1280}).label,'竖屏');
  assert.equal(assetGeometry(source,{width:720}).ratioLabel,'16:9');
  assert.equal(assetGeometry({type:'VIDEO',width:720}).orientation,'unknown');
});
test('缺失或无效尺寸不猜测方向，音频与文本不展示画幅', () => {
  for (const value of [0,-1,NaN,Infinity,1.5]) {
    const result=assetGeometry({type:'VIDEO',width:value,height:value});
    assert.equal(result.orientation,'unknown'); assert.equal(result.label,'画幅待获取'); assert.equal(result.ratio,undefined);
  }
  for (const type of ['AUDIO','TEXT']) assert.equal(assetGeometry({type,width:720,height:1280}).label,'');
});
