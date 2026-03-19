package com.huatai.rag.api.question.dto;

import java.util.ArrayList;
import java.util.List;

public class TopQuestionsResponse {

    private String status;
    private List<QuestionCount> questions = new ArrayList<>();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<QuestionCount> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionCount> questions) {
        this.questions = questions;
    }

    public static class QuestionCount {
        private String question;
        private int count;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
