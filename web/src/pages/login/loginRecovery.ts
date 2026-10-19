export function resetPasswordError(password: string): string {
  return password.length >= 8 && password.length <= 20 && /[A-Za-z]/.test(password) && /\d/.test(password)
    ? ""
    : "密码需为 8–20 位，且包含字母和数字";
}
