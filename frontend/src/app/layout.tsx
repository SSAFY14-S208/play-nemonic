import type { Metadata } from "next";
import { Toaster } from "sonner";

import { BrowserExtensionErrorGuard, LogBootstrapLoader, UserBootstrapLoader } from "@/shared/components";
import "@/shared/styles/index.css";

export const metadata: Metadata = {
  title: "Play! Nemonic",
  description: "Play! Nemonic",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className="h-full antialiased" suppressHydrationWarning>
      <body className="min-h-full flex flex-col" suppressHydrationWarning>
        <BrowserExtensionErrorGuard />
        <UserBootstrapLoader />
        <LogBootstrapLoader />
        <Toaster position="top-center" />
        {children}
      </body>
    </html>
  );
}
