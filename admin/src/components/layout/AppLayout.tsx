import { useEffect } from "react";
import { Outlet } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Check, ChevronsUpDown, Moon, Sun } from "lucide-react";
import { Sidebar } from "./Sidebar";
import { useCluster, STORAGE_KEY } from "@/context/ClusterContext";
import { useTheme } from "@/context/ThemeContext";
import { raftApi } from "@/api/raft";
import type { ClusterInfo } from "@/types/api";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export function AppLayout() {
  const { currentCluster, setCurrentCluster } = useCluster();
  const { theme, toggleTheme } = useTheme();

  const { data: clusters } = useQuery({
    queryKey: ["clusters"],
    queryFn: raftApi.clusters,
  });

  useEffect(() => {
    if (!clusters?.length || currentCluster) return;
    const savedId = localStorage.getItem(STORAGE_KEY);
    const match = savedId ? clusters.find((c) => c.id === savedId) : null;
    if (match) setCurrentCluster(match);
  }, [clusters]);

  const sorted = clusters?.slice().sort((a, b) => a.title.localeCompare(b.title)) ?? [];

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-10 items-center justify-end gap-2 border-b border-border px-4 shrink-0">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={toggleTheme}
            title={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
          >
            {theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="flex items-center gap-2 rounded-md px-2 py-1 text-xs transition-colors hover:bg-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                {currentCluster ? (
                  <>
                    <span
                      className="h-2.5 w-2.5 rounded-full flex-shrink-0"
                      style={{ backgroundColor: currentCluster.color }}
                    />
                    <span className="font-semibold text-foreground">{currentCluster.title}</span>
                    <span className="text-muted-foreground font-mono hidden sm:inline">
                      {currentCluster.raftAddress}
                    </span>
                  </>
                ) : (
                  <span className="text-muted-foreground/60">no cluster selected</span>
                )}
                <ChevronsUpDown className="h-3 w-3 text-muted-foreground ml-0.5" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-64">
              <DropdownMenuLabel className="text-xs font-normal text-muted-foreground">
                Switch cluster
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              {sorted.map((cluster: ClusterInfo) => (
                <DropdownMenuItem
                  key={cluster.id}
                  onClick={() => setCurrentCluster(cluster)}
                  className="flex items-center gap-3 cursor-pointer"
                >
                  <span
                    className="h-5 w-5 rounded flex-shrink-0"
                    style={{ backgroundColor: cluster.color }}
                  />
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-sm leading-none">{cluster.title}</p>
                    <p className="text-xs text-muted-foreground font-mono mt-0.5 truncate">
                      {cluster.raftAddress}
                    </p>
                  </div>
                  {currentCluster?.id === cluster.id && (
                    <Check className="h-3.5 w-3.5 text-primary flex-shrink-0" />
                  )}
                </DropdownMenuItem>
              ))}
              {sorted.length === 0 && (
                <p className="px-2 py-3 text-xs text-muted-foreground text-center">
                  No clusters available
                </p>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </header>
        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
