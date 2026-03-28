package com.huatai.rag.api.chat.dto;

public class FeedbackDto {
    private String rating;
    private String comment;

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
