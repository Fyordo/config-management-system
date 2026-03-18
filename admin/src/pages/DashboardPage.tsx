import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  Crown,
  Database,
  Fingerprint,
  RefreshCw,
  Server,
} from "lucide-react";
import { raftApi } from "@/api/raft";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export function DashboardPage() {
  const {
    data: raft,
    isLoading: raftLoading,
    refetch: refetchRaft,
    isFetching: raftFetching,
  } = useQuery({
    queryKey: ["raft-status"],
    queryFn: raftApi.status,
    refetchInterval: 5000,
  });

  return (
    <div className="flex flex-col gap-8 p-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-sm text-muted-foreground mt-1">
            System overview and cluster status
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => void refetchRaft()}
          disabled={raftFetching}
          className="gap-2"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${raftFetching ? "animate-spin" : ""}`} />
          Refresh
        </Button>
      </div>

      {/* Raft status */}
      <section>
        <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-widest mb-3">
          Raft Cluster
        </h2>
        {raftLoading ? (
          <Card>
            <CardContent className="p-6">
              <div className="space-y-3">
                <Skeleton className="h-5 w-40" />
                <Skeleton className="h-5 w-64" />
                <Skeleton className="h-5 w-48" />
              </div>
            </CardContent>
          </Card>
        ) : raft ? (
          <div className="grid gap-4 sm:grid-cols-2">
            {Object.entries(raft).sort(([a], [b]) => a.localeCompare(b)).map(([groupName, group]) => (
              <Card key={groupName} className="overflow-hidden">
                {group.color && (
                  <div className="h-1 w-full" style={{ backgroundColor: group.color }} />
                )}
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm flex items-center gap-2">
                    {group.color ? (
                      <span
                        className="h-4 w-4 rounded-sm flex-shrink-0"
                        style={{ backgroundColor: group.color }}
                      />
                    ) : (
                      <Database className="h-4 w-4 text-primary" />
                    )}
                    <span className="font-mono">{groupName}</span>
                    {group.error && (
                      <Badge variant="destructive" className="ml-auto text-xs">Error</Badge>
                    )}
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="flex items-start gap-3">
                    <div className="flex h-8 w-8 items-center justify-center rounded-md bg-muted flex-shrink-0">
                      <Server className="h-3.5 w-3.5 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">Leader</p>
                      <p className="text-sm font-mono mt-0.5 text-foreground">
                        {group.nodes.find((n) => n.isLeader)?.nodeId ?? "—"}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="flex h-8 w-8 items-center justify-center rounded-md bg-muted flex-shrink-0">
                      <Fingerprint className="h-3.5 w-3.5 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">Group ID</p>
                      <p className="text-sm font-mono mt-0.5 text-foreground max-w-[350px]" title={group.groupId}>
                        {group.groupId}
                      </p>
                    </div>
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wider mb-2">Nodes</p>
                    <div className="flex flex-wrap gap-2">
                      {group.nodes
                        .slice()
                        .sort((a, b) => a.nodeId.localeCompare(b.nodeId))
                        .map((node) => (
                          <div
                            key={node.nodeId}
                            className="flex items-center gap-2 rounded-lg border bg-muted/30 px-3 py-2"
                          >
                            <Activity className="h-4 w-4 text-muted-foreground flex-shrink-0" />
                            <span className="font-mono text-sm font-medium">{node.nodeId}</span>
                            {node.reachable ? (
                              node.isLeader ? (
                                <Badge
                                  variant="default"
                                  className="gap-1 border-0"
                                  style={group.color ? { backgroundColor: group.color, color: "#fff" } : undefined}
                                >
                                  <Crown className="h-3 w-3" />
                                  Leader
                                </Badge>
                              ) : (
                                <Badge variant="secondary">Follower</Badge>
                              )
                            ) : (
                              <Badge variant="destructive" title={node.error ?? undefined}>
                                Unreachable
                              </Badge>
                            )}
                          </div>
                        ))}
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <Card>
            <CardContent className="p-6">
              <p className="text-sm text-destructive">Failed to load cluster status</p>
            </CardContent>
          </Card>
        )}
      </section>
    </div>
  );
}
