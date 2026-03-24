import React from "react";
import { Link, useLocation } from "react-router-dom";
import { Home, Upload, MessageSquare, Moon, Sun, Settings } from "lucide-react";
import { Button } from "@/components/ui/button";

interface LayoutProps {
  children: React.ReactNode;
}

const Layout = ({ children }: LayoutProps) => {
  const location = useLocation();
  const [isDark, setIsDark] = React.useState(false);

  // Toggle dark mode
  React.useEffect(() => {
    if (isDark) {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
  }, [isDark]);

  const navItems = [
    { path: "/", label: "Home", icon: Home },
    { path: "/upload", label: "Upload", icon: Upload },
    { path: "/qa", label: "Q&A", icon: MessageSquare },
  ];

  const allNavItems = import.meta.env.DEV
    ? [...navItems, { path: "/admin", label: "Admin", icon: Settings }]
    : navItems;

  return (
    <div className="min-h-screen flex flex-col bg-background font-sans selection:bg-primary/20">
      {/* Navbar */}
      <header className="sticky top-0 z-50 w-full border-b border-white/10 bg-background/80 backdrop-blur-xl supports-[backdrop-filter]:bg-background/60">
        <div className="container flex h-16 items-center justify-between">
          <Link to="/" className="flex items-center gap-2 group">
            <div className="relative flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-purple-600 text-white shadow-lg transition-transform group-hover:scale-110">
              <span className="font-bold">H</span>
            </div>
            <span className="text-lg font-bold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-foreground to-foreground/70">
              Huatai<span className="text-primary">RAG</span>
            </span>
          </Link>

          <nav className="flex items-center gap-6">
            {allNavItems.map((item) => {
              const Icon = item.icon;
              const isActive = location.pathname === item.path;
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`relative flex items-center gap-2 text-sm font-medium transition-colors hover:text-primary ${
                    isActive ? "text-primary" : "text-muted-foreground"
                  }`}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                  {isActive && (
                    <span className="absolute -bottom-[21px] left-0 h-[2px] w-full bg-primary transition-transform" />
                  )}
                </Link>
              );
            })}
            
            <Button
              variant="ghost"
              size="icon"
              className="ml-4 rounded-full hover:bg-muted"
              onClick={() => setIsDark(!isDark)}
            >
              {isDark ? (
                <Sun className="h-5 w-5 text-yellow-400" />
              ) : (
                <Moon className="h-5 w-5 text-slate-700" />
              )}
            </Button>
          </nav>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 container py-8 animate-fade-in">
        {children}
      </main>

      {/* Footer */}
      <footer className="border-t border-white/10 bg-background/50 py-8 backdrop-blur-sm">
        <div className="container flex flex-col items-center justify-between gap-4 md:flex-row">
          <p className="text-sm text-muted-foreground">
            © 2026 Huatai RAG System. Powered by Cosmic Intelligence.
          </p>
          <div className="flex gap-4">
            <Link to="#" className="text-sm text-muted-foreground hover:text-primary transition-colors">
              Privacy Policy
            </Link>
            <Link to="#" className="text-sm text-muted-foreground hover:text-primary transition-colors">
              Terms of Service
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default Layout;
