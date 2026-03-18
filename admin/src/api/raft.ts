import { api } from "./client";
import type { ClusterInfo, RaftClusterStatus } from "@/types/api";

export const raftApi = {
  status: () => api.get<RaftClusterStatus>("/api/v1/cluster/status"),
  clusters: () => api.get<ClusterInfo[]>("/api/v1/cluster/names"),
};
