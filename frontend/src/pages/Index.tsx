import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Upload, MessageSquare, FileSearch, ArrowRight, Sparkles } from "lucide-react";

const Index = () => {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col items-center justify-center space-y-16 py-12">
      {/* Hero Section */}
      <div className="text-center space-y-6 max-w-3xl animate-fade-in-down">
        <div className="inline-flex items-center rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-sm font-medium text-primary backdrop-blur-sm">
          <Sparkles className="mr-2 h-4 w-4" />
          <span>Next Gen AI Knowledge Base</span>
        </div>
        
        <h1 className="text-6xl font-extrabold tracking-tight sm:text-7xl">
          <span className="block text-foreground">Intelligent</span>
          <span className="text-gradient block mt-2">Financial Analysis</span>
        </h1>
        
        <p className="text-xl text-muted-foreground max-w-2xl mx-auto leading-relaxed">
          Upload financial reports and let our cosmic AI engine extract insights, answer queries, and visualize data in seconds.
        </p>
        
        <div className="flex flex-wrap items-center justify-center gap-4 pt-4">
          <Button 
            size="lg" 
            className="rounded-full px-8 bg-primary hover:bg-primary/90 text-white shadow-glow hover:shadow-lg transition-all hover:-translate-y-1"
            onClick={() => navigate("/upload")}
          >
            Get Started <ArrowRight className="ml-2 h-4 w-4" />
          </Button>
          <Button 
            size="lg" 
            variant="outline" 
            className="rounded-full px-8 border-primary/20 bg-white/5 hover:bg-white/10 backdrop-blur-sm"
            onClick={() => navigate("/qa")}
          >
            Try Demo
          </Button>
        </div>
      </div>

      {/* Feature Cards */}
      <div className="grid md:grid-cols-2 gap-8 w-full max-w-5xl stagger-children">
        <div 
          className="group relative overflow-hidden rounded-2xl border border-white/10 bg-white/5 p-8 transition-all hover:border-primary/50 hover:shadow-glow cursor-pointer"
          onClick={() => navigate("/upload")}
        >
          <div className="absolute inset-0 bg-gradient-to-br from-primary/10 to-transparent opacity-0 transition-opacity group-hover:opacity-100" />
          
          <div className="relative space-y-4">
            <div className="w-14 h-14 rounded-xl bg-gradient-to-br from-primary to-purple-600 flex items-center justify-center shadow-lg group-hover:scale-110 transition-transform duration-300">
              <Upload className="w-7 h-7 text-white" />
            </div>
            
            <h2 className="text-2xl font-bold text-foreground">Upload Reports</h2>
            <p className="text-muted-foreground leading-relaxed">
              Drag & drop PDF financial reports. Our system automatically processes, chunks, and vectorizes your documents for instant retrieval.
            </p>
            
            <div className="flex items-center text-primary font-medium pt-2">
              Start Uploading <ArrowRight className="ml-2 h-4 w-4 transition-transform group-hover:translate-x-1" />
            </div>
          </div>
        </div>

        <div 
          className="group relative overflow-hidden rounded-2xl border border-white/10 bg-white/5 p-8 transition-all hover:border-secondary/50 hover:shadow-glow cursor-pointer"
          onClick={() => navigate("/qa")}
        >
          <div className="absolute inset-0 bg-gradient-to-br from-secondary/10 to-transparent opacity-0 transition-opacity group-hover:opacity-100" />
          
          <div className="relative space-y-4">
            <div className="w-14 h-14 rounded-xl bg-gradient-to-br from-secondary to-teal-600 flex items-center justify-center shadow-lg group-hover:scale-110 transition-transform duration-300">
              <MessageSquare className="w-7 h-7 text-white" />
            </div>
            
            <h2 className="text-2xl font-bold text-foreground">Ask Questions</h2>
            <p className="text-muted-foreground leading-relaxed">
              Interact with your data using natural language. Get citations, source highlights, and deep insights from multiple documents.
            </p>
            
            <div className="flex items-center text-secondary font-medium pt-2">
              Start Asking <ArrowRight className="ml-2 h-4 w-4 transition-transform group-hover:translate-x-1" />
            </div>
          </div>
        </div>
      </div>

      {/* Info Section */}
      <div className="w-full max-w-5xl animate-fade-in-up" style={{ animationDelay: '0.6s' }}>
        <div className="rounded-2xl border border-white/10 bg-white/5 p-8 backdrop-blur-md">
          <div className="flex flex-col md:flex-row items-center gap-8">
            <div className="flex-shrink-0 w-16 h-16 rounded-full bg-primary/10 flex items-center justify-center animate-pulse-glow">
              <FileSearch className="w-8 h-8 text-primary" />
            </div>
            <div className="space-y-2 text-center md:text-left">
              <h3 className="text-xl font-semibold text-foreground">How it works</h3>
              <p className="text-muted-foreground leading-relaxed">
                Our RAG (Retrieval-Augmented Generation) engine combines the power of Large Language Models with your proprietary data. 
                Upload your PDF reports, and we create a semantic index that allows the AI to find the exact information you need, 
                reducing hallucinations and providing evidence-based answers.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Index;
