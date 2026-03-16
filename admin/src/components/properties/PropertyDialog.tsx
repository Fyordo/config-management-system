import { useState, useEffect } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { propertiesApi, propertyKeyToString } from "@/api/properties";
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

export function PropertyDialog({
  open,
  onOpenChange,
  editProperty,
  copyProperty,
}: PropertyDialogProps) {
  const [form, setForm] = useState<FormState>(emptyForm);
  const queryClient = useQueryClient();
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
      onOpenChange(false);
    },
    onError: (err: Error) => {
      toast.error(err.message);
    },
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!form.namespace || !form.service || !form.appId || !form.key || !form.value) {
      toast.error("All fields are required");
      return;
    }
    mutation.mutate({
      key: {
        version: 1,
        namespace: form.namespace,
        service: form.service,
        appId: form.appId,
        key: form.key,
      },
      value: form.value,
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

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
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
            <Label htmlFor="value">Value</Label>
            <Textarea
              id="value"
              placeholder='{"host": "localhost", "port": 5432}'
              value={form.value}
              onChange={(e) => set("value", e.target.value)}
              rows={5}
              className="font-mono text-sm"
            />
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
