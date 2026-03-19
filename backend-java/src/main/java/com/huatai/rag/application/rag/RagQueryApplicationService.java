package com.huatai.rag.application.rag;

import com.huatai.rag.api.rag.dto.RagRequest;
import com.huatai.rag.api.rag.dto.RagResponse;

public interface RagQueryApplicationService {

    RagResponse answer(RagRequest request);
}
