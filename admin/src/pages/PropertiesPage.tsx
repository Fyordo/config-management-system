import React, { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useCluster } from "@/context/ClusterContext";
import { ClusterSelectDialog } from "@/components/cluster/ClusterSelectDialog";
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  flexRender,
  createColumnHelper,
  type SortingState,
} from "@tanstack/react-table";
import { format } from "date-fns";
import {
  ChevronDown,
  ChevronUp,
  ChevronsUpDown,
  Copy,
  Edit2,
  Filter,
  Loader2,
  Plus,
  Search,
  Trash2,
  X,
} from "lucide-react";
import { usePropertiesApi } from "@/api/properties";
import type { PropertyDto, PropertyQueryFilter } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { PropertyDialog } from "@/components/properties/PropertyDialog";
import { DeleteConfirmDialog } from "@/components/properties/DeleteConfirmDialog";

const col = createColumnHelper<PropertyDto>();

const EMPTY_FILTER: PropertyQueryFilter = {
  namespaceRegex: "",
  serviceRegex: "",
  appIdRegex: "",
  keyRegex: "",
  limit: 500,
};

function SortIcon({ state }: { state: false | "asc" | "desc" }) {
  if (state === "asc") return <ChevronUp className="h-3 w-3" />;
  if (state === "desc") return <ChevronDown className="h-3 w-3" />;
  return <ChevronsUpDown className="h-3 w-3 opacity-40" />;
}

function ValueCell({ value }: { value: string }) {
  const isLong = value.length > 60;
  const display = isLong ? value.slice(0, 60) + "…" : value;
  return (
    <TooltipProvider delayDuration={300}>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="font-mono text-xs text-muted-foreground cursor-default">
            {display}
          </span>
        </TooltipTrigger>
        {isLong && (
          <TooltipContent className="max-w-sm break-all font-mono text-xs">
            {value}
          </TooltipContent>
        )}
      </Tooltip>
    </TooltipProvider>
  );
}

export function PropertiesPage() {
  const { currentCluster } = useCluster();
  const propertiesApi = usePropertiesApi();
  const [filterDraft, setFilterDraft] = useState<PropertyQueryFilter>(EMPTY_FILTER);
  const [filter, setFilter] = useState<PropertyQueryFilter>(EMPTY_FILTER);
  const [showFilters, setShowFilters] = useState(false);
  const [globalSearchDraft, setGlobalSearchDraft] = useState("");
  const [globalSearch, setGlobalSearch] = useState("");
  const [sorting, setSorting] = useState<SortingState>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [editProperty, setEditProperty] = useState<PropertyDto | null>(null);
  const [copyProperty, setCopyProperty] = useState<PropertyDto | null>(null);
  const [deleteProperty, setDeleteProperty] = useState<PropertyDto | null>(null);

  const activeFilter: PropertyQueryFilter = {
    namespaceRegex: filter.namespaceRegex || undefined,
    serviceRegex: filter.serviceRegex || undefined,
    appIdRegex: filter.appIdRegex || undefined,
    keyRegex: filter.keyRegex || undefined,
    limit: filter.limit,
  };

  function commitSearch() {
    setGlobalSearch(globalSearchDraft);
  }

  function clearSearch() {
    setGlobalSearchDraft("");
    setGlobalSearch("");
  }

  function commitFilter(field: keyof PropertyQueryFilter, value: string) {
    setFilter((prev) => ({ ...prev, [field]: value }));
  }

  function clearFilterField(field: keyof PropertyQueryFilter) {
    setFilterDraft((prev) => ({ ...prev, [field]: "" }));
    setFilter((prev) => ({ ...prev, [field]: "" }));
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ["properties", currentCluster?.id, activeFilter],
    queryFn: () => propertiesApi.query(activeFilter),
    placeholderData: (prev) => prev,
    enabled: !!currentCluster,
  });

  const columns = useMemo(() => [
    col.accessor((row) => row.key.namespace, {
      id: "namespace",
      header: "Namespace",
      cell: (info) => (
        <Badge variant="outline" className="font-mono text-xs">
          {info.getValue()}
        </Badge>
      ),
    }),
    col.accessor((row) => row.key.service, {
      id: "service",
      header: "Service",
      cell: (info) => (
        <span className="text-sm font-medium">{info.getValue()}</span>
      ),
    }),
    col.accessor((row) => row.key.appId, {
      id: "appId",
      header: "App ID",
      cell: (info) => (
        <span className="text-sm text-muted-foreground font-mono">
          {info.getValue()}
        </span>
      ),
    }),
    col.accessor((row) => row.key.key, {
      id: "key",
      header: "Key",
      cell: (info) => (
        <span className="text-sm font-mono text-primary">{info.getValue()}</span>
      ),
    }),
    col.accessor((row) => row.value.value, {
      id: "value",
      header: "Value",
      cell: (info) => <ValueCell value={info.getValue()} />,
      enableSorting: false,
    }),
    col.accessor((row) => row.value.lastModifiedMs, {
      id: "lastModified",
      header: "Last Modified",
      cell: (info) => {
        const ms = info.getValue();
        if (!ms) return <span className="text-xs text-muted-foreground">—</span>;
        return (
          <span className="text-xs text-muted-foreground font-mono">
            {format(new Date(ms), "MMM d, HH:mm:ss")}
          </span>
        );
      },
    }),
    col.display({
      id: "actions",
      header: "",
      cell: ({ row }) => (
        <div className="flex items-center gap-1 justify-end opacity-0 group-hover:opacity-100 transition-opacity">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            title="Copy property"
            onClick={() => setCopyProperty(row.original)}
          >
            <Copy className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            title="Edit property"
            onClick={() => setEditProperty(row.original)}
          >
            <Edit2 className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-destructive hover:text-destructive"
            title="Delete property"
            onClick={() => setDeleteProperty(row.original)}
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      ),
    }),
  // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  const filteredData = useMemo(() => {
    if (!globalSearch) return data ?? [];
    const q = globalSearch.toLowerCase();
    return (data ?? []).filter(
      (row) =>
        row.key.namespace.toLowerCase().includes(q) ||
        row.key.service.toLowerCase().includes(q) ||
        row.key.appId.toLowerCase().includes(q) ||
        row.key.key.toLowerCase().includes(q) ||
        row.value.value.toLowerCase().includes(q),
    );
  }, [data, globalSearch]);

  const table = useReactTable({
    data: filteredData,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
  });

  const activeFilterCount = Object.entries(filterDraft)
    .filter(([k, v]) => k !== "limit" && !!v)
    .length;

  return (
    <div
      className="flex flex-col h-full"
      style={currentCluster ? ({ "--primary": currentCluster.color, "--ring": currentCluster.color } as React.CSSProperties) : undefined}
    >
      <ClusterSelectDialog open={!currentCluster} />
      {/* Header */}
      <div className="flex items-center justify-between px-8 py-5 border-b border-border">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Properties</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {isLoading ? (
              "Loading…"
            ) : (
              <>
                {filteredData.length} of {data?.length ?? 0} properties
                {isFetching && !isLoading && (
                  <Loader2 className="inline h-3 w-3 ml-2 animate-spin" />
                )}
              </>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
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
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-2">
            <Plus className="h-3.5 w-3.5" />
            New Property
          </Button>
        </div>
      </div>

      {/* Toolbar */}
      <div className="px-8 py-3 border-b border-border space-y-3">
        {/* Global search */}
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
          <Input
            placeholder="Search across all fields… (Enter to apply)"
            value={globalSearchDraft}
            onChange={(e) => setGlobalSearchDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && commitSearch()}
            className="pl-9 pr-8"
          />
          {globalSearchDraft && (
            <button
              onClick={clearSearch}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>

        {/* Regex filters */}
        {showFilters && (
          <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
            {(
              [
                ["namespaceRegex", "Namespace regex"],
                ["serviceRegex", "Service regex"],
                ["appIdRegex", "App ID regex"],
                ["keyRegex", "Key regex"],
              ] as const
            ).map(([field, placeholder]) => (
              <div key={field} className="relative">
                <Input
                  placeholder={placeholder}
                  value={filterDraft[field] ?? ""}
                  onChange={(e) =>
                    setFilterDraft((prev) => ({ ...prev, [field]: e.target.value }))
                  }
                  onKeyDown={(e) => e.key === "Enter" && commitFilter(field, (e.target as HTMLInputElement).value)}
                  className="font-mono text-xs pr-7"
                />
                {filterDraft[field] && (
                  <button
                    onClick={() => clearFilterField(field)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  >
                    <X className="h-3 w-3" />
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto">
        <table className="w-full text-sm">
          <thead className="sticky top-0 z-10 bg-card border-b border-border">
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id}>
                {hg.headers.map((header) => (
                  <th
                    key={header.id}
                    className="px-4 py-2.5 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider whitespace-nowrap select-none"
                    style={{ width: header.getSize() }}
                  >
                    {header.isPlaceholder ? null : (
                      <button
                        className={`flex items-center gap-1 ${header.column.getCanSort() ? "cursor-pointer hover:text-foreground" : ""}`}
                        onClick={header.column.getToggleSortingHandler()}
                      >
                        {flexRender(
                          header.column.columnDef.header,
                          header.getContext()
                        )}
                        {header.column.getCanSort() && (
                          <SortIcon
                            state={header.column.getIsSorted()}
                          />
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
                  <p className="text-sm">No properties found</p>
                  <p className="text-xs mt-1 opacity-60">
                    {activeFilterCount > 0 || globalSearch
                      ? "Try adjusting your filters"
                      : "Create a new property to get started"}
                  </p>
                </td>
              </tr>
            ) : (
              table.getRowModel().rows.map((row) => (
                <tr
                  key={row.id}
                  className="group border-b border-border/50 hover:bg-accent/40 transition-colors"
                >
                  {row.getVisibleCells().map((cell) => (
                    <td key={cell.id} className="px-4 py-3">
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Dialogs */}
      <PropertyDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        editProperty={null}
      />
      <PropertyDialog
        open={!!copyProperty}
        onOpenChange={(v) => !v && setCopyProperty(null)}
        copyProperty={copyProperty}
      />
      <PropertyDialog
        open={!!editProperty}
        onOpenChange={(v) => !v && setEditProperty(null)}
        editProperty={editProperty}
      />
      <DeleteConfirmDialog
        open={!!deleteProperty}
        onOpenChange={(v) => !v && setDeleteProperty(null)}
        property={deleteProperty}
      />
    </div>
  );
}
