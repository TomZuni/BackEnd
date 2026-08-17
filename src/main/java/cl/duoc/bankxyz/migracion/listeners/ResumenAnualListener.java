package cl.duoc.bankxyz.migracion.listeners;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

import lombok.RequiredArgsConstructor;

/**
 * Listener del Step "cuentaAnualStep" (Proceso 3: Generacion de Estados de
 * Cuenta Anuales). Al terminar el Step (afterStep), compila los movimientos
 * validos de movimiento_anual_procesado agrupados por cuenta_id y genera un
 * informe detallado por cuenta (total depositado, total retirado/gastado y
 * saldo neto) en resumen_anual_cuenta, tal como pide el enunciado
 * ("compilar datos anuales para cada cuenta y generar un informe detallado
 * para auditorias").
 *
 * Reemplaza al antiguo ResumenAnualTasklet: en vez de un Step adicional
 * solo de resumen, el resumen se genera como parte del cierre del mismo
 * Step, manteniendo 1 Step por proceso.
 */
@RequiredArgsConstructor
public class ResumenAnualListener implements StepExecutionListener {

    private final DataSource dataSource;

    @Override
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
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

        return stepExecution.getExitStatus();
    }
}
