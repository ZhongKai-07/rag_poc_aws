import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  fetchParseResults,
  fetchRawBdaJson,
  fetchIndexedChunks,
  ParseResultSummary,
  IndexedChunk,
} from "@/api/adminApi";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ChevronDown, ChevronRight } from "lucide-react";

// ── JSON tree viewer ──────────────────────────────────────────────────────────
function JsonTree({ data, depth = 0 }: { data: unknown; depth?: number }) {
  const [open, setOpen] = useState(depth < 1);
  if (data === null || typeof data !== "object") {
    return <span className="text-green-600 dark:text-green-400">{JSON.stringify(data)}</span>;
  }
  const entries = Array.isArray(data)
    ? data.map((v, i) => [i, v] as [unknown, unknown])
    : Object.entries(data as Record<string, unknown>);
  const preview = Array.isArray(data) ? `[${entries.length}]` : `{${entries.length}}`;
  return (
    <span>
      <button
        className="inline-flex items-center gap-0.5 hover:text-primary"
        onClick={() => setOpen(!open)}
      >
        {open ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
        <span className="text-muted-foreground text-xs">{preview}</span>
      </button>
      {open && (
        <div className="ml-4 border-l border-border pl-2">
          {entries.map(([key, val]) => (
            <div key={String(key)} className="my-0.5">
              <span className="text-blue-600 dark:text-blue-400 mr-1">{String(key)}:</span>
              <JsonTree data={val} depth={depth + 1} />
            </div>
          ))}
        </div>
      )}
    </span>
  );
}

// ── Chunk item ─────────────────────────────────────────────────────────────────
function ChunkItem({ chunk }: { chunk: IndexedChunk }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="border rounded-md mb-2">
      <button
        className="w-full flex items-center gap-2 px-3 py-2 text-sm text-left hover:bg-muted/50"
        onClick={() => setOpen(!open)}
      >
        {open ? <ChevronDown className="h-3 w-3 shrink-0" /> : <ChevronRight className="h-3 w-3 shrink-0" />}
        <span className="font-mono text-xs text-muted-foreground w-20 shrink-0">{chunk.chunk_id}</span>
        <span className="text-xs text-muted-foreground shrink-0">p.{chunk.page_number}</span>
        {chunk.section_path.length > 0 && (
          <Badge variant="outline" className="text-xs shrink-0">{chunk.section_path.join(" > ")}</Badge>
        )}
        <span className="text-xs truncate text-muted-foreground">{chunk.sentence}</span>
      </button>
      {open && (
        <div className="px-4 pb-3 space-y-2 text-sm">
          <div>
            <div className="text-xs font-medium text-muted-foreground mb-1">段落</div>
            <p className="text-sm leading-relaxed">{chunk.paragraph}</p>
          </div>
          <div>
            <div className="text-xs font-medium text-muted-foreground mb-1">摘要句</div>
            <p className="text-sm text-muted-foreground">{chunk.sentence}</p>
          </div>
          {chunk.asset_references.length > 0 && (
            <div>
              <div className="text-xs font-medium text-muted-foreground mb-1">Assets</div>
              {chunk.asset_references.map((ref) => (
                <div key={ref} className="text-xs font-mono text-blue-500 truncate">{ref}</div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── Detail panel ───────────────────────────────────────────────────────────────
function DetailPanel({ selected }: { selected: ParseResultSummary }) {
  const rawQuery = useQuery({
    queryKey: ["bdaRaw", selected.index_name],
    queryFn: () => fetchRawBdaJson(selected.index_name),
    enabled: false,  // lazy — activated when tab is clicked
  });
  const chunksQuery = useQuery({
    queryKey: ["bdaChunks", selected.index_name],
    queryFn: () => fetchIndexedChunks(selected.index_name),
    enabled: false,
  });

  return (
    <Tabs defaultValue="summary" className="h-full flex flex-col">
      <TabsList className="shrink-0">
        <TabsTrigger value="summary">摘要</TabsTrigger>
        <TabsTrigger value="raw" onClick={() => rawQuery.refetch()}>原始 JSON</TabsTrigger>
        <TabsTrigger value="chunks" onClick={() => chunksQuery.refetch()}>Chunks</TabsTrigger>
      </TabsList>

      <TabsContent value="summary" className="flex-1 overflow-auto">
        <div className="grid grid-cols-2 gap-3 p-1">
          {[
            ["文件名", selected.filename],
            ["Index", selected.index_name],
            ["Chunks", selected.chunk_count],
            ["页数", selected.page_count],
            ["解析器", `${selected.parser_type}:${selected.parser_version}`],
            ["解析时间", new Date(selected.created_at).toLocaleString()],
          ].map(([label, value]) => (
            <div key={String(label)} className="bg-muted/40 rounded p-2">
              <div className="text-xs text-muted-foreground">{label}</div>
              <div className="text-sm font-medium truncate">{String(value)}</div>
            </div>
          ))}
        </div>
      </TabsContent>

      <TabsContent value="raw" className="flex-1 overflow-auto">
        {rawQuery.isFetching && <p className="text-sm text-muted-foreground p-2">加载中…</p>}
        {rawQuery.isError && (
          <p className="text-sm text-destructive p-2">{(rawQuery.error as Error).message}</p>
        )}
        {rawQuery.data && (
          <pre className="text-xs p-3 font-mono overflow-auto">
            <JsonTree data={rawQuery.data} />
          </pre>
        )}
      </TabsContent>

      <TabsContent value="chunks" className="flex-1 overflow-auto">
        {chunksQuery.isFetching && <p className="text-sm text-muted-foreground p-2">加载中…</p>}
        {chunksQuery.isError && (
          <p className="text-sm text-destructive p-2">{(chunksQuery.error as Error).message}</p>
        )}
        {chunksQuery.data && (
          <div className="p-1">
            <p className="text-xs text-muted-foreground mb-2">{chunksQuery.data.length} 个 chunks</p>
            {chunksQuery.data.map((chunk) => (
              <ChunkItem key={chunk.chunk_id} chunk={chunk} />
            ))}
          </div>
        )}
      </TabsContent>
    </Tabs>
  );
}

// ── Main page ──────────────────────────────────────────────────────────────────
const AdminPage: React.FC = () => {
  const [selected, setSelected] = useState<ParseResultSummary | null>(null);
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["parseResults"],
    queryFn: fetchParseResults,
  });

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-bold">BDA 解析观测</h1>
      <div className="grid grid-cols-[280px_1fr] gap-4 h-[calc(100vh-180px)]">
        {/* Left panel */}
        <Card className="overflow-auto">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">文档列表</CardTitle>
          </CardHeader>
          <CardContent className="p-2">
            {isLoading && <p className="text-sm text-muted-foreground px-2">加载中…</p>}
            {isError && (
              <p className="text-sm text-destructive px-2">{(error as Error).message}</p>
            )}
            {data?.map((item) => (
              <button
                key={`${item.index_name}-${item.created_at}`}
                className={`w-full text-left rounded px-3 py-2 mb-1 hover:bg-muted/50 transition-colors ${
                  selected?.index_name === item.index_name &&
                  selected?.created_at === item.created_at
                    ? "bg-muted"
                    : ""
                }`}
                onClick={() => setSelected(item)}
              >
                <div className="text-sm font-medium truncate">{item.filename}</div>
                <div className="text-xs text-muted-foreground">
                  {item.chunk_count} 块 · {item.page_count} 页
                </div>
                <div className="text-xs text-muted-foreground">
                  {new Date(item.created_at).toLocaleDateString()}
                </div>
              </button>
            ))}
          </CardContent>
        </Card>

        {/* Right panel */}
        <Card className="overflow-hidden">
          <CardContent className="p-3 h-full">
            {selected ? (
              <DetailPanel key={`${selected.index_name}-${selected.created_at}`} selected={selected} />
            ) : (
              <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
                选择左侧文档查看解析详情
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default AdminPage;
