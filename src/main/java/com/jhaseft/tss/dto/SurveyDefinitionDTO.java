package com.jhaseft.tss.dto;

import java.util.List;

public class SurveyDefinitionDTO {

    private String name;
    private String origin;
    private List<LikertScaleDef> likertScales;
    private List<QuestionDef> questions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public List<LikertScaleDef> getLikertScales() { return likertScales; }
    public void setLikertScales(List<LikertScaleDef> likertScales) { this.likertScales = likertScales; }

    public List<QuestionDef> getQuestions() { return questions; }
    public void setQuestions(List<QuestionDef> questions) { this.questions = questions; }

    public static class LikertScaleDef {
        private String name;
        private List<OptionDef> options;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<OptionDef> getOptions() { return options; }
        public void setOptions(List<OptionDef> options) { this.options = options; }
    }

    public static class QuestionDef {
        private int position;
        private String columnName;
        private String text;
        private String type;
        private String section;
        private String matrixGroup;
        private String likertScaleName;
        private boolean personalData;
        private List<OptionDef> options;

        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getSection() { return section; }
        public void setSection(String section) { this.section = section; }

        public String getMatrixGroup() { return matrixGroup; }
        public void setMatrixGroup(String matrixGroup) { this.matrixGroup = matrixGroup; }

        public String getLikertScaleName() { return likertScaleName; }
        public void setLikertScaleName(String likertScaleName) { this.likertScaleName = likertScaleName; }

        public boolean isPersonalData() { return personalData; }
        public void setPersonalData(boolean personalData) { this.personalData = personalData; }

        public List<OptionDef> getOptions() { return options; }
        public void setOptions(List<OptionDef> options) { this.options = options; }
    }

    public static class OptionDef {
        private String displayText;
        private Double numericValue;
        private int position;

        public String getDisplayText() { return displayText; }
        public void setDisplayText(String displayText) { this.displayText = displayText; }

        public Double getNumericValue() { return numericValue; }
        public void setNumericValue(Double numericValue) { this.numericValue = numericValue; }

        public int getPosition() { return position; }
        public void setPosition(int position) { this.position = position; }
    }
}
