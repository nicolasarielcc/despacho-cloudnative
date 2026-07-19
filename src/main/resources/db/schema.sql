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

-- Secuencia para GUIA_DESPACHO (tabla principal)
CREATE SEQUENCE SEQ_GUIA_DESPACHO
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Secuencia para GUIA_DESPACHO_PROCESADA (tabla nueva, distinta)
CREATE SEQUENCE SEQ_GUIA_PROCESADA
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- ============================================================
-- TABLA PRINCIPAL: GUIA_DESPACHO
-- Almacena las guías de despacho creadas desde el backend
-- ============================================================

CREATE TABLE GUIA_DESPACHO (
    ID              NUMBER(19)      NOT NULL PRIMARY KEY,
    CODIGO_GUIA     VARCHAR2(50)    NOT NULL UNIQUE,
    TRANSPORTISTA   VARCHAR2(200)   NOT NULL,
    FECHA_EMISION   TIMESTAMP       NOT NULL,
    ORIGEN          VARCHAR2(300)   NOT NULL,
    DESTINO         VARCHAR2(300)   NOT NULL,
    DESCRIPCION_CARGA VARCHAR2(1000),
    PESO_KG         NUMBER(10,2),
    ESTADO          VARCHAR2(20)    NOT NULL,
    URL_S3          VARCHAR2(500),
    FECHA_CREACION  TIMESTAMP       NOT NULL
);

COMMENT ON TABLE GUIA_DESPACHO IS 'Guías de despacho del sistema transportista';
COMMENT ON COLUMN GUIA_DESPACHO.ESTADO IS 'PENDIENTE | ENVIADA | CON_ERROR';

-- ============================================================
-- TABLA NUEVA: GUIA_DESPACHO_PROCESADA
-- Almacena guías procesadas desde la cola RabbitMQ
-- (TABLA DISTINTA a la usada en sumativas anteriores)
-- ============================================================

CREATE TABLE GUIA_DESPACHO_PROCESADA (
    ID                  NUMBER(19)      NOT NULL PRIMARY KEY,
    CODIGO_GUIA         VARCHAR2(50)    NOT NULL,
    TRANSPORTISTA       VARCHAR2(200),
    FECHA_EMISION       TIMESTAMP,
    ORIGEN              VARCHAR2(300),
    DESTINO             VARCHAR2(300),
    DESCRIPCION_CARGA   VARCHAR2(1000),
    PESO_KG             NUMBER(10,2),
    URL_S3              VARCHAR2(500),
    FECHA_PROCESAMIENTO TIMESTAMP       NOT NULL,
    ESTADO              VARCHAR2(20)
);

COMMENT ON TABLE GUIA_DESPACHO_PROCESADA IS 'Guías procesadas desde cola RabbitMQ (tabla NUEVA)';
COMMENT ON COLUMN GUIA_DESPACHO_PROCESADA.FECHA_PROCESAMIENTO IS 'Fecha en que se procesó desde la cola (campo NUEVO)';
