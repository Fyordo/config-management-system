import { useState, useEffect, useMemo } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { usePropertiesApi, propertyKeyToString } from "@/api/properties";
import type { PropertyDto } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface PropertyDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editProperty?: PropertyDto | null;
  copyProperty?: PropertyDto | null;
  forcePrevValue?: string | null;
}

interface FormState {
  namespace: string;
  service: string;
  appId: string;
  key: string;
  value: string;
}

const emptyForm: FormState = {
  namespace: "",
  service: "",
  appId: "",
  key: "",
  value: "",
};

type ValueType = "number" | "boolean" | "json" | "string" | "empty";

type ParsedValue = {
  type: ValueType;
  formattedValue: string;
  highlightedHtml: string;
};

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function highlightJson(value: string): string {
  const escaped = escapeHtml(value);
  return escaped.replace(
    /("(?:\\u[\da-fA-F]{4}|\\[^u]|[^\\"])*"(\s*:)?|\btrue\b|\bfalse\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g,
    (match, _group, isKey: string | undefined) => {
      if (isKey) return `<span class="text-blue-700 dark:text-sky-400">${match}</span>`;
      if (match.startsWith('"')) {
        return `<span class="text-emerald-700 dark:text-emerald-400">${match}</span>`;
      }
      if (match === "true" || match === "false") {
        return `<span class="text-violet-700 dark:text-violet-400">${match}</span>`;
      }
      if (match === "null") return `<span class="text-slate-500 dark:text-muted-foreground">${match}</span>`;
      return `<span class="text-amber-700 dark:text-amber-400">${match}</span>`;
    },
  );
}

function parseValue(rawValue: string): ParsedValue {
  const trimmed = rawValue.trim();
  if (!trimmed) {
    return {
      type: "empty",
      formattedValue: rawValue,
      highlightedHtml: '<span class="text-muted-foreground">Empty value</span>',
    };
  }

  if (/^[+-]?\d+(?:\.\d+)?$/.test(trimmed)) {
    const formattedValue = `${Number(trimmed)}`;
    return {
      type: "number",
      formattedValue,
      highlightedHtml: `<span class="text-amber-700 dark:text-amber-400">${escapeHtml(formattedValue)}</span>`,
    };
  }

  if (/^(true|false)$/i.test(trimmed)) {
    const formattedValue = trimmed.toLowerCase();
    return {
      type: "boolean",
      formattedValue,
      highlightedHtml: `<span class="text-violet-700 dark:text-violet-400">${formattedValue}</span>`,
    };
  }

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (parsed !== null && typeof parsed === "object") {
      const formattedValue = JSON.stringify(parsed, null, 2);
      return {
        type: "json",
        formattedValue,
        highlightedHtml: highlightJson(formattedValue),
      };
    }
  } catch {
    // Not a valid JSON object/array, treat as a plain string.
  }

  return {
    type: "string",
    formattedValue: rawValue,
    highlightedHtml: `<span class="text-foreground">${escapeHtml(rawValue)}</span>`,
  };
}

export function PropertyDialog({
  open,
  onOpenChange,
  editProperty,
  copyProperty,
  forcePrevValue,
}: PropertyDialogProps) {
  const [form, setForm] = useState<FormState>(emptyForm);
  const [isPreviewVisible, setIsPreviewVisible] = useState(true);
  const queryClient = useQueryClient();
  const propertiesApi = usePropertiesApi();
  const isEdit = !!editProperty;

  useEffect(() => {
    if (editProperty) {
      setForm({
        namespace: editProperty.key.namespace,
        service: editProperty.key.service,
        appId: editProperty.key.appId,
        key: editProperty.key.key,
        value: editProperty.value.value,
      });
    } else if (copyProperty) {
      setForm({
        namespace: copyProperty.key.namespace,
        service: copyProperty.key.service,
        appId: "",
        key: copyProperty.key.key,
        value: copyProperty.value.value,
      });
    } else {
      setForm(emptyForm);
    }
  }, [editProperty, copyProperty, open]);

  const mutation = useMutation({
    mutationFn: propertiesApi.put,
    onSuccess: () => {
      toast.success(isEdit ? "Property updated" : "Property created");
      void queryClient.invalidateQueries({ queryKey: ["properties"] });
      void queryClient.invalidateQueries({ queryKey: ["constants"] });
      void queryClient.invalidateQueries({ queryKey: ["properties-count"] });
      void queryClient.invalidateQueries({ queryKey: ["audit"] });
      onOpenChange(false);
    },
    onError: (err: Error) => {
      toast.error(`An error occured: ${err.message}`);
    },
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.namespace || !form.service || !form.appId || !form.key || !form.value) {
      toast.error("All fields are required");
      return;
    }
    const normalizedValue = parseValue(form.value).formattedValue;
    if (normalizedValue !== form.value) {
      set("value", normalizedValue);
    }
    mutation.mutate({
      key: {
        version: 1,
        namespace: form.namespace,
        service: form.service,
        appId: form.appId,
        key: form.key,
      },
      value: normalizedValue,
      ...((isEdit && editProperty) || forcePrevValue !== undefined
        ? { prevValue: forcePrevValue ?? editProperty?.value.value ?? null }
        : {}),
    });
  }

  function set(field: keyof FormState, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  const previewKey = form.namespace
    ? propertyKeyToString({
        version: 1,
        namespace: form.namespace || "…",
        service: form.service || "…",
        appId: form.appId || "…",
        key: form.key || "…",
      })
    : null;
  const parsedValue = useMemo(() => parseValue(form.value), [form.value]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className={isPreviewVisible ? "sm:max-w-4xl" : "sm:max-w-lg"}>
        <DialogHeader>
          <DialogTitle>
            {isEdit ? "Edit Property" : copyProperty ? "Copy Property" : "New Property"}
          </DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update the value of an existing property."
              : copyProperty
              ? "Copied from an existing property. Fill in the App ID to create a new entry."
              : "Create a new config property in the system."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="namespace">Namespace</Label>
              <Input
                id="namespace"
                placeholder="prod"
                value={form.namespace}
                onChange={(e) => set("namespace", e.target.value)}
                disabled={isEdit}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="service">Service</Label>
              <Input
                id="service"
                placeholder="auth"
                value={form.service}
                onChange={(e) => set("service", e.target.value)}
                disabled={isEdit}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="appId">App ID</Label>
              <Input
                id="appId"
                placeholder="app-1"
                value={form.appId}
                onChange={(e) => set("appId", e.target.value)}
                disabled={isEdit}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="key">Key</Label>
              <Input
                id="key"
                placeholder="db.url"
                value={form.key}
                onChange={(e) => set("key", e.target.value)}
                disabled={isEdit}
              />
            </div>
          </div>

          {previewKey && (
            <div className="rounded-md bg-muted px-3 py-2">
              <p className="text-[10px] text-muted-foreground mb-0.5">Key path</p>
              <p className="text-xs font-mono text-foreground break-all">{previewKey}</p>
            </div>
          )}

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="value">Value</Label>
              <div className="flex items-center gap-2">
                <span className="rounded-md border border-border bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">
                  {parsedValue.type}
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setIsPreviewVisible((prev) => !prev)}
                >
                  {isPreviewVisible ? "Hide preview" : "Show preview"}
                </Button>
              </div>
            </div>
            <div className={isPreviewVisible ? "grid gap-3 md:grid-cols-2" : "block"}>
              <Textarea
                id="value"
                placeholder='{"host": "localhost", "port": 5432}'
                value={form.value}
                onChange={(e) => set("value", e.target.value)}
                rows={15}
                className="font-mono text-sm"
              />
              {isPreviewVisible && (
                <div className="rounded-md border border-border bg-muted/40 px-3 py-2">
                  <p className="mb-1 text-[10px] uppercase tracking-wide text-muted-foreground">
                    Preview
                  </p>
                  <pre
                    className="whitespace-pre-wrap break-words font-mono text-xs leading-5 text-foreground"
                    dangerouslySetInnerHTML={{ __html: parsedValue.highlightedHtml }}
                  />
                </div>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "Saving…" : isEdit ? "Update" : "Create"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
