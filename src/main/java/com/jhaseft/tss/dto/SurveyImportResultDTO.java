package com.jhaseft.tss.dto;

import java.util.List;

public class SurveyImportResultDTO {

    private Long surveyId;
    private String publicSlug;
    private String surveyName;
    private String origin;
    private int rowsLoaded;
    private int questionsDetected;
    private List<String> errors;

    public Long getSurveyId() { return surveyId; }
    public void setSurveyId(Long surveyId) { this.surveyId = surveyId; }

    public String getPublicSlug() { return publicSlug; }
    public void setPublicSlug(String publicSlug) { this.publicSlug = publicSlug; }

    public String getSurveyName() { return surveyName; }
    public void setSurveyName(String surveyName) { this.surveyName = surveyName; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public int getRowsLoaded() { return rowsLoaded; }
    public void setRowsLoaded(int rowsLoaded) { this.rowsLoaded = rowsLoaded; }

    public int getQuestionsDetected() { return questionsDetected; }
    public void setQuestionsDetected(int questionsDetected) { this.questionsDetected = questionsDetected; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
