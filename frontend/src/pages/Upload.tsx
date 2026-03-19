import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Upload as UploadIcon, FileText, Loader2, FileCheck, AlertCircle } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

interface ProcessedFile {
  filename: string;
  index_name: string;
}

const Upload = () => {
  const [uploading, setUploading] = useState(false);
  const [processedFiles, setProcessedFiles] = useState<ProcessedFile[]>([]);
  const { toast } = useToast();

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
    <div className="flex flex-col items-center space-y-8 animate-fade-in">
      <div className="text-center space-y-4 max-w-2xl">
        <h1 className="text-4xl font-bold tracking-tight text-foreground">
          Upload Documents
        </h1>
        <p className="text-muted-foreground text-lg">
          Upload PDF financial reports for AI-powered analysis and indexing.
        </p>
      </div>

      <div className="w-full max-w-4xl grid gap-8 md:grid-cols-[2fr_1fr]">
        <div className="space-y-6">
          <Card className="p-10 border-dashed border-2 border-primary/30 bg-primary/5 hover:bg-primary/10 hover:border-primary/50 transition-all cursor-pointer relative overflow-hidden group">
            <div className="absolute inset-0 bg-gradient-to-br from-primary/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none" />
            
            <label htmlFor="file-upload" className="flex flex-col items-center justify-center w-full h-full cursor-pointer relative z-10">
              <div className="w-20 h-20 rounded-full bg-primary/10 flex items-center justify-center mb-6 group-hover:scale-110 transition-transform duration-300">
                <UploadIcon className="w-10 h-10 text-primary" />
              </div>
              <p className="mb-2 text-xl font-semibold text-foreground">
                {uploading ? "Processing..." : "Click or drag files here"}
              </p>
              <p className="text-sm text-muted-foreground">Support for PDF files only</p>
              
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
              <div className="absolute inset-0 bg-background/80 backdrop-blur-sm flex items-center justify-center z-20">
                <div className="flex flex-col items-center gap-4">
                  <Loader2 className="w-12 h-12 animate-spin text-primary" />
                  <span className="text-lg font-medium text-foreground">Analyzing Document Structure...</span>
                </div>
              </div>
            )}
          </Card>
          
          <div className="rounded-xl border border-blue-500/20 bg-blue-500/5 p-4 flex gap-3 items-start">
            <AlertCircle className="w-5 h-5 text-blue-500 mt-0.5 shrink-0" />
            <div className="text-sm text-muted-foreground">
              <p className="font-medium text-blue-500 mb-1">Processing Note</p>
              Large files may take a few moments to process. The system performs OCR, text chunking, and vector embedding in real-time.
            </div>
          </div>
        </div>

        <div className="space-y-4">
          <div className="flex items-center justify-between">
             <h3 className="font-semibold text-foreground flex items-center gap-2">
               <FileCheck className="w-4 h-4 text-primary" />
               Processed Files
             </h3>
             <span className="text-xs text-muted-foreground bg-secondary/50 px-2 py-0.5 rounded-full">
               {processedFiles.length}
             </span>
          </div>
          
          <div className="rounded-xl border border-white/10 bg-white/5 h-[500px] overflow-y-auto p-2 space-y-2 custom-scrollbar">
            {processedFiles.length > 0 ? (
              processedFiles.map((file, index) => (
                <div
                  key={index}
                  className="group flex items-center gap-3 p-3 rounded-lg hover:bg-white/5 transition-colors border border-transparent hover:border-white/10 animate-fade-in-up"
                  style={{ animationDelay: `${index * 50}ms` }}
                >
                  <div className="w-8 h-8 rounded bg-primary/10 flex items-center justify-center shrink-0">
                    <FileText className="w-4 h-4 text-primary" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-foreground truncate">{file.filename}</p>
                    <p className="text-xs text-muted-foreground truncate opacity-0 group-hover:opacity-100 transition-opacity">
                      ID: {file.index_name}
                    </p>
                  </div>
                </div>
              ))
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-muted-foreground gap-2">
                <FileText className="w-8 h-8 opacity-20" />
                <p className="text-sm">No files processed yet</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Upload;
