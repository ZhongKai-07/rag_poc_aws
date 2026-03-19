package com.huatai.rag.api.rag;

import com.huatai.rag.api.rag.dto.RagRequest;
import com.huatai.rag.api.rag.dto.RagResponse;
import com.huatai.rag.application.rag.RagQueryApplicationService;
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
        return ragQueryApplicationService.answer(request);
    }
}
