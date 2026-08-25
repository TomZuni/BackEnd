package cl.duoc.bankxyz.migracion.tasklets;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

import lombok.RequiredArgsConstructor;

/**
 * Segundo Step del Job "transaccionesJob": lee lo que dejo el primer Step en
 * transaccion_procesada y genera una fila de resumen (anomalias detectadas y
 * totales por tipo) en resumen_transacciones_diarias, tal como pide el
 * enunciado ("detectar anomalias y generar un resumen").
 */
@RequiredArgsConstructor
public class ResumenTransaccionesTasklet implements Tasklet {

    private final DataSource dataSource;

    @Override
    public RepeatStatus execute(@NonNull org.springframework.batch.core.StepContribution contribution,
                                 @NonNull org.springframework.batch.core.scope.context.ChunkContext chunkContext) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
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

        return RepeatStatus.FINISHED;
    }
}
