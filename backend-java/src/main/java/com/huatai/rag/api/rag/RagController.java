package com.huatai.rag.api.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.rag.api.rag.dto.CitationDto;
import com.huatai.rag.api.rag.dto.RagRequest;
import com.huatai.rag.api.rag.dto.RagResponse;
import com.huatai.rag.api.rag.dto.RecallDocumentDto;
import com.huatai.rag.api.rag.dto.SourceDocumentDto;
import com.huatai.rag.application.rag.RagQueryApplicationService;
import com.huatai.rag.domain.rag.AnswerGenerationPort;
import com.huatai.rag.domain.rag.Citation;
import com.huatai.rag.domain.retrieval.RetrievedDocument;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagQueryApplicationService ragQueryApplicationService;
    private final TaskExecutor streamingExecutor;
    private final AnswerGenerationPort answerGenerationPort;

    public RagController(RagQueryApplicationService ragQueryApplicationService,
                         @Qualifier("streamingExecutor") TaskExecutor streamingExecutor,
                         AnswerGenerationPort answerGenerationPort) {
        this.ragQueryApplicationService = ragQueryApplicationService;
        this.streamingExecutor = streamingExecutor;
        this.answerGenerationPort = answerGenerationPort;
    }

    @PostMapping("/rag_answer")
    public RagResponse ragAnswer(@Valid @RequestBody RagRequest request) {
        log.info("[API] POST /rag_answer session={} module={} indices={} query='{}'",
                request.getSessionId(), request.getModule(), request.getIndexNames(), request.getQuery());
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

        RagResponse response = buildResponse(result);
        log.info("[API] response: answer_len={}, sources={}, citations={}",
                result.answer().length(), result.sourceDocuments().size(), result.citations().size());
        return response;
    }

    @PostMapping(value = "/rag_answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ragAnswerStream(@RequestBody RagRequest request) {
        log.info("[API] POST /rag_answer/stream session={} query='{}'",
                request.getSessionId(), request.getQuery());
        SseEmitter emitter = new SseEmitter(60_000L);
        streamingExecutor.execute(() -> {
            try {
                RagQueryApplicationService.QueryResult result = ragQueryApplicationService.handle(
                        new RagQueryApplicationService.QueryCommand(
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

                String[] tokens = result.answer().split("(?<=\\G.{1,10})");
                for (String token : tokens) {
                    emitter.send(SseEmitter.event().name("token")
                            .data(Map.of("content", token)));
                }

                emitter.send(SseEmitter.event().name("done").data(buildResponse(result)));
                emitter.complete();
            } catch (Exception e) {
                log.error("[API] SSE stream error", e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", e.getMessage() != null ? e.getMessage() : "Unknown error")));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private RagResponse buildResponse(RagQueryApplicationService.QueryResult result) {
        RagResponse response = new RagResponse();
        response.setAnswer(result.answer());
        response.setSourceDocuments(result.sourceDocuments().stream().map(this::toSourceDocument).toList());
        response.setRecallDocuments(result.recallDocuments().stream().map(this::toRecallDocument).toList());
        response.setRerankDocuments(result.rerankDocuments().stream().map(this::toSourceDocument).toList());
        response.setCitations(result.citations().stream().map(this::toCitationDto).toList());
        response.setSuggestedQuestions(result.suggestedQuestions());
        response.setConfidence(result.confidence() != null ? result.confidence().name() : null);
        response.setHistoryCompressed(result.historyCompressed());
        response.setSessionId(result.sessionId() != null ? result.sessionId().toString() : null);
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

    private CitationDto toCitationDto(Citation citation) {
        return new CitationDto(
                citation.index(),
                citation.filename(),
                citation.pageNumber(),
                citation.sectionPath(),
                citation.excerpt());
    }
}
