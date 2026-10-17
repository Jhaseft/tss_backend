-- surveys: una por archivo cargado
CREATE TABLE surveys (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    origin      VARCHAR(20)  NOT NULL,
    file_name   VARCHAR(255),
    source_hash VARCHAR(64),
    public_slug VARCHAR(36)  NOT NULL UNIQUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- likert_scales: escalas configurables (acuerdo, satisfacción, importancia, etc.)
CREATE TABLE likert_scales (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    options_json TEXT         NOT NULL
);

-- questions: una por columna del archivo (excluidas las de metadatos)
CREATE TABLE questions (
    id                   BIGSERIAL     PRIMARY KEY,
    survey_id            BIGINT        NOT NULL REFERENCES surveys(id) ON DELETE CASCADE,
    column_name          VARCHAR(500)  NOT NULL,
    text                 VARCHAR(1000) NOT NULL,
    section              VARCHAR(255),
    position             INT           NOT NULL,
    type                 VARCHAR(30)   NOT NULL,
    is_personal_data     BOOLEAN       NOT NULL DEFAULT FALSE,
    inference_confidence NUMERIC(3,2),
    parent_question_id   BIGINT        REFERENCES questions(id),
    conditional_value    VARCHAR(255),
    likert_scale_id      BIGINT        REFERENCES likert_scales(id),
    matrix_group         VARCHAR(500)
);

-- question_options: opciones de preguntas de selección única/múltiple y Likert
CREATE TABLE question_options (
    id            BIGSERIAL    PRIMARY KEY,
    question_id   BIGINT       NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    value         VARCHAR(500) NOT NULL,
    display_text  VARCHAR(500) NOT NULL,
    position      INT          NOT NULL,
    numeric_value NUMERIC(5,2)
);

-- submissions: una fila del archivo = un envío; formulario directo = otro envío
CREATE TABLE submissions (
    id              BIGSERIAL   PRIMARY KEY,
    survey_id       BIGINT      NOT NULL REFERENCES surveys(id) ON DELETE CASCADE,
    submitted_at    TIMESTAMP,
    row_index       INT,
    source          VARCHAR(10) NOT NULL DEFAULT 'DIRECT',
    is_inconsistent BOOLEAN     NOT NULL DEFAULT FALSE
);

-- submission_answers: una respuesta por pregunta por envío
CREATE TABLE submission_answers (
    id                BIGSERIAL PRIMARY KEY,
    submission_id     BIGINT    NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    question_id       BIGINT    NOT NULL REFERENCES questions(id),
    raw_value         TEXT,
    normalized_value  TEXT,
    is_not_applicable BOOLEAN   NOT NULL DEFAULT FALSE
);

-- submission_answer_options: opciones marcadas en selección múltiple
CREATE TABLE submission_answer_options (
    id        BIGSERIAL    PRIMARY KEY,
    answer_id BIGINT       NOT NULL REFERENCES submission_answers(id) ON DELETE CASCADE,
    option_id BIGINT       NOT NULL REFERENCES question_options(id),
    free_text VARCHAR(1000)
);

-- derived_variables: variables calculadas que el admin puede crear
CREATE TABLE derived_variables (
    id              BIGSERIAL    PRIMARY KEY,
    survey_id       BIGINT       NOT NULL REFERENCES surveys(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    output_type     VARCHAR(30)  NOT NULL,
    definition_json TEXT         NOT NULL
);

-- Índices para consultas frecuentes de análisis
CREATE INDEX idx_questions_survey      ON questions(survey_id);
CREATE INDEX idx_submissions_survey    ON submissions(survey_id);
CREATE INDEX idx_answers_submission    ON submission_answers(submission_id);
CREATE INDEX idx_answers_question      ON submission_answers(question_id);
CREATE INDEX idx_answer_opts_answer    ON submission_answer_options(answer_id);
CREATE INDEX idx_derived_survey        ON derived_variables(survey_id);
