import { NavLink } from "react-router-dom";
import { ClipboardList, LayoutDashboard, Settings2, Server } from "lucide-react";
import { cn } from "@/lib/utils";
import { Separator } from "@/components/ui/separator";

const navItems = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/properties", label: "Properties", icon: Settings2, end: false },
  { to: "/audit", label: "Audit", icon: ClipboardList, end: false },
];

export function Sidebar() {
  return (
    <aside className="flex h-full w-14 flex-col bg-sidebar border-r border-sidebar-border">
      <div className="flex h-14 items-center justify-center border-b border-sidebar-border">
        <div className="flex h-7 w-7 items-center justify-center rounded-md bg-primary">
          <Server className="h-4 w-4 text-primary-foreground" />
        </div>
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
                    "flex items-center justify-center rounded-md p-2 transition-colors",
                    isActive
                      ? "bg-sidebar-accent text-sidebar-primary"
                      : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
                  )
                }
              >
                <Icon className="h-4 w-4" />
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <div className="p-2">
        <Separator className="mb-2" />
        <p className="text-center text-[8px] text-muted-foreground font-mono pb-1">v0.1</p>
      </div>
    </aside>
  );
}
