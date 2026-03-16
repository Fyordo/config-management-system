import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  Crown,
  Database,
  Fingerprint,
  Hash,
  Layers,
  RefreshCw,
  Server,
  Tag,
} from "lucide-react";
import { raftApi } from "@/api/raft";
import { propertiesApi } from "@/api/properties";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

function StatCard({
  label,
  value,
  icon: Icon,
  loading,
}: {
  label: string;
  value: string | number;
  icon: React.ElementType;
  loading?: boolean;
}) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
            {label}
          </CardTitle>
          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary/10">
            <Icon className="h-4 w-4 text-primary" />
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {loading ? (
          <Skeleton className="h-8 w-16" />
        ) : (
          <p className="text-3xl font-bold font-mono">{value}</p>
        )}
      </CardContent>
    </Card>
  );
}

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

  const { data: constants, isLoading: constantsLoading } = useQuery({
    queryKey: ["constants"],
    queryFn: propertiesApi.constants,
    refetchInterval: 10000,
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
        <Card>
          <CardContent className="p-6">
            {raftLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-5 w-40" />
                <Skeleton className="h-5 w-64" />
                <Skeleton className="h-5 w-48" />
              </div>
            ) : raft ? (
              <div className="space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="flex items-start gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-md bg-muted flex-shrink-0 mt-0.5">
                      <Server className="h-4 w-4 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">Leader ID</p>
                      <p className="text-sm font-mono mt-1 text-foreground">
                        {raft.nodes.find((n) => n.isLeader)?.nodeId ?? "—"}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-md bg-muted flex-shrink-0 mt-0.5">
                      <Fingerprint className="h-4 w-4 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="text-xs text-muted-foreground">Group ID</p>
                      <p className="text-sm font-mono mt-1 text-foreground truncate max-w-[200px]" title={raft.groupId}>
                        {raft.groupId}
                      </p>
                    </div>
                  </div>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground uppercase tracking-wider mb-2">Nodes</p>
                  <div className="flex flex-wrap gap-2">
                    {raft.nodes.sort((a, b) => a.nodeId.localeCompare(b.nodeId)).map((node) => (
                      <div
                        key={node.nodeId}
                        className="flex items-center gap-2 rounded-lg border bg-muted/30 px-3 py-2"
                      >
                        <Activity className="h-4 w-4 text-muted-foreground flex-shrink-0" />
                        <span className="font-mono text-sm font-medium">{node.nodeId}</span>
                        {node.reachable ? (
                          node.isLeader ? (
                            <Badge variant="default" className="gap-1">
                              <Crown className="h-3 w-3" />
                              Leader
                            </Badge>
                          ) : (
                            <Badge variant="secondary">Follower</Badge>
                          )
                        ) : (
                          <Badge variant="destructive" title={node.error}>
                            Unreachable
                          </Badge>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <p className="text-sm text-destructive">Failed to load cluster status</p>
            )}
          </CardContent>
        </Card>
      </section>

      {/* Stats */}
      <section>
        <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-widest mb-3">
          Storage Stats
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            label="Namespaces"
            value={constants?.namespaces.length ?? 0}
            icon={Layers}
            loading={constantsLoading}
          />
          <StatCard
            label="Services"
            value={constants?.services.length ?? 0}
            icon={Server}
            loading={constantsLoading}
          />
          <StatCard
            label="App IDs"
            value={constants?.appIds.length ?? 0}
            icon={Tag}
            loading={constantsLoading}
          />
          <StatCard
            label="Unique Keys"
            value={constants?.keys.length ?? 0}
            icon={Tag}
            loading={constantsLoading}
          />
        </div>
      </section>

      {/* Breakdown */}
      {constants && (
        <section className="grid gap-6 sm:grid-cols-2">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm flex items-center gap-2">
                <Layers className="h-4 w-4 text-primary" />
                Namespaces
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex flex-wrap gap-2">
                {constants.namespaces.length === 0 ? (
                  <p className="text-xs text-muted-foreground">No namespaces yet</p>
                ) : (
                  constants.namespaces.map((ns) => (
                    <Badge key={ns} variant="outline" className="font-mono text-xs">
                      {ns}
                    </Badge>
                  ))
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-sm flex items-center gap-2">
                <Hash className="h-4 w-4 text-primary" />
                App IDs
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex flex-wrap gap-2">
                {constants.appIds.length === 0 ? (
                  <p className="text-xs text-muted-foreground">No app IDs yet</p>
                ) : (
                  constants.appIds.map((id) => (
                    <Badge key={id} variant="outline" className="font-mono text-xs">
                      {id}
                    </Badge>
                  ))
                )}
              </div>
            </CardContent>
          </Card>
        </section>
      )}
    </div>
  );
}
