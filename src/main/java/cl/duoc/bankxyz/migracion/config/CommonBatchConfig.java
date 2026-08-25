package cl.duoc.bankxyz.migracion.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Beans compartidos por los 3 Jobs.
 */
@Configuration
public class CommonBatchConfig {

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    /**
     * Escalamiento (pauta S2, criterio "Escala el procesamiento... mediante
     * chunks y multithreading"): pool fijo de 3 hilos de ejecucion paralela
     * que usan los 3 Steps chunk-oriented. Se comparte entre los 3 Jobs
     * porque en este proyecto se disparan uno a la vez via REST (no hay
     * concurrencia real entre Jobs distintos).
     *
     * setQueueCapacity(0) + CallerRunsPolicy: si en algun momento se
     * disparara mas de un Job en paralelo y se agotan los 3 hilos, el chunk
     * extra se ejecuta en el hilo que lo solicito en vez de acumularse en
     * una cola indefinida o perderse.
     */
    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("bankxyz-batch-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
