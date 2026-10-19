import { apiGet, apiPut } from '@/api/http';
export interface InvoiceProfile { title: string; type: string; taxNo: string; bank: string }
export interface LegalProfile {
  tenantId: string; companyName: string; creditCode: string;
  verificationStatus: 'UNSUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewReason: string | null; revision: number;
  nickname?: string; industry?: string; email?: string; companyScale?: string; contactPhone?: string;
  submittedAt: string | null; reviewedAt: string | null; invoice: InvoiceProfile | null;
}
export interface CompanyProfile { companyName: string; creditCode: string; nickname: string; industry: string; email: string; companyScale: string | null }
const root = '/api/v1/tenant/account';
export const getLegalProfile = () => apiGet<LegalProfile>(root + '/legal-profile');
export const saveCompanyProfile = (body: CompanyProfile) => apiPut<LegalProfile>(root + '/company', body);
export const saveInvoiceProfile = (body: InvoiceProfile) => apiPut<LegalProfile>(root + '/invoice', body);
