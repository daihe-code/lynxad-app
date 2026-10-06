import type { TaskVo } from '@/features/creative/creativeApi';

// STANDARD follows the nine stages defined by CreativePipelinePolicy.
const pipeline = [
  ['PARSE', '分析商品'], ['TITLE', '生成标题'], ['COPY', '生成文案'],
  ['TTS', '生成配音'], ['STORYBOARD', '生成分镜'], ['VIDEO', '合成视频'],
  ['COVER', '生成图片'], ['UPLOAD', '准备素材入库'], ['DONE', '完成素材整理'],
] as const;

export function generationProgress(task: TaskVo | null, productCount: number) {
  const completed = new Set((task?.steps || [])
    .filter(step => step.endsWith(':SUCCEEDED')).map(step => step.split(':')[0]));
  const ready = task?.status === 'SUCCEEDED';
  const failed = task?.status === 'FAILED' || task?.status === 'CANCELLED';
  const running = !!task && !ready && !failed;
  const percent = ready ? 100 : Math.min(99, Math.round(
    pipeline.filter(([name]) => completed.has(name)).length / pipeline.length * 100,
  ));
  const stage = !task ? '请确认需要智能生成素材的商品'
    : task.status === 'PENDING' ? '任务已提交，等待开始'
    : task.status === 'CANCELLED' ? '生成已取消'
    : failed ? task.errorMsg || '素材生成失败，可重新生成'
    : ready ? '素材生成完成'
    : `当前阶段：${pipeline.find(([name]) => !completed.has(name))?.[1] || '素材入库'}`;
  const items = task?.items || [];
  const card = (key: 'video' | 'image', name: string, stages: string[], count: number) => {
    const done = productCount > 0 && count >= productCount;
    const stageDone = stages.every(step => completed.has(step));
    const ratio = count > 0 ? count / Math.max(productCount, count)
      : stages.filter(step => completed.has(step)).length / stages.length;
    return {
      key, name, icon: key, count, total: productCount, done,
      percent: done ? 100 : Math.min(99, Math.round(ratio * 100)),
      running: running && !done,
      description: done ? '全部完成' : failed ? '生成已停止'
        : !task ? '确认商品后开始生成'
        : task.status === 'PENDING' ? '等待开始生成'
        : stageDone ? '已生成，正在整理入库…'
        : key === 'video' ? `正在处理视频素材 · ${stage.replace('当前阶段：', '')}`
        : completed.has('VIDEO') ? '正在生成图片素材…' : '等待视频生成后提取封面',
    };
  };
  return {
    percent, stage, ready, failed, running,
    cards: [
      card('video', '短视频', pipeline.slice(0, 6).map(([name]) => name), items.filter(item => item.playUrl).length),
      card('image', '图片', ['COVER'], items.filter(item => item.posterUrl).length),
      { key: 'article', name: '图文', icon: 'article', count: 0, total: 0,
        done: false, percent: 0, running: false, description: '当前标准素材包不包含图文' },
    ],
  };
}
