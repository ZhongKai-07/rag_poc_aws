package com.huatai.rag.domain.parser;

public interface DocumentParser {

    ParsedDocument parse(ParserRequest request);
}
