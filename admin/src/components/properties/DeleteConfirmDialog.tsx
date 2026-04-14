import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { usePropertiesApi, propertyKeyToString } from "@/api/properties";
import type { DeletePropertyRequest, PropertyDto } from "@/types/api";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface DeleteConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  property: PropertyDto | null;
}

export function DeleteConfirmDialog({
  open,
  onOpenChange,
  property,
}: DeleteConfirmDialogProps) {
  const queryClient = useQueryClient();
  const propertiesApi = usePropertiesApi();

  const mutation = useMutation({
    mutationFn: (data: DeletePropertyRequest) => propertiesApi.delete(data),
    onSuccess: () => {
      toast.success("Property deleted");
      void queryClient.invalidateQueries({ queryKey: ["properties"] });
      void queryClient.invalidateQueries({ queryKey: ["constants"] });
      void queryClient.invalidateQueries({ queryKey: ["properties-count"] });
      onOpenChange(false);
    },
    onError: (err: Error) => {
      toast.error(err.message);
    },
  });

  function handleDelete() {
    if (!property) return;
    mutation.mutate({
      key: property.key,
      prevValue: property.value.value,
    });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Delete Property</DialogTitle>
          <DialogDescription>
            This action cannot be undone. The property will be permanently
            removed from the cluster.
          </DialogDescription>
        </DialogHeader>

        {property && (
          <div className="rounded-md bg-destructive/10 border border-destructive/20 px-4 py-3">
            <p className="text-xs text-muted-foreground mb-1">Key to delete</p>
            <p className="text-sm font-mono break-all text-destructive">
              {propertyKeyToString(property.key)}
            </p>
          </div>
        )}

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={mutation.isPending}
          >
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={handleDelete}
            disabled={mutation.isPending}
          >
            {mutation.isPending ? "Deleting…" : "Delete"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
