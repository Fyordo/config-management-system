import { useMemo, useState, type CSSProperties } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronsUpDown,
  ChevronDown,
  ChevronUp,
  Eye,
  Filter,
  Loader2,
  X,
} from "lucide-react";
import ReactDiffViewer from "react-diff-viewer-continued";
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  type SortingState,
  useReactTable,
} from "@tanstack/react-table";
import { format } from "date-fns";
import {
  type AuditListItem,
  type AuditSearchFilter,
  getAuditById,
  searchAudit,
} from "@/api/audit";
import { useCluster } from "@/context/ClusterContext";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";

const col = createColumnHelper<AuditListItem>();

const EMPTY_FILTER: AuditSearchFilter = {
  namespaceRegex: "",
  serviceRegex: "",
  appIdRegex: "",
  keyRegex: "",
  userId: "",
  after: "",
  before: "",
};

function SortIcon({ state }: { state: false | "asc" | "desc" }) {
  if (state === "asc") return <ChevronUp className="h-3 w-3" />;
  if (state === "desc") return <ChevronDown className="h-3 w-3" />;
  return <ChevronsUpDown className="h-3 w-3 opacity-40" />;
}

function toIsoOrUndefined(value: string | undefined): string | undefined {
  if (!value) return undefined;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}

export function AuditPage() {
  const { currentCluster } = useCluster();
  const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
  const [showFilters, setShowFilters] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [filterDraft, setFilterDraft] = useState<AuditSearchFilter>(EMPTY_FILTER);
  const [filter, setFilter] = useState<AuditSearchFilter>(EMPTY_FILTER);
  const [detailsId, setDetailsId] = useState<number | null>(null);

  const activeFilter = useMemo<AuditSearchFilter>(
    () => ({
      namespaceRegex: filter.namespaceRegex || undefined,
      serviceRegex: filter.serviceRegex || undefined,
      appIdRegex: filter.appIdRegex || undefined,
      keyRegex: filter.keyRegex || undefined,
      userId: filter.userId || undefined,
      after: toIsoOrUndefined(filter.after),
      before: toIsoOrUndefined(filter.before),
    }),
    [filter],
  );

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ["audit", activeFilter, page, size],
    queryFn: () => searchAudit(activeFilter, page, size),
    placeholderData: (prev) => prev,
  });

  const rows = data?.content ?? [];
  const isDetailsOpen = detailsId !== null;

  const { data: details, isLoading: isDetailsLoading } = useQuery({
    queryKey: ["audit", "details", detailsId],
    queryFn: () => getAuditById(detailsId as number),
    enabled: isDetailsOpen,
  });

  const columns = useMemo(
    () => [
      col.accessor("id", {
        header: "ID",
        cell: (info) => (
          <span className="text-xs font-mono text-muted-foreground">{info.getValue()}</span>
        ),
      }),
      col.accessor("timestamp", {
        header: "Timestamp",
        cell: (info) => {
          const value = info.getValue();
          const parsed = new Date(value);
          if (Number.isNaN(parsed.getTime())) {
            return <span className="text-xs font-mono text-muted-foreground">{value}</span>;
          }
          return (
            <span className="text-xs font-mono text-muted-foreground">
              {format(parsed, "yyyy-MM-dd HH:mm:ss")}
            </span>
          );
        },
      }),
      col.accessor("userId", {
        header: "User",
        cell: (info) => (
          <Badge variant="outline" className="font-mono text-xs">
            {info.getValue()}
          </Badge>
        ),
      }),
      col.accessor("namespace", {
        header: "Namespace",
      }),
      col.accessor("service", {
        header: "Service",
      }),
      col.accessor("appId", {
        header: "App ID",
        cell: (info) => <span className="font-mono text-xs">{info.getValue()}</span>,
      }),
      col.accessor("key", {
        header: "Key",
        cell: (info) => <span className="font-mono text-xs text-primary">{info.getValue()}</span>,
      }),
      col.display({
        id: "actions",
        header: "",
        enableSorting: false,
        cell: ({ row }) => (
          <div className="flex justify-end">
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              title="View details"
              onClick={() => setDetailsId(row.original.id)}
            >
              <Eye className="h-3.5 w-3.5" />
            </Button>
          </div>
        ),
      }),
    ],
    [],
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const activeFilterCount = Object.values(filterDraft).filter(Boolean).length;

  function applyFilters() {
    setFilter(filterDraft);
    setPage(0);
  }

  function clearFilters() {
    setFilterDraft(EMPTY_FILTER);
    setFilter(EMPTY_FILTER);
    setPage(0);
  }

  function setPageSize(nextSize: number) {
    setSize(nextSize);
    setPage(0);
  }

  return (
    <div
      className="flex flex-col h-full"
      style={
        currentCluster
          ? ({ "--primary": currentCluster.color, "--ring": currentCluster.color } as CSSProperties)
          : undefined
      }
    >
      <div className="flex items-center justify-between px-8 py-5 border-b border-border">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Audit</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {isLoading ? "Loading…" : `${data?.totalElements ?? 0} entries`}
            {isFetching && !isLoading && <Loader2 className="inline h-3 w-3 ml-2 animate-spin" />}
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setShowFilters((v) => !v)}
          className="gap-2"
        >
          <Filter className="h-3.5 w-3.5" />
          Filters
          {activeFilterCount > 0 && (
            <Badge variant="default" className="h-4 min-w-4 px-1 text-[10px]">
              {activeFilterCount}
            </Badge>
          )}
        </Button>
      </div>

      <div className="px-8 py-3 border-b border-border space-y-3">
        {showFilters && (
          <>
            <div className="grid grid-cols-2 gap-2 lg:grid-cols-5">
              {(
                [
                  ["namespaceRegex", "Namespace regex"],
                  ["serviceRegex", "Service regex"],
                  ["appIdRegex", "App ID regex"],
                  ["keyRegex", "Key regex"],
                  ["userId", "User ID"],
                ] as const
              ).map(([field, placeholder]) => (
                <div key={field} className="relative">
                  <Input
                    placeholder={placeholder}
                    value={filterDraft[field] ?? ""}
                    onChange={(e) =>
                      setFilterDraft((prev) => ({ ...prev, [field]: e.target.value }))
                    }
                    onKeyDown={(e) => e.key === "Enter" && applyFilters()}
                    className="font-mono text-xs pr-7"
                  />
                  {filterDraft[field] && (
                    <button
                      onClick={() => setFilterDraft((prev) => ({ ...prev, [field]: "" }))}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  )}
                </div>
              ))}
            </div>
            <div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
              <div className="space-y-1">
                <p className="text-xs text-muted-foreground">After</p>
                <Input
                  type="datetime-local"
                  value={filterDraft.after ?? ""}
                  onChange={(e) =>
                    setFilterDraft((prev) => ({ ...prev, after: e.target.value }))
                  }
                />
              </div>
              <div className="space-y-1">
                <p className="text-xs text-muted-foreground">Before</p>
                <Input
                  type="datetime-local"
                  value={filterDraft.before ?? ""}
                  onChange={(e) =>
                    setFilterDraft((prev) => ({ ...prev, before: e.target.value }))
                  }
                />
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Button size="sm" onClick={applyFilters}>
                Apply
              </Button>
              <Button size="sm" variant="outline" onClick={clearFilters}>
                Reset
              </Button>
            </div>
          </>
        )}
      </div>

      <div className="flex-1 overflow-auto">
        <table className="w-full text-sm">
          <thead className="sticky top-0 z-10 bg-card border-b border-border">
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id}>
                {hg.headers.map((header) => (
                  <th
                    key={header.id}
                    className="px-4 py-2.5 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider whitespace-nowrap select-none"
                  >
                    {header.isPlaceholder ? null : (
                      <button
                        className={`flex items-center gap-1 ${header.column.getCanSort() ? "cursor-pointer hover:text-foreground" : ""}`}
                        onClick={header.column.getToggleSortingHandler()}
                      >
                        {flexRender(header.column.columnDef.header, header.getContext())}
                        {header.column.getCanSort() && (
                          <SortIcon state={header.column.getIsSorted()} />
                        )}
                      </button>
                    )}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {isLoading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <tr key={i} className="border-b border-border/50">
                  {columns.map((_, j) => (
                    <td key={j} className="px-4 py-3">
                      <Skeleton className="h-4 w-full max-w-32" />
                    </td>
                  ))}
                </tr>
              ))
            ) : table.getRowModel().rows.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length}
                  className="px-4 py-16 text-center text-muted-foreground"
                >
                  <p className="text-sm">No audit entries found</p>
                  <p className="text-xs mt-1 opacity-60">Try adjusting your filters</p>
                </td>
              </tr>
            ) : (
              table.getRowModel().rows.map((row) => (
                <tr
                  key={row.id}
                  className="border-b border-border/50 hover:bg-accent/40 transition-colors"
                >
                  {row.getVisibleCells().map((cell) => (
                    <td key={cell.id} className="px-4 py-3">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between px-8 py-3 border-t border-border">
        <p className="text-xs text-muted-foreground">
          Page {(data?.number ?? page) + 1} of {Math.max(data?.totalPages ?? 1, 1)}
        </p>
        <div className="flex items-center gap-2">
          {[20, 50, 100].map((opt) => (
            <Button
              key={opt}
              size="sm"
              variant={size === opt ? "default" : "outline"}
              onClick={() => setPageSize(opt)}
            >
              {opt}
            </Button>
          ))}
          <Button
            size="sm"
            variant="outline"
            disabled={data?.first ?? page === 0}
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
          >
            Prev
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={data?.last ?? true}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      </div>

      <Dialog open={isDetailsOpen} onOpenChange={(open) => !open && setDetailsId(null)}>
        <DialogContent className="w-[95vw] max-w-6xl max-h-[85vh] overflow-hidden p-0 gap-0">
          <DialogHeader className="px-6 pt-6 pb-4 border-b border-border">
            <DialogTitle>Audit entry details</DialogTitle>
            <DialogDescription>
              {detailsId !== null ? `Entry #${detailsId}` : "Selected entry"}
            </DialogDescription>
          </DialogHeader>
          <div className="min-h-0 overflow-auto px-6 pb-6 pt-4">
            {isDetailsLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-24 w-full" />
                <Skeleton className="h-24 w-full" />
              </div>
            ) : details ? (
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div><span className="text-muted-foreground">User:</span> <span className="font-mono">{details.userId}</span></div>
                  <div><span className="text-muted-foreground">Timestamp:</span> <span className="font-mono">{format(new Date(details.timestamp), "yyyy-MM-dd HH:mm:ss")}</span></div>
                  <div><span className="text-muted-foreground">Namespace:</span> <span className="font-mono">{details.namespace}</span></div>
                  <div><span className="text-muted-foreground">Service:</span> <span className="font-mono">{details.service}</span></div>
                  <div><span className="text-muted-foreground">App ID:</span> <span className="font-mono">{details.appId}</span></div>
                  <div><span className="text-muted-foreground">Key:</span> <span className="font-mono">{details.key}</span></div>
                </div>
                <div className="rounded-md border border-border bg-muted/20 max-h-[52vh] overflow-auto">
                  <ReactDiffViewer
                    oldValue={details.prevValue ?? ""}
                    newValue={details.newValue ?? ""}
                    splitView
                    showDiffOnly={false}
                    disableWordDiff
                    styles={{
                      contentText: {
                        fontFamily:
                          'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
                        fontSize: "12px",
                      },
                      gutter: {
                        fontFamily:
                          'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
                        fontSize: "12px",
                      },
                      diffContainer: {
                        width: "100%",
                        overflowX: "auto",
                      },
                    }}
                  />
                </div>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">Entry not found.</p>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
