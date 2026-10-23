// Labels describe returned platform facts; they do not grant permissions.
interface AwemeLabelSource { type?: string; sceneHint?: string; authTypes?: readonly string[]; isAllowMall?: boolean | null }
const labels: Record<string, string> = {
  OFFICIAL: '官方', SELF: '自运营', AWEME_COOPERATOR: '合作达人', COOP: '合作达人',
  COOP_AGENCY: '合作机构', ALLIANCE: '联盟合作', VIDEO: '视频授权', ROOM: '直播授权',
};
export function awemeLabels(aweme?: AwemeLabelSource): string[] {
  if (!aweme) return [];
  const result = (aweme.authTypes || []).map(type => labels[type]).filter((label): label is string => !!label);
  if (!result.length && aweme.type && labels[aweme.type]) result.push(labels[aweme.type]);
  if (aweme.isAllowMall === true) result.push('可投商城');
  return [...new Set(result.length ? result : [aweme.sceneHint || '已授权抖音号'])];
}
