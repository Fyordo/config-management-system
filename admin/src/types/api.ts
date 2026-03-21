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

export interface ClusterInfo {
  id: string;
  title: string;
  color: string;
  raftAddress: string;
}

export interface PutPropertyRequest {
  key: PropertyKey;
  value: string;
}

export interface ConnectedAgent {
  namespace: string;
  service: string;
  appId: string;
}

export interface RaftNodeStatus {
  nodeId: string;
  isLeader: boolean | null;
  groupId: string | null;
  reachable: boolean;
  error?: string | null;
  connectedAgents: ConnectedAgent[];
}

export interface RaftGroupStatus {
  groupId: string;
  nodes: RaftNodeStatus[];
  error: string | null;
  color: string | null;
}

export type RaftClusterStatus = Record<string, RaftGroupStatus>;
