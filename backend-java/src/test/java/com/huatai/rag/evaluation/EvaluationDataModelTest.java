package com.huatai.rag.evaluation;

import com.huatai.rag.evaluation.application.TestDatasetLoader;
import com.huatai.rag.evaluation.model.TestDataset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EvaluationDataModelTest {

    @Test
    void loads_cob_testset_from_classpath() {
        TestDataset dataset = TestDatasetLoader.load("evaluation/cob_testset.json");
        assertThat(dataset.dataset()).isEqualTo("cob_baseline");
        assertThat(dataset.cases()).hasSize(2);
        assertThat(dataset.cases().get(0).id()).isEqualTo("COB-001");
        assertThat(dataset.cases().get(0).expectedKeywords()).containsExactly("AML", "KYC", "审查");
    }

    @Test
    void loads_collateral_testset_from_classpath() {
        TestDataset dataset = TestDatasetLoader.load("evaluation/collateral_testset.json");
        assertThat(dataset.dataset()).isEqualTo("collateral_baseline");
        assertThat(dataset.cases()).hasSize(2);
        assertThat(dataset.cases().get(0).expectedStructured()).isNotNull();
        assertThat(dataset.cases().get(0).expectedStructured().counterparty()).isEqualTo("HSBC");
    }
}
