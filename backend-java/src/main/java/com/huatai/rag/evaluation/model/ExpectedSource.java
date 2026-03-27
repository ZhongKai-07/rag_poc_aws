package com.huatai.rag.evaluation.model;

import java.util.List;

public record ExpectedSource(String filename, List<Integer> pageNumbers) {}
