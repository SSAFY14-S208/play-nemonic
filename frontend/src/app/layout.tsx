import type { Metadata } from "next";
import { Toaster } from "sonner";

import { BrowserExtensionErrorGuard, UserBootstrapLoader } from "@/shared/components";
import "@/shared/styles/index.css";

export const metadata: Metadata = {
  title: "네모닉 월드",
  description: "네모닉 월드",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="min-h-full flex flex-col">
        <BrowserExtensionErrorGuard />
        <UserBootstrapLoader />
        <Toaster position="top-center" />
        {children}
      </body>
    </html>
  );
}
