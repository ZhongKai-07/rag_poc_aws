package com.huatai.rag.domain.bda;

import java.util.List;
import java.util.Optional;

public interface BdaParseResultPort {

    BdaParseResultRecord save(BdaParseResultRecord record);

    List<BdaParseResultRecord> findAll();

    Optional<BdaParseResultRecord> findLatestByIndexName(String indexName);
}
