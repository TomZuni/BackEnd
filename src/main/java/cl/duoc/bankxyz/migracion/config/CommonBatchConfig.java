package cl.duoc.bankxyz.migracion.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Bean compartido por los 3 Jobs: el PlatformTransactionManager que Spring
 * Batch usa para envolver cada chunk (o cada tasklet) en una transaccion
 * JDBC sobre el mismo DataSource H2.
 */
@Configuration
public class CommonBatchConfig {

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
