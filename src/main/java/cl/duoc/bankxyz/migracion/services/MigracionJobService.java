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
 * Encapsula el disparo manual del Job unico "migracionBancoXyzJob", que
 * encadena los 3 procesos de migracion del Banco XYZ (transacciones,
 * intereses y cuentas anuales) como 3 Steps de un mismo Job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigracionJobService {

    private final JobLauncher jobLauncher;
    private final Job migracionBancoXyzJob;
    private final JobExplorer jobExplorer;

    public JobExecution ejecutar() {
        try {
            JobParametersBuilder parametersBuilder = new JobParametersBuilder(jobExplorer)
                    .getNextJobParameters(migracionBancoXyzJob);
            return jobLauncher.run(migracionBancoXyzJob, parametersBuilder.toJobParameters());
        } catch (JobExecutionAlreadyRunningException | JobRestartException
                | JobInstanceAlreadyCompleteException | JobParametersInvalidException ex) {
            log.error("No fue posible iniciar el Job migracionBancoXyzJob", ex);
            throw new BatchJobLaunchException("No fue posible iniciar el Job migracionBancoXyzJob: " + ex.getMessage(), ex);
        }
    }
}
