package com.huatai.rag.evaluation.model;

import java.util.List;

public record TestDataset(String version, String dataset, List<TestCase> cases) {}
