import { api } from "./client";

export const DEFAULT_AUDIT_USER_ID = "admin-user-id";

export interface CreateAuditRequest {
  userId: string;
  namespace: string;
  service: string;
  appId: string;
  key: string;
  prevValue?: string | null;
  newValue: string;
}

/** POST /api/v1/audit (admin-api). */
export function createAuditEntry(body: CreateAuditRequest) {
  return api.post<unknown>("/api/v1/audit", body);
}
