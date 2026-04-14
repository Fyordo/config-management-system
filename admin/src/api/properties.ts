import { useMemo } from "react";
import { createAuditEntry, DEFAULT_AUDIT_USER_ID } from "./audit";
import { createClient } from "./client";
import { useCluster } from "@/context/ClusterContext";
import type {
  DeletePropertyRequest,
  PropertyConstants,
  PropertyDto,
  PropertyKey,
  PropertyQueryFilter,
  PutPropertyRequest,
} from "@/types/api";

export function propertyKeyToString(key: PropertyKey): string {
  return `${key.version ?? 1}/${key.namespace}/${key.service}/${key.appId}/${key.key}`;
}

export function createPropertiesApi(baseUrl: string) {
  const api = createClient(baseUrl);
  return {
    query: (filter: PropertyQueryFilter) =>
      api.post<PropertyDto[]>("/v1/property/query", filter),

    get: (key: string) =>
      api.get<PropertyDto>(`/v1/property/query/get?key=${encodeURIComponent(key)}`),

    constants: () =>
      api.get<{ namespaces: string[]; services: string[]; appIds: string[]; keys: string[] }>(
        "/v1/property/query/constants"
      ),

    put: async (data: PutPropertyRequest) => {
      const result = await api.post<{ result: string; key: string }>(
        "/v1/property/modify/put",
        { key: data.key, value: data.value },
      );
      void createAuditEntry({
        userId: DEFAULT_AUDIT_USER_ID,
        namespace: data.key.namespace,
        service: data.key.service,
        appId: data.key.appId,
        key: data.key.key,
        prevValue: data.prevValue ?? null,
        newValue: data.value,
      }).catch((err: unknown) => {
        console.warn("[audit] Failed to record property change", err);
      });
      return result;
    },

    delete: async (data: DeletePropertyRequest) => {
      const result = await api.post<{ result: string }>("/v1/property/modify/delete", data.key);
      void createAuditEntry({
        userId: DEFAULT_AUDIT_USER_ID,
        namespace: data.key.namespace,
        service: data.key.service,
        appId: data.key.appId,
        key: data.key.key,
        prevValue: data.prevValue ?? null,
        newValue: null,
      }).catch((err: unknown) => {
        console.warn("[audit] Failed to record property deletion", err);
      });
      return result;
    },
  };
}

export function usePropertiesApi() {
  const { currentCluster } = useCluster();
  return useMemo(
    () => createPropertiesApi(currentCluster?.raftAddress ?? ""),
    [currentCluster?.raftAddress],
  );
}

export type { PropertyConstants };
