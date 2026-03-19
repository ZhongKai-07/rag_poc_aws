package com.huatai.rag.api.rag;

import com.huatai.rag.api.rag.dto.RagRequest;
import com.huatai.rag.api.rag.dto.RagResponse;
import com.huatai.rag.api.rag.dto.RecallDocumentDto;
import com.huatai.rag.api.rag.dto.SourceDocumentDto;
import com.huatai.rag.api.rag.dto.RagResponse;
import com.huatai.rag.application.rag.RagQueryApplicationService;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagController {

    private final RagQueryApplicationService ragQueryApplicationService;

    public RagController(RagQueryApplicationService ragQueryApplicationService) {
        this.ragQueryApplicationService = ragQueryApplicationService;
    }

    @PostMapping("/rag_answer")
    public RagResponse ragAnswer(@Valid @RequestBody RagRequest request) {
        RagQueryApplicationService.QueryResult result = ragQueryApplicationService.handle(new RagQueryApplicationService.QueryCommand(
                request.getSessionId(),
                request.getIndexNames(),
                request.getQuery(),
                request.getModule(),
                request.getVecDocsNum(),
                request.getTxtDocsNum(),
                request.getVecScoreThreshold(),
                request.getTextScoreThreshold(),
                request.getRerankScoreThreshold(),
                request.getSearchMethod()));

        RagResponse response = new RagResponse();
        response.setAnswer(result.answer());
        response.setSourceDocuments(result.sourceDocuments().stream().map(this::toSourceDocument).toList());
        response.setRecallDocuments(result.recallDocuments().stream().map(this::toRecallDocument).toList());
        response.setRerankDocuments(result.rerankDocuments().stream().map(this::toSourceDocument).toList());
        return response;
    }

    private SourceDocumentDto toSourceDocument(RetrievedDocument document) {
        SourceDocumentDto dto = new SourceDocumentDto();
        dto.setPageContent(document.pageContent());
        dto.setScore(document.score());
        dto.setRerankScore(document.rerankScore());
        return dto;
    }

    private RecallDocumentDto toRecallDocument(RetrievedDocument document) {
        RecallDocumentDto dto = new RecallDocumentDto();
        dto.setPageContent(document.pageContent());
        dto.setScore(document.score());
        return dto;
    }
}
