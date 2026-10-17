package com.jhaseft.tss.dto;

import java.util.List;

public class SurveyStatsDTO {
    private Long surveyId;
    private String surveyName;
    private String origin;
    private Long totalResponses;
    private List<QuestionStatsDTO> questions;

    public Long getSurveyId() { return surveyId; }
    public void setSurveyId(Long surveyId) { this.surveyId = surveyId; }

    public String getSurveyName() { return surveyName; }
    public void setSurveyName(String surveyName) { this.surveyName = surveyName; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public Long getTotalResponses() { return totalResponses; }
    public void setTotalResponses(Long totalResponses) { this.totalResponses = totalResponses; }

    public List<QuestionStatsDTO> getQuestions() { return questions; }
    public void setQuestions(List<QuestionStatsDTO> questions) { this.questions = questions; }
}
