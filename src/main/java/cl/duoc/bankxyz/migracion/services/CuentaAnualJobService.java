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
 * Encapsula el disparo manual del Job "cuentasAnualesJob" (Proceso 3:
 * Generacion de Estados de Cuenta Anuales).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CuentaAnualJobService {

    private final JobLauncher jobLauncher;
    private final Job cuentasAnualesJob;
    private final JobExplorer jobExplorer;

    public JobExecution ejecutar() {
        try {
            JobParametersBuilder parametersBuilder = new JobParametersBuilder(jobExplorer)
                    .getNextJobParameters(cuentasAnualesJob);
            return jobLauncher.run(cuentasAnualesJob, parametersBuilder.toJobParameters());
        } catch (JobExecutionAlreadyRunningException | JobRestartException
                | JobInstanceAlreadyCompleteException | JobParametersInvalidException ex) {
            log.error("No fue posible iniciar el Job cuentasAnualesJob", ex);
            throw new BatchJobLaunchException("No fue posible iniciar el Job cuentasAnualesJob: " + ex.getMessage(), ex);
        }
    }
}
