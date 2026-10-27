interface Dimensions { width?: number | null; height?: number | null }
interface GeometrySource extends Dimensions { type: string; imageMode?: string }
export interface AssetGeometry {
  orientation: 'portrait' | 'landscape' | 'square' | 'unknown';
  label: string;
  width?: number;
  height?: number;
  ratio?: string;
  ratioValue?: number;
  ratioLabel?: string;
}
const valid = (value?: number | null): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const complete = (value?: Dimensions) => valid(value?.width) && valid(value?.height);
const gcd = (a: number, b: number): number => b ? gcd(b, a % b) : a;

/** File dimensions take precedence; a provider orientation flag supplies a ratio, never invented pixels. */
export function assetGeometry(source: GeometrySource, observed?: Dimensions): AssetGeometry {
  if (!['VIDEO', 'IMAGE', 'CAROUSEL'].includes(source.type)) return { orientation: 'unknown', label: '' };
  const dimensions = complete(observed) ? observed : complete(source) ? source : undefined;
  const width = dimensions?.width as number | undefined, height = dimensions?.height as number | undefined;
  const mode = source.imageMode?.toUpperCase() || '';
  const pair = width && height ? [width, height] : mode.includes('VERTICAL') ? [9, 16] : mode.includes('HORIZONTAL') ? [16, 9] : mode.includes('SQUARE') ? [1, 1] : undefined;
  if (!pair) return { orientation: 'unknown', label: '画幅待获取' };
  const [w, h] = pair, divisor = gcd(w!, h!);
  return { orientation: w! < h! ? 'portrait' : w! > h! ? 'landscape' : 'square',
    label: w! < h! ? '竖屏' : w! > h! ? '横屏' : '方形', width, height,
    ratio: `${w} / ${h}`, ratioValue: w! / h!, ratioLabel: `${w! / divisor}:${h! / divisor}` };
}
