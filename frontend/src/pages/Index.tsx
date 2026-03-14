import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Upload, MessageSquare, FileSearch } from "lucide-react";

const Index = () => {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-6">
      <div className="max-w-5xl w-full space-y-12">
        <div className="text-center space-y-4">
          <h1 className="text-5xl font-bold bg-gradient-primary bg-clip-text text-transparent">
            Research Report QA System
          </h1>
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
            Powered by AI - Upload financial reports and get instant answers to your questions
          </p>
        </div>

        <div className="grid md:grid-cols-2 gap-6">
          <Card
            className="p-8 border-border bg-gradient-card shadow-glow cursor-pointer hover:scale-[1.02] transition-transform"
            onClick={() => navigate("/upload")}
          >
            <div className="space-y-4">
              <div className="w-14 h-14 rounded-lg bg-primary/10 flex items-center justify-center">
                <Upload className="w-7 h-7 text-primary" />
              </div>
              <h2 className="text-2xl font-semibold text-foreground">Upload Reports</h2>
              <p className="text-muted-foreground">
                Upload and process PDF financial reports for AI-powered analysis
              </p>
              <Button className="w-full bg-primary hover:bg-primary/90 text-primary-foreground">
                Start Uploading
              </Button>
            </div>
          </Card>

          <Card
            className="p-8 border-border bg-gradient-card shadow-glow cursor-pointer hover:scale-[1.02] transition-transform"
            onClick={() => navigate("/qa")}
          >
            <div className="space-y-4">
              <div className="w-14 h-14 rounded-lg bg-primary/10 flex items-center justify-center">
                <MessageSquare className="w-7 h-7 text-primary" />
              </div>
              <h2 className="text-2xl font-semibold text-foreground">Ask Questions</h2>
              <p className="text-muted-foreground">
                Query your uploaded reports and get AI-generated insights
              </p>
              <Button className="w-full bg-primary hover:bg-primary/90 text-primary-foreground">
                Start Asking
              </Button>
            </div>
          </Card>
        </div>

        <Card className="p-6 border-primary/20 bg-card/50">
          <div className="flex items-start gap-4">
            <FileSearch className="w-6 h-6 text-primary flex-shrink-0 mt-1" />
            <div className="space-y-1">
              <h3 className="font-semibold text-foreground">How it works</h3>
              <p className="text-sm text-muted-foreground">
                Upload your PDF financial reports, and our AI will process and analyze them. 
                Then ask questions to get instant, accurate answers based on the document content.
              </p>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
};

export default Index;
