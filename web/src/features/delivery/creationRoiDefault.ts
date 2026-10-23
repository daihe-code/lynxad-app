import { roiInputError } from './inputValidation.ts';

/** Follow new platform recommendations only while the input is still an automatic default. */
export function recommendedRoiDefault(current: string, previousDefault: string | undefined, recommended: number | undefined, edited: boolean): { value: string; defaultValue?: string } {
  if (edited || current.trim() && (previousDefault === undefined || Number(current) !== Number(previousDefault))) return { value: current };
  if (recommended === undefined || roiInputError(String(recommended), 'CUSTOM')) return { value: '' };
  const value=String(recommended);
  return { value, defaultValue: value };
}
