import { api } from "./client";
import type {
  PropertyConstants,
  PropertyDto,
  PropertyKey,
  PropertyQueryFilter,
  PutPropertyRequest,
} from "@/types/api";

export function propertyKeyToString(key: PropertyKey): string {
  return `${key.version ?? 1}/${key.namespace}/${key.service}/${key.appId}/${key.key}`;
}

export const propertiesApi = {
  query: (filter: PropertyQueryFilter) =>
    api.post<PropertyDto[]>("/v1/property/query", filter),

  get: (key: string) =>
    api.get<PropertyDto>(`/v1/property/query/get?key=${encodeURIComponent(key)}`),

  constants: () =>
    api.get<{ namespaces: string[]; services: string[]; appIds: string[]; keys: string[] }>(
      "/v1/property/query/constants"
    ),

  put: (data: PutPropertyRequest) =>
    api.post<{ result: string; key: string }>("/v1/property/modify/put", data),

  delete: (key: string) =>
    api.delete<{ result: string }>(
      `/v1/property/modify/delete?key=${encodeURIComponent(key)}`
    ),
};

export type { PropertyConstants };
