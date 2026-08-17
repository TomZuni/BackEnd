package cl.duoc.bankxyz.migracion.listeners;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

import lombok.RequiredArgsConstructor;

/**
 * Listener del Step "transaccionStep" (Proceso 1: Reporte de Transacciones
 * Diarias). Al terminar el Step (afterStep), compila lo que el mismo Step
 * dejo en transaccion_procesada y genera una fila de resumen (anomalias
 * detectadas y totales por tipo) en resumen_transacciones_diarias, tal como
 * pide el enunciado ("detectar anomalias y generar un resumen").
 *
 * Reemplaza al antiguo ResumenTransaccionesTasklet: en vez de un Step
 * adicional solo de resumen, el resumen se genera como parte del cierre del
 * mismo Step, manteniendo 1 Step por proceso.
 */
@RequiredArgsConstructor
public class ResumenTransaccionesListener implements StepExecutionListener {

    private final DataSource dataSource;

    @Override
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        long jobExecutionId = stepExecution.getJobExecutionId();

        Integer totalRegistros = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaccion_procesada", Integer.class);
        Integer totalValidas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaccion_procesada WHERE estado = 'VALIDA'", Integer.class);
        Integer totalRechazadas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaccion_procesada WHERE estado = 'RECHAZADA'", Integer.class);
        BigDecimal totalCreditos = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(monto), 0) FROM transaccion_procesada WHERE estado = 'VALIDA' AND tipo = 'credito'",
                BigDecimal.class);
        BigDecimal totalDebitos = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(monto), 0) FROM transaccion_procesada WHERE estado = 'VALIDA' AND tipo = 'debito'",
                BigDecimal.class);

        jdbcTemplate.update(
                "INSERT INTO resumen_transacciones_diarias "
                        + "(job_execution_id, fecha_generacion, total_registros, total_validas, total_rechazadas, "
                        + "monto_total_creditos, monto_total_debitos) VALUES (?, ?, ?, ?, ?, ?, ?)",
                jobExecutionId, LocalDateTime.now(), totalRegistros, totalValidas, totalRechazadas,
                totalCreditos, totalDebitos);

        return stepExecution.getExitStatus();
    }
}
