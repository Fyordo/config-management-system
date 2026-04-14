import { BrowserRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import { ClusterProvider } from "@/context/ClusterContext";
import { ThemeProvider } from "@/context/ThemeContext";
import { AppLayout } from "@/components/layout/AppLayout";
import { DashboardPage } from "@/pages/DashboardPage";
import { PropertiesPage } from "@/pages/PropertiesPage";
import { AuditPage } from "@/pages/AuditPage";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 5_000,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <ClusterProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<AppLayout />}>
                <Route index element={<DashboardPage />} />
                <Route path="properties" element={<PropertiesPage />} />
                <Route path="audit" element={<AuditPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
          <Toaster position="bottom-right" theme="dark" richColors />
        </ClusterProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
