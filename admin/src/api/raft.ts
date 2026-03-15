import { api } from "./client";
import type { RaftStatus } from "@/types/api";

export const raftApi = {
  status: () => api.get<RaftStatus>("/raft/status"),
};
