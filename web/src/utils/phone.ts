/** 与后端 PhoneNumber 一致：中国大陆 11 位手机号。 */
export const CN_MOBILE_PATTERN = /^1[3-9]\d{9}$/;

export function isCnMobile(raw: string): boolean {
  return CN_MOBILE_PATTERN.test((raw || "").trim());
}

export const PHONE_ERROR = "手机号输入错误，请重新输入";
