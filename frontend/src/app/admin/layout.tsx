import {
  AdminAuthGuard,
  AdminPageHeader,
  AdminSidebar,
  AdminTopBar,
} from "@/features/admin";

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AdminAuthGuard>
      <div className="flex h-screen min-h-0 flex-col bg-surface-default">
        <AdminTopBar />
        <div className="flex min-h-0 flex-1">
          <AdminSidebar />
          <main className="flex min-h-0 flex-1 flex-col overflow-hidden">
            <AdminPageHeader />
            <div className="flex-1 overflow-y-auto px-8 py-6">{children}</div>
          </main>
        </div>
      </div>
    </AdminAuthGuard>
  );
}
