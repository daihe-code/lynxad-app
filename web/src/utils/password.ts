/** 与后端 PasswordRules 一致：8–20 位且同时含字母和数字。 */
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/;

export function isValidPassword(raw: string): boolean {
  return PASSWORD_PATTERN.test(raw || "");
}

export const PASSWORD_HINT = "密码须为 8–20 位且同时包含字母和数字";
