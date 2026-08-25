package cl.duoc.bankxyz.migracion.services;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.stereotype.Service;

import cl.duoc.bankxyz.migracion.exceptions.BatchJobLaunchException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Encapsula el disparo manual del Job "transaccionesJob" (Proceso 1:
 * Reporte de Transacciones Diarias).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionJobService {

    private final JobLauncher jobLauncher;
    private final Job transaccionesJob;
    private final JobExplorer jobExplorer;

    public JobExecution ejecutar() {
        try {
            JobParametersBuilder parametersBuilder = new JobParametersBuilder(jobExplorer)
                    .getNextJobParameters(transaccionesJob);
            return jobLauncher.run(transaccionesJob, parametersBuilder.toJobParameters());
        } catch (JobExecutionAlreadyRunningException | JobRestartException
                | JobInstanceAlreadyCompleteException | JobParametersInvalidException ex) {
            log.error("No fue posible iniciar el Job transaccionesJob", ex);
            throw new BatchJobLaunchException("No fue posible iniciar el Job transaccionesJob: " + ex.getMessage(), ex);
        }
    }
}
