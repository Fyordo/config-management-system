import { api } from "./client";
import type { RaftClusterStatus } from "@/types/api";

export const raftApi = {
  status: () => api.get<RaftClusterStatus>("/api/v1/cluster/status"),
};
