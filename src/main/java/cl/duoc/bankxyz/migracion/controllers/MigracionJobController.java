package cl.duoc.bankxyz.migracion.controllers;

import java.util.Map;

import org.springframework.batch.core.JobExecution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.bankxyz.migracion.exceptions.BatchJobLaunchException;
import cl.duoc.bankxyz.migracion.services.MigracionJobService;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint REST para disparar manualmente el Job unico "migracionBancoXyzJob",
 * que ejecuta encadenados los 3 procesos de migracion del Banco XYZ
 * (transacciones diarias, intereses mensuales y estados de cuenta anuales).
 * Se prueba con Postman:
 *
 *   POST http://localhost:8081/api/migracion/procesar
 */
@RestController
@RequestMapping("/api/migracion")
@RequiredArgsConstructor
public class MigracionJobController {

    private final MigracionJobService migracionJobService;

    @PostMapping("/procesar")
    public ResponseEntity<Map<String, Object>> procesar() {
        JobExecution execution = migracionJobService.ejecutar();
        return ResponseEntity.ok(Map.of(
                "jobExecutionId", execution.getId(),
                "estado", execution.getStatus().toString(),
                "exitStatus", execution.getExitStatus().getExitCode()));
    }

    @ExceptionHandler(BatchJobLaunchException.class)
    public ResponseEntity<Map<String, Object>> alNoPoderIniciarElJob(BatchJobLaunchException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
