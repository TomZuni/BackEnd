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
import cl.duoc.bankxyz.migracion.services.InteresJobService;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint REST para disparar manualmente el Job "interesesJob" (Proceso 2:
 * Calculo de Intereses Mensuales). Se prueba con Postman:
 *
 *   POST http://localhost:8081/api/intereses/procesar
 */
@RestController
@RequestMapping("/api/intereses")
@RequiredArgsConstructor
public class InteresJobController {

    private final InteresJobService interesJobService;

    @PostMapping("/procesar")
    public ResponseEntity<Map<String, Object>> procesar() {
        JobExecution execution = interesJobService.ejecutar();
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
