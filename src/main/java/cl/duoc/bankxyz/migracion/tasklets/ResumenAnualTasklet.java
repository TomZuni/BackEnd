package cl.duoc.bankxyz.migracion.tasklets;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

import lombok.RequiredArgsConstructor;

/**
 * Segundo Step del Job "cuentasAnualesJob": compila los movimientos validos
 * de movimiento_anual_procesado agrupados por cuenta_id y genera un informe
 * detallado por cuenta (total depositado, total retirado/gastado y saldo
 * neto) en resumen_anual_cuenta, tal como pide el enunciado ("compilar
 * datos anuales para cada cuenta y generar un informe detallado para
 * auditorias").
 */
@RequiredArgsConstructor
public class ResumenAnualTasklet implements Tasklet {

    private final DataSource dataSource;

    @Override
    public RepeatStatus execute(@NonNull org.springframework.batch.core.StepContribution contribution,
                                 @NonNull org.springframework.batch.core.scope.context.ChunkContext chunkContext) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        long jobExecutionId = stepExecution.getJobExecutionId();

        List<Long> cuentaIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT cuenta_id FROM movimiento_anual_procesado WHERE estado = 'VALIDA' ORDER BY cuenta_id",
                Long.class);

        for (Long cuentaId : cuentaIds) {
            Map<String, Object> totales = jdbcTemplate.queryForMap(
                    "SELECT "
                            + "COALESCE(SUM(CASE WHEN monto > 0 THEN monto ELSE 0 END), 0) AS depositos, "
                            + "COALESCE(SUM(CASE WHEN monto < 0 THEN monto ELSE 0 END), 0) AS retiros, "
                            + "COUNT(*) AS cantidad "
                            + "FROM movimiento_anual_procesado WHERE estado = 'VALIDA' AND cuenta_id = ?",
                    cuentaId);

            BigDecimal depositos = (BigDecimal) totales.get("DEPOSITOS");
            BigDecimal retiros = (BigDecimal) totales.get("RETIROS");
            BigDecimal saldoNeto = depositos.add(retiros);
            long cantidad = ((Number) totales.get("CANTIDAD")).longValue();

            jdbcTemplate.update(
                    "INSERT INTO resumen_anual_cuenta "
                            + "(job_execution_id, cuenta_id, total_depositos, total_retiros_compras, saldo_neto, cantidad_movimientos) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    jobExecutionId, cuentaId, depositos, retiros, saldoNeto, cantidad);
        }

        return RepeatStatus.FINISHED;
    }
}
