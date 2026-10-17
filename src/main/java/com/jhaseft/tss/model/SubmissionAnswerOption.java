package com.jhaseft.tss.model;

import jakarta.persistence.*;

@Entity
@Table(name = "submission_answer_options")
public class SubmissionAnswerOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false)
    private SubmissionAnswer answer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private QuestionOption option;

    @Column(name = "free_text", length = 1000)
    private String freeText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SubmissionAnswer getAnswer() { return answer; }
    public void setAnswer(SubmissionAnswer answer) { this.answer = answer; }

    public QuestionOption getOption() { return option; }
    public void setOption(QuestionOption option) { this.option = option; }

    public String getFreeText() { return freeText; }
    public void setFreeText(String freeText) { this.freeText = freeText; }
}
