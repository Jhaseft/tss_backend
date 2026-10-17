package com.jhaseft.tss.dto;

import java.util.List;

public class SurveyResponseDTO {
    private List<AnswerInputDTO> answers;

    public List<AnswerInputDTO> getAnswers() { return answers; }
    public void setAnswers(List<AnswerInputDTO> answers) { this.answers = answers; }
}
