import type { UploadPolicy } from './creativeApi';
import fileRules from '../../../../lynx-ad-app/src/main/resources/asset-file-policy.json' with { type: 'json' };

export interface FileMetadata { width?: number; height?: number; duration?: number; characters?: number; posterDataUrl?: string; summary: string }
export function targetError(file: File, sync: boolean) {
  const rule = fileRules.files.find(r => r.extension === file.name.split('.').pop()?.toLowerCase());
  return sync && rule && rule.kind !== 'TEXT' && !rule.qianchuanSupported ? '千川仅支持 MP3/M4A 音频，请转换 WAV 或取消同步账户后仅保存到平台' : '';
}
function fail(message = '文件内容损坏或与扩展名不匹配'): never { throw new Error(message); }
function ascii(bytes: Uint8Array, start: number, length: number) { return String.fromCharCode(...bytes.slice(start, start + length)); }
function dimensions(width: number, height: number, policy: UploadPolicy) {
  if (!width || !height || width * height > policy.maxPixels) fail(`尺寸无效或超过 ${(policy.maxPixels / 10000).toLocaleString()} 万像素，请缩小后上传`);
}
function imageDimensions(bytes: Uint8Array, png: boolean) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (png) {
    if (bytes.length < 24 || ascii(bytes, 12, 4) !== 'IHDR') fail();
    return [view.getUint32(16), view.getUint32(20)] as const;
  }
  // JPEG SOF dimensions are read before allocating decoded pixels.
  let pos = 2;
  while (pos + 4 <= bytes.length) {
    if (bytes[pos] !== 255) fail();
    while (bytes[pos] === 255) pos++;
    const marker = bytes[pos++]!;
    if (marker === 216 || marker === 1 || (marker >= 208 && marker <= 215)) continue;
    if (marker === 217 || marker === 218) break;
    if (pos + 2 > bytes.length) fail();
    const length = view.getUint16(pos);
    if (length < 2 || pos + length > bytes.length) fail();
    if ([192, 193, 194, 195, 197, 198, 199, 201, 202, 203, 205, 206, 207].includes(marker)) {
      if (length < 8) fail();
      return [view.getUint16(pos + 5), view.getUint16(pos + 3)] as const;
    }
    pos += length;
  }
  return fail('无法读取图片尺寸，请重新导出图片');
}
export function decodeText(bytes: Uint8Array) {
  try {
    if (bytes[0] === 255 && bytes[1] === 254) return new TextDecoder('utf-16le', { fatal: true }).decode(bytes.slice(2));
    if (bytes[0] === 254 && bytes[1] === 255) return new TextDecoder('utf-16be', { fatal: true }).decode(bytes.slice(2));
    try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes); }
    catch { return new TextDecoder('gb18030', { fatal: true }).decode(bytes); }
  } catch { return fail('文本编码无法识别，请使用 UTF-8、带 BOM 的 UTF-16 或 GB18030'); }
}
interface Box { type: string; start: number; end: number }
function boxes(bytes: Uint8Array, start = 0, end = bytes.length): Box[] {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength), result: Box[] = [];
  while (start < end) {
    if (start + 8 > end || result.length > 10000) fail('媒体元数据损坏或过于复杂，请重新导出文件');
    let size = view.getUint32(start), header = 8;
    if (size === 1) { if (start + 16 > end) fail(); size = Number(view.getBigUint64(start + 8)); header = 16; }
    if (size === 0) size = end - start;
    if (!Number.isSafeInteger(size) || size < header || start + size > end) fail();
    result.push({ type: ascii(bytes, start + 4, 4), start: start + header, end: start + size }); start += size;
  }
  return result;
}
async function mp4Codec(file: File, video: boolean, policy: UploadPolicy) {
  let position = 0, moov: Uint8Array | undefined, media = false, count = 0;
  while (position < file.size) {
    if (++count > 10000) fail('媒体元数据过于复杂，请重新导出文件');
    const header = new Uint8Array(await file.slice(position, position + 16).arrayBuffer());
    if (header.length < 8) fail();
    const view = new DataView(header.buffer); let size = view.getUint32(0), length = 8;
    if (size === 1) { if (header.length < 16) fail(); size = Number(view.getBigUint64(8)); length = 16; }
    if (!size) size = file.size - position;
    if (!Number.isSafeInteger(size) || size < length || size > file.size - position) fail();
    const type = ascii(header, 4, 4);
    if (type === 'mdat' && size > length) media = true;
    if (type === 'moov') {
      if (size > 16 * 1024 ** 2) fail('媒体元数据超过预检容量，请重新导出文件');
      moov = new Uint8Array(await file.slice(position + length, position + size).arrayBuffer());
    }
    position += size;
  }
  if (!moov || !media) fail('媒体缺少轨道信息或内容，请检查文件是否完整');
  const child = (box: Box, type: string) => boxes(moov!, box.start, box.end).find(b => b.type === type);
  for (const track of boxes(moov).filter(b => b.type === 'trak')) {
    const mdia = child(track, 'mdia'); if (!mdia) continue;
    const handler = child(mdia, 'hdlr');
    if (!handler || handler.end < handler.start + 12 || ascii(moov, handler.start + 8, 4) !== (video ? 'vide' : 'soun')) continue;
    const minf = child(mdia, 'minf'), stbl = minf && child(minf, 'stbl'), stsd = stbl && child(stbl, 'stsd');
    if (!stsd || stsd.end < stsd.start + 16) fail('媒体轨道编码信息缺失');
    const entry = boxes(moov, stsd.start + 8, stsd.end)[0];
    if (entry && (video ? ['avc1', 'avc3'].includes(entry.type) : entry.type === 'mp4a')) return;
    fail(video ? `MP4 需 ${policy.videoCodec.toUpperCase()} 编码，请转换后上传` : `M4A 需 ${policy.m4aCodec.toUpperCase()} 编码，请转换后上传`);
  }
  fail(video ? 'MP4 中未找到视频轨道' : 'M4A 中未找到音频轨道');
}
async function wavFormat(file: File) {
  const bytes = new Uint8Array(await file.slice(0, Math.min(file.size, 65536)).arrayBuffer()), view = new DataView(bytes.buffer);
  if (bytes.length < 12 || view.getUint32(4, true) + 8 !== file.size) fail('WAV 文件长度不完整或声明不一致，请重新导出');
  let pos = 12;
  while (pos + 8 <= bytes.length) {
    const type = ascii(bytes, pos, 4), size = view.getUint32(pos + 4, true), data = pos + 8;
    if (size > file.size - data) fail();
    if (type === 'fmt ') {
      if (size < 16 || data + 16 > bytes.length) fail();
      let codec = view.getUint16(data, true);
      if (codec === 65534) { if (size < 40 || data + 40 > bytes.length) fail(); codec = view.getUint16(data + 24, true); }
      if (![1, 3].includes(codec)) fail('WAV 需 PCM 编码，请转换后上传');
      if (!view.getUint16(data + 2, true) || !view.getUint32(data + 4, true)) fail('WAV 声道数或采样率无效');
      return;
    }
    pos = data + size + (size % 2);
  }
  fail('无法读取 WAV 编码信息，请重新导出文件');
}
function mediaMetadata(file: File, video: boolean): Promise<FileMetadata> {
  return new Promise((resolve, reject) => {
    const media = document.createElement(video ? 'video' : 'audio'), url = URL.createObjectURL(file);
    const finish = (error?: string) => {
      clearTimeout(timer); media.onloadeddata = null; media.onerror = null;
      const result: FileMetadata = { width: video ? (media as HTMLVideoElement).videoWidth : undefined, height: video ? (media as HTMLVideoElement).videoHeight : undefined, duration: media.duration, summary: '' };
      // Reuse the preflight decode; a batch must not start a second decoder for each thumbnail.
      if (!error && video && result.width && result.height) {
        try {
          const canvas = document.createElement('canvas'), scale = Math.min(1, 280 / Math.max(result.width, result.height));
          canvas.width = Math.round(result.width * scale); canvas.height = Math.round(result.height * scale);
          const context = canvas.getContext('2d');
          if (context) { context.drawImage(media as HTMLVideoElement, 0, 0, canvas.width, canvas.height); result.posterDataUrl = canvas.toDataURL('image/jpeg', .8); }
        } catch { /* A missing thumbnail must not reject an otherwise valid video. */ }
      }
      media.removeAttribute('src'); media.load(); URL.revokeObjectURL(url);
      if (error) reject(new Error(error)); else resolve(result);
    };
    const timer = setTimeout(() => finish('媒体读取超时，请检查文件是否完整后重试'), 30000);
    media.preload = 'auto'; media.muted = true;
    media.onloadeddata = () => finish(!Number.isFinite(media.duration) || media.duration <= 0 ? '媒体时长无效，请重新导出文件' : undefined);
    media.onerror = () => finish('媒体无法解码，请检查格式、编码或文件是否损坏');
    media.src = url; media.load();
  });
}
/** Local checks happen before init/storage requests; server decoding remains authoritative. */
export async function preflight(file: File, policy: UploadPolicy): Promise<FileMetadata> {
  const ext = file.name.split('.').pop()?.toLowerCase(), rule = policy.files.find(r => r.extension === ext);
  if (!rule) fail('不支持该文件格式，请参照上传说明');
  if (!file.size) fail('文件为空');
  if (file.size > rule.maxBytes) fail(`文件超过 ${rule.maxBytes / 1024 ** 2}MB 限制（当前 ${file.size.toLocaleString()} 字节）`);
  const header = new Uint8Array(await file.slice(0, 32).arrayBuffer());
  if (rule.kind === 'TEXT') {
    const text = decodeText(new Uint8Array(await file.arrayBuffer()));
    if (!text.trim() || /[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]/.test(text)) fail('文案为空或包含二进制／控制字符');
    if (text.length > policy.maxTextChars) fail(`文案最多 ${policy.maxTextChars.toLocaleString()} 字符，请拆分后上传`);
    return { characters: text.length, summary: `${text.length.toLocaleString()} 字符 · 文案预检通过` };
  }
  if (rule.kind === 'IMAGE') {
    const png = header[0] === 137 && ascii(header, 1, 7) === 'PNG\r\n\x1a\n', jpg = header[0] === 255 && header[1] === 216 && header[2] === 255;
    if (!(ext === 'png' ? png : jpg)) fail();
    const [width, height] = imageDimensions(new Uint8Array(await file.arrayBuffer()), png);
    dimensions(width, height, policy);
    let bitmap: ImageBitmap;
    try { bitmap = await createImageBitmap(file); } catch { return fail('图片解码失败，请重新导出图片'); }
    bitmap.close();
    return { width, height, summary: `${width}×${height} · 图片预检通过` };
  }
  if (ext === 'mp4' || ext === 'm4a') {
    if (header.length < 16 || ascii(header, 4, 4) !== 'ftyp') fail();
    await mp4Codec(file, ext === 'mp4', policy);
  }
  else if (ext === 'wav') {
    if (ascii(header, 0, 4) !== 'RIFF' || ascii(header, 8, 4) !== 'WAVE') fail();
    await wavFormat(file);
  }
  else if (ext === 'mp3') { if (!(ascii(header, 0, 3) === 'ID3' || (header[0] === 255 && (header[1]! & 224) === 224))) fail(); }
  const metadata = await mediaMetadata(file, rule.kind === 'VIDEO');
  if (rule.kind === 'VIDEO') dimensions(metadata.width!, metadata.height!, policy);
  metadata.summary = `${metadata.width ? `${metadata.width}×${metadata.height} · ` : ''}${metadata.duration!.toFixed(2)} 秒 · 预检通过，上传后完整校验编码与内容`;
  return metadata;
}
