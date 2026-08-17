-- =========================================================================
-- Tablas de negocio para los 3 procesos batch del Banco XYZ.
-- Se recrean cada vez que arranca la aplicacion (spring.sql.init.mode=always).
-- =========================================================================

-- 1) Reporte de Transacciones Diarias --------------------------------------
DROP TABLE IF EXISTS transaccion_procesada;
CREATE TABLE transaccion_procesada (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaccion_id      BIGINT         NOT NULL,
    fecha               DATE,
    monto               DECIMAL(12, 2),
    tipo                VARCHAR(20),
    estado              VARCHAR(20)    NOT NULL,   -- VALIDA / RECHAZADA
    motivo_rechazo      VARCHAR(200)
);

DROP TABLE IF EXISTS resumen_transacciones_diarias;
CREATE TABLE resumen_transacciones_diarias (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_execution_id        BIGINT         NOT NULL,
    fecha_generacion        TIMESTAMP      NOT NULL,
    total_registros         INT            NOT NULL,
    total_validas           INT            NOT NULL,
    total_rechazadas        INT            NOT NULL,
    monto_total_creditos    DECIMAL(14, 2) NOT NULL,
    monto_total_debitos     DECIMAL(14, 2) NOT NULL
);

-- 2) Calculo de Intereses Mensuales ----------------------------------------
DROP TABLE IF EXISTS interes_procesado;
CREATE TABLE interes_procesado (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    cuenta_id           BIGINT         NOT NULL,
    nombre              VARCHAR(100),
    tipo_cuenta         VARCHAR(20),
    edad                INT,
    saldo_original      DECIMAL(12, 2),
    tasa_interes        DECIMAL(6, 4),
    interes_calculado   DECIMAL(12, 2),
    saldo_final         DECIMAL(12, 2),
    estado              VARCHAR(20)    NOT NULL,   -- VALIDA / RECHAZADA
    motivo_rechazo      VARCHAR(200)
);

-- 3) Generacion de Estados de Cuenta Anuales -------------------------------
DROP TABLE IF EXISTS movimiento_anual_procesado;
CREATE TABLE movimiento_anual_procesado (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    cuenta_id           BIGINT         NOT NULL,
    fecha               DATE,
    tipo_transaccion    VARCHAR(20),
    monto               DECIMAL(12, 2),
    descripcion         VARCHAR(200),
    estado              VARCHAR(20)    NOT NULL,   -- VALIDA / RECHAZADA
    motivo_rechazo      VARCHAR(200)
);

DROP TABLE IF EXISTS resumen_anual_cuenta;
CREATE TABLE resumen_anual_cuenta (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_execution_id        BIGINT         NOT NULL,
    cuenta_id                BIGINT         NOT NULL,
    total_depositos          DECIMAL(14, 2) NOT NULL,
    total_retiros_compras    DECIMAL(14, 2) NOT NULL,
    saldo_neto               DECIMAL(14, 2) NOT NULL,
    cantidad_movimientos     INT            NOT NULL
);
