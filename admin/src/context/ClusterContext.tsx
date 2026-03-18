import { createContext, useContext, useState, type ReactNode } from "react";
import type { ClusterInfo } from "@/types/api";

const STORAGE_KEY = "cms:selectedClusterId";

interface ClusterContextType {
  currentCluster: ClusterInfo | null;
  setCurrentCluster: (cluster: ClusterInfo) => void;
}

const ClusterContext = createContext<ClusterContextType | null>(null);

export function ClusterProvider({ children }: { children: ReactNode }) {
  const [currentCluster, setCurrentClusterState] = useState<ClusterInfo | null>(null);

  function setCurrentCluster(cluster: ClusterInfo) {
    setCurrentClusterState(cluster);
    localStorage.setItem(STORAGE_KEY, cluster.id);
  }

  return (
    <ClusterContext.Provider value={{ currentCluster, setCurrentCluster }}>
      {children}
    </ClusterContext.Provider>
  );
}

export function useCluster() {
  const ctx = useContext(ClusterContext);
  if (!ctx) throw new Error("useCluster must be used inside ClusterProvider");
  return ctx;
}

export { STORAGE_KEY };
