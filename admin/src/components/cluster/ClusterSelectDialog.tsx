import { useQuery } from "@tanstack/react-query";
import { raftApi } from "@/api/raft";
import { useCluster } from "@/context/ClusterContext";
import type { ClusterInfo } from "@/types/api";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";

interface ClusterSelectDialogProps {
  open: boolean;
}

export function ClusterSelectDialog({ open }: ClusterSelectDialogProps) {
  const { setCurrentCluster } = useCluster();

  const { data: clusters, isLoading, isError } = useQuery({
    queryKey: ["clusters"],
    queryFn: raftApi.clusters,
    enabled: open,
  });

  return (
    <Dialog open={open}>
      <DialogContent className="sm:max-w-sm" hideCloseButton>
        <DialogHeader>
          <DialogTitle>Select cluster</DialogTitle>
          <DialogDescription>
            Choose a cluster to work with on the Properties page.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-2 space-y-2">
          {isLoading ? (
            <>
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </>
          ) : isError ? (
            <p className="text-sm text-destructive text-center py-4">
              Failed to load cluster list
            </p>
          ) : clusters && clusters.length > 0 ? (
            clusters
              .slice()
              .sort((a, b) => a.title.localeCompare(b.title))
              .map((cluster: ClusterInfo) => (
                <button
                  key={cluster.id}
                  onClick={() => setCurrentCluster(cluster)}
                  className="w-full flex items-center gap-3 rounded-lg border border-border bg-card px-4 py-3 text-left transition-colors hover:bg-accent/50 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <span
                    className="h-8 w-8 rounded-md flex-shrink-0"
                    style={{ backgroundColor: cluster.color }}
                  />
                  <div>
                    <p className="text-sm font-semibold leading-none">{cluster.title}</p>
                    <p className="text-xs text-muted-foreground mt-1 font-mono">
                      {cluster.raftAddress}
                    </p>
                  </div>
                </button>
              ))
          ) : (
            <p className="text-sm text-muted-foreground text-center py-4">
              No clusters configured
            </p>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
