import type { Metadata } from "next";
import Script from "next/script";
import { Toaster } from "sonner";

import { BrowserExtensionErrorGuard, LogBootstrapLoader, UserBootstrapLoader } from "@/shared/components";
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
        <Script
          id="browser-extension-error-guard"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{
            __html: `
              (function () {
                function getErrorText(value) {
                  if (!value) return '';
                  if (value instanceof Error) return value.message + '\\n' + (value.stack || '');
                  return String(value);
                }
                function isMetaMaskExtensionError(text) {
                  return text.indexOf('Failed to connect to MetaMask') !== -1 &&
                    text.indexOf('chrome-extension://') !== -1;
                }
                window.addEventListener('error', function (event) {
                  var text = event.message + '\\n' + event.filename + '\\n' + getErrorText(event.error);
                  if (!isMetaMaskExtensionError(text)) return;
                  event.preventDefault();
                  event.stopImmediatePropagation();
                }, true);
                window.addEventListener('unhandledrejection', function (event) {
                  var text = getErrorText(event.reason);
                  if (!isMetaMaskExtensionError(text)) return;
                  event.preventDefault();
                  event.stopImmediatePropagation();
                }, true);
              })();
            `,
          }}
        />
        <BrowserExtensionErrorGuard />
        <UserBootstrapLoader />
        <LogBootstrapLoader />
        <Toaster position="top-center" />
        {children}
      </body>
    </html>
  );
}
