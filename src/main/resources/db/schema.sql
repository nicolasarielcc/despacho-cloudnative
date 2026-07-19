-- ============================================================
-- SCRIPTS SQL — BASE DE DATOS ORACLE CLOUD
-- Asignatura: CDY2204 - Desarrollo Cloud Native
-- Sumativa 3 — Evaluación Final Transversal
-- ============================================================
--
-- INSTRUCCIONES:
--   Ejecutar en Oracle Cloud (Autonomous Database / ATP)
--   desde SQL Developer o sqlplus.
--
--   Las secuencias y tablas también pueden crearse
--   automáticamente con spring.jpa.hibernate.ddl-auto=update
--   pero se recomienda usar estos scripts en producción.
-- ============================================================

-- ============================================================
-- SECUENCIAS
-- ============================================================

-- Secuencia para CURSO (tabla principal)
CREATE SEQUENCE SEQ_CURSO
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Secuencia para INSCRIPCION (tabla nueva, distinta)
CREATE SEQUENCE SEQ_INSCRIPCION
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- ============================================================
-- TABLA PRINCIPAL: CURSO
-- Almacena los cursos creados desde el backend
-- ============================================================

CREATE TABLE CURSO (
    ID              NUMBER(19)      NOT NULL PRIMARY KEY,
    CODIGO_CURSO    VARCHAR2(50)    NOT NULL UNIQUE,
    NOMBRE          VARCHAR2(200)   NOT NULL,
    INSTRUCTOR      VARCHAR2(200)   NOT NULL,
    DESCRIPCION     VARCHAR2(1000),
    CREDITOS        NUMBER(3),
    FECHA_INICIO    TIMESTAMP       NOT NULL,
    FECHA_FIN       TIMESTAMP       NOT NULL,
    ESTADO          VARCHAR2(20)    NOT NULL,
    URL_S3          VARCHAR2(500),
    FECHA_CREACION  TIMESTAMP       NOT NULL
);

COMMENT ON TABLE CURSO IS 'Cursos del sistema cursosonline';
COMMENT ON COLUMN CURSO.ESTADO IS 'PENDIENTE | ACTIVO | FINALIZADO | CON_ERROR';

-- ============================================================
-- TABLA NUEVA: INSCRIPCION
-- Almacena inscripciones procesadas desde la cola RabbitMQ
-- (TABLA DISTINTA a la usada en sumativas anteriores)
-- ============================================================

CREATE TABLE INSCRIPCION (
    ID                  NUMBER(19)      NOT NULL PRIMARY KEY,
    CODIGO_CURSO        VARCHAR2(50)    NOT NULL,
    ESTUDIANTE          VARCHAR2(200),
    EMAIL_ESTUDIANTE    VARCHAR2(300),
    FECHA_INSCRIPCION   TIMESTAMP,
    CALIFICACION        NUMBER(3,1),
    ESTADO              VARCHAR2(20),
    FECHA_PROCESAMIENTO TIMESTAMP       NOT NULL
);

COMMENT ON TABLE INSCRIPCION IS 'Inscripciones procesadas desde cola RabbitMQ (tabla NUEVA)';
COMMENT ON COLUMN INSCRIPCION.FECHA_PROCESAMIENTO IS 'Fecha en que se procesó desde la cola (campo NUEVO)';
