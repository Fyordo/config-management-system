import { useEffect, useState } from "react";
import { NavLink } from "react-router-dom";
import {
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  LayoutDashboard,
  Settings2,
  Server,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";

const navItems = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/properties", label: "Properties", icon: Settings2, end: false },
  { to: "/audit", label: "Audit", icon: ClipboardList, end: false },
];

const SIDEBAR_EXPANDED_KEY = "cms:admin:sidebar-expanded";

export function Sidebar() {
  const [expanded, setExpanded] = useState<boolean>(() => {
    if (typeof window === "undefined") return false;
    return localStorage.getItem(SIDEBAR_EXPANDED_KEY) === "1";
  });

  useEffect(() => {
    localStorage.setItem(SIDEBAR_EXPANDED_KEY, expanded ? "1" : "0");
  }, [expanded]);

  return (
    <aside
      className={cn(
        "flex h-full flex-col bg-sidebar border-r border-sidebar-border transition-[width] duration-200",
        expanded ? "w-52" : "w-14",
      )}
    >
      <div
        className={cn(
          "flex h-14 items-center border-b border-sidebar-border px-2",
          expanded ? "justify-between" : "justify-center",
        )}
      >
        <div className="flex h-7 w-7 items-center justify-center rounded-md bg-primary">
          <Server className="h-4 w-4 text-primary-foreground shrink-0" />
        </div>
        {expanded && (
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground"
            onClick={() => setExpanded(false)}
            title="Collapse sidebar"
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
        )}
      </div>

      <nav className="flex-1 overflow-auto py-3 px-2">
        <ul className="space-y-0.5">
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <li key={to}>
              <NavLink
                to={to}
                end={end}
                title={label}
                className={({ isActive }) =>
                  cn(
                    "flex items-center rounded-md p-2 transition-colors",
                    expanded ? "justify-start gap-2" : "justify-center",
                    isActive
                      ? "bg-sidebar-accent text-sidebar-primary"
                      : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
                  )
                }
              >
                <Icon className="h-4 w-4 shrink-0" />
                {expanded && <span className="text-sm truncate">{label}</span>}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <div className="p-2">
        <Separator className="mb-2" />
        <div className={cn("flex items-center", expanded ? "justify-between" : "justify-center")}>
          <p className="text-center text-[8px] text-muted-foreground font-mono pb-1">Version: 1.0.0</p>
          {!expanded && (
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-muted-foreground"
              onClick={() => setExpanded(true)}
              title="Expand sidebar"
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          )}
        </div>
      </div>
    </aside>
  );
}
