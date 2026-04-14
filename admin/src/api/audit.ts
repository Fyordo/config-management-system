import { api } from "./client";

export const DEFAULT_AUDIT_USER_ID = "admin-user-id";

export interface CreateAuditRequest {
  userId: string;
  namespace: string;
  service: string;
  appId: string;
  key: string;
  prevValue?: string | null;
  newValue: string | null;
}

export interface AuditSearchFilter {
  namespaceRegex?: string;
  serviceRegex?: string;
  appIdRegex?: string;
  keyRegex?: string;
  userId?: string;
  after?: string;
  before?: string;
}

export interface AuditListItem {
  id: number;
  userId: string;
  namespace: string;
  service: string;
  appId: string;
  key: string;
  timestamp: string;
}

export interface AuditDetails extends AuditListItem {
  prevValue: string | null;
  newValue: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}

/** POST /api/v1/audit (admin-api). */
export function createAuditEntry(body: CreateAuditRequest) {
  return api.post<unknown>("/api/v1/audit", body);
}

/** GET /api/v1/audit (admin-api). */
export function searchAudit(
  filter: AuditSearchFilter,
  page: number,
  size: number,
) {
  const params = new URLSearchParams();
  if (filter.namespaceRegex) params.set("namespaceRegex", filter.namespaceRegex);
  if (filter.serviceRegex) params.set("serviceRegex", filter.serviceRegex);
  if (filter.appIdRegex) params.set("appIdRegex", filter.appIdRegex);
  if (filter.keyRegex) params.set("keyRegex", filter.keyRegex);
  if (filter.userId) params.set("userId", filter.userId);
  if (filter.after) params.set("after", filter.after);
  if (filter.before) params.set("before", filter.before);
  params.set("page", String(page));
  params.set("size", String(size));
  return api.get<PageResponse<AuditListItem>>(`/api/v1/audit?${params.toString()}`);
}

/** GET /api/v1/audit/{id} (admin-api). */
export function getAuditById(id: number) {
  return api.get<AuditDetails>(`/api/v1/audit/${id}`);
}
