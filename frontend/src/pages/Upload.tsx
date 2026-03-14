import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Upload as UploadIcon, FileText, Loader2 } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { useNavigate } from "react-router-dom";

interface ProcessedFile {
  filename: string;
  index_name: string;
}

const Upload = () => {
  const [uploading, setUploading] = useState(false);
  const [processedFiles, setProcessedFiles] = useState<ProcessedFile[]>([]);
  const { toast } = useToast();
  const navigate = useNavigate();

  useEffect(() => {
    fetchProcessedFiles();
  }, []);

  const fetchProcessedFiles = async () => {
    try {
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/processed_files`);
      const data = await response.json();
      if (data.status === "success") {
        setProcessedFiles(data.files);
      }
    } catch (error) {
      console.error("Error fetching processed files:", error);
    }
  };

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (!files || files.length === 0) return;

    // Check if all files are PDFs
    const invalidFiles = Array.from(files).filter(file => file.type !== "application/pdf");
    if (invalidFiles.length > 0) {
      toast({
        title: "Invalid File Type",
        description: "Please upload only PDF files.",
        variant: "destructive",
      });
      return;
    }

    setUploading(true);

    try {
      // Create date-based directory path with hours and minutes
      const now = new Date();
      const dateTime = now.toISOString().slice(0, 16).replace('T', '-').replace(':', '-'); // YYYY-MM-DD-HH-MM format
      const directoryPath = `./documents/${dateTime}`;
      
      console.log('Uploading files:', Array.from(files).map(f => f.name));
      console.log('Directory path:', directoryPath);
      console.log('API URL:', `${import.meta.env.VITE_API_BASE_URL}/upload_files`);
      
      // Upload files one by one
      for (const file of Array.from(files)) {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('directory_path', directoryPath);
        
        const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/upload_files`, {
          method: "POST",
          body: formData,
        });
        
        if (!response.ok) {
          const errorText = await response.text();
          console.error('Upload error response:', errorText);
          throw new Error(`Upload failed for ${file.name}: ${response.status}`);
        }
      }
      
      console.log('All files uploaded successfully');
      toast({
        title: "Upload Successful",
        description: `${files.length} file(s) have been processed successfully.`,
      });
      fetchProcessedFiles();
    } catch (error) {
      console.error('Upload error:', error);
      toast({
        title: "Upload Failed",
        description: "There was an error processing your files.",
        variant: "destructive",
      });
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card/50 backdrop-blur-sm">
        <div className="container mx-auto px-6 py-4 flex items-center justify-between">
          <h1 className="text-2xl font-bold bg-gradient-primary bg-clip-text text-transparent">
            Research Report QA System
          </h1>
          <Button
            onClick={() => navigate("/qa")}
            variant="outline"
            className="border-primary/30 hover:bg-primary/10"
          >
            Go to Q&A
          </Button>
        </div>
      </header>

      <main className="container mx-auto px-6 py-12">
        <div className="max-w-4xl mx-auto space-y-8">
          <div className="text-center space-y-2">
            <h2 className="text-3xl font-bold text-foreground">Upload Financial Reports</h2>
            <p className="text-muted-foreground">Upload PDF financial reports for AI-powered analysis</p>
          </div>

          <Card className="p-8 border-border bg-gradient-card shadow-glow">
            <div className="space-y-6">
              <label
                htmlFor="file-upload"
                className="flex flex-col items-center justify-center w-full h-64 border-2 border-dashed border-primary/30 rounded-lg cursor-pointer hover:border-primary/50 transition-all hover:bg-primary/5"
              >
                <div className="flex flex-col items-center justify-center pt-5 pb-6">
                  <UploadIcon className="w-12 h-12 mb-4 text-primary" />
                  <p className="mb-2 text-lg font-semibold text-foreground">
                    {uploading ? "Processing..." : "Click to upload"}
                  </p>
                  <p className="text-sm text-muted-foreground">PDF files only</p>
                </div>
                <input
                  id="file-upload"
                  type="file"
                  className="hidden"
                  accept=".pdf"
                  multiple
                  onChange={handleFileUpload}
                  disabled={uploading}
                />
              </label>

              {uploading && (
                <div className="flex items-center justify-center gap-3 py-4">
                  <Loader2 className="w-5 h-5 animate-spin text-primary" />
                  <span className="text-sm text-muted-foreground">Processing your document...</span>
                </div>
              )}
            </div>
          </Card>

          {processedFiles.length > 0 && (
            <Card className="p-6 border-border bg-card">
              <h3 className="text-xl font-semibold mb-4 text-foreground">Processed Files</h3>
              <div className="space-y-2">
                {processedFiles.map((file, index) => (
                  <div
                    key={index}
                    className="flex items-center gap-3 p-3 rounded-lg bg-secondary/50 hover:bg-secondary/70 transition-colors"
                  >
                    <FileText className="w-5 h-5 text-primary" />
                    <span className="text-sm text-foreground">{file.filename}</span>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>
      </main>
    </div>
  );
};

export default Upload;
