package com.huatai.rag.api.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CitationDto {
    private int index;
    private String filename;
    @JsonProperty("page_number")
    private Integer pageNumber;
    @JsonProperty("section_path")
    private String sectionPath;
    private String excerpt;

    public CitationDto() {}

    public CitationDto(int index, String filename, Integer pageNumber, String sectionPath, String excerpt) {
        this.index = index;
        this.filename = filename;
        this.pageNumber = pageNumber;
        this.sectionPath = sectionPath;
        this.excerpt = excerpt;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public String getSectionPath() { return sectionPath; }
    public void setSectionPath(String sectionPath) { this.sectionPath = sectionPath; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
}
