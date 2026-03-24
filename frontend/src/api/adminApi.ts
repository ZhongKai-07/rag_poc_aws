const BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8001";

export interface ParseResultSummary {
  index_name: string;
  filename: string;
  chunk_count: number;
  page_count: number;
  parser_type: string;
  parser_version: string;
  created_at: string;
}

export interface IndexedChunk {
  chunk_id: string;
  page_number: number;
  section_path: string[];
  paragraph: string;
  sentence: string;
  asset_references: string[];
}

export async function fetchParseResults(): Promise<ParseResultSummary[]> {
  const res = await fetch(`${BASE}/admin/parse_results`);
  if (!res.ok) throw new Error(`Failed to fetch parse results: ${res.status}`);
  return res.json();
}

export async function fetchRawBdaJson(indexName: string): Promise<unknown> {
  const res = await fetch(`${BASE}/admin/parse_results/${indexName}/raw`);
  if (res.status === 404) throw new Error("Parse result not found");
  if (!res.ok) throw new Error(`Failed to fetch raw BDA JSON: ${res.status}`);
  return res.json();
}

export async function fetchIndexedChunks(indexName: string): Promise<IndexedChunk[]> {
  const res = await fetch(`${BASE}/admin/parse_results/${indexName}/chunks`);
  if (res.status === 404) throw new Error("Index not found");
  if (!res.ok) throw new Error(`Failed to fetch chunks: ${res.status}`);
  return res.json();
}
