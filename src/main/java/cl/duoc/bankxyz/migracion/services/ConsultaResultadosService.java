package cl.duoc.bankxyz.migracion.services;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Servicio de solo lectura para exponer, via REST, el contenido de las
 * tablas que dejan los 3 Jobs. Pensado para que la evidencia de ejecucion
 * (capturas de Postman) muestre los datos ya procesados sin depender de la
 * consola H2.
 */
@Service
@RequiredArgsConstructor
public class ConsultaResultadosService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> transaccionesProcesadas() {
        return jdbcTemplate.queryForList("SELECT * FROM transaccion_procesada ORDER BY id");
    }

    public List<Map<String, Object>> resumenTransaccionesDiarias() {
        return jdbcTemplate.queryForList("SELECT * FROM resumen_transacciones_diarias ORDER BY id");
    }

    public List<Map<String, Object>> interesesProcesados() {
        return jdbcTemplate.queryForList("SELECT * FROM interes_procesado ORDER BY id");
    }

    public List<Map<String, Object>> movimientosAnualesProcesados() {
        return jdbcTemplate.queryForList("SELECT * FROM movimiento_anual_procesado ORDER BY id");
    }

    public List<Map<String, Object>> resumenAnualPorCuenta() {
        return jdbcTemplate.queryForList("SELECT * FROM resumen_anual_cuenta ORDER BY id");
    }
}
