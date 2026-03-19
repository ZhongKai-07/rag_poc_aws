package com.huatai.rag.infrastructure.persistence.repository;

import com.huatai.rag.infrastructure.persistence.entity.QuestionHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionHistoryJpaRepository extends JpaRepository<QuestionHistoryEntity, UUID> {

    @Query("""
            select q.question as question, count(q) as total
            from QuestionHistoryEntity q
            where q.indexName = :indexName
            group by q.question
            order by count(q) desc, q.question asc
            """)
    List<QuestionCountView> findTopQuestionsByIndexName(
            @Param("indexName") String indexName,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            select q.question as question, count(q) as total
            from QuestionHistoryEntity q
            where q.indexName in :indexNames
            group by q.question
            order by count(q) desc, q.question asc
            """)
    List<QuestionCountView> findTopQuestionsByIndexNames(
            @Param("indexNames") List<String> indexNames,
            org.springframework.data.domain.Pageable pageable);

    default List<QuestionCountView> findTop5ByIndexNameGroupByQuestionOrderByCountDesc(String indexName) {
        return findTopQuestionsByIndexName(indexName, PageRequest.of(0, 5));
    }

    interface QuestionCountView {
        String getQuestion();

        long getTotal();
    }
}
