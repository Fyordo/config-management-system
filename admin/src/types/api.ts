export interface PropertyKey {
  version: number;
  namespace: string;
  service: string;
  appId: string;
  key: string;
}

export interface PropertyValue {
  value: string;
  lastModifiedMs: number;
  version: number;
}

export interface PropertyDto {
  key: PropertyKey;
  value: PropertyValue;
}

export interface PropertyQueryFilter {
  namespaceRegex?: string;
  serviceRegex?: string;
  appIdRegex?: string;
  keyRegex?: string;
  limit?: number;
}

export interface PropertyConstants {
  namespaces: string[];
  services: string[];
  appIds: string[];
  keys: string[];
}

export interface PutPropertyRequest {
  key: PropertyKey;
  value: string;
}

export interface RaftNodeStatus {
  nodeId: string;
  isLeader: boolean | null;
  groupId: string | null;
  reachable: boolean;
  error?: string;
}

export interface RaftStatus {
  groupId: string;
  nodes: RaftNodeStatus[];
}
