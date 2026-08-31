package cl.duoc.bankxyz.migracion.config;

import org.springframework.core.task.TaskExecutor;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import cl.duoc.bankxyz.migracion.dtos.CuentaAnualDTO;
import cl.duoc.bankxyz.migracion.entities.CuentaAnualEntity;
import cl.duoc.bankxyz.migracion.listeners.BatchRetryListener;
import cl.duoc.bankxyz.migracion.listeners.BatchStepListener;
import cl.duoc.bankxyz.migracion.processors.CuentaAnualProcessor;
import cl.duoc.bankxyz.migracion.tasklets.ResumenAnualTasklet;

/**
 * Configuracion del Job "cuentasAnualesJob" (Proceso 3: Generacion de
 * Estados de Cuenta Anuales).
 *
 * Step 1 (cuentaAnualStep): lee cuentas_anuales.csv, valida cada movimiento
 * (fecha, monto, descripcion) y lo persiste en movimiento_anual_procesado.
 * Step 2 (resumenAnualStep): compila, por cada cuenta_id, el total
 * depositado, el total retirado/gastado y el saldo neto anual, para el
 * informe de auditoria en resumen_anual_cuenta.
 */
@Configuration
public class CuentaAnualBatchConfig {

    @Value("${bankxyz.batch.chunk-size:5}")
    private int chunkSize;

    @Value("${bankxyz.archivo-cuentas-anuales:classpath:data/cuentas_anuales.csv}")
    private Resource archivoCuentasAnuales;

    @Bean
    public FlatFileItemReader<CuentaAnualDTO> cuentaAnualItemReader() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("cuentaId", "fecha", "transaccion", "monto", "descripcion");

        BeanWrapperFieldSetMapper<CuentaAnualDTO> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(CuentaAnualDTO.class);

        DefaultLineMapper<CuentaAnualDTO> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        return new FlatFileItemReaderBuilder<CuentaAnualDTO>()
                .name("cuentaAnualItemReader")
                .resource(archivoCuentasAnuales)
                .linesToSkip(1)
                .lineMapper(lineMapper)
                .build();
    }

    @Bean
    public SynchronizedItemStreamReader<CuentaAnualDTO> cuentaAnualItemReaderSincronizado(
            FlatFileItemReader<CuentaAnualDTO> cuentaAnualItemReader) {
        SynchronizedItemStreamReader<CuentaAnualDTO> reader = new SynchronizedItemStreamReader<>();
        reader.setDelegate(cuentaAnualItemReader);
        return reader;
    }

    @Bean
    public CuentaAnualProcessor cuentaAnualItemProcessor() {
        return new CuentaAnualProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<CuentaAnualEntity> cuentaAnualItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<CuentaAnualEntity>()
                .dataSource(dataSource)
                .sql("INSERT INTO movimiento_anual_procesado "
                        + "(cuenta_id, fecha, tipo_transaccion, monto, descripcion, estado, motivo_rechazo) "
                        + "VALUES (:cuentaId, :fecha, :tipoTransaccion, :monto, :descripcion, :estado, :motivoRechazo)")
                .beanMapped()
                .build();
    }

    @Bean
    public BatchStepListener cuentaAnualStepListener() {
        return new BatchStepListener();
    }

    @Bean
    public Step cuentaAnualStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 SynchronizedItemStreamReader<CuentaAnualDTO> cuentaAnualItemReaderSincronizado,
                                 CuentaAnualProcessor cuentaAnualItemProcessor,
                                 JdbcBatchItemWriter<CuentaAnualEntity> cuentaAnualItemWriter,
                                 TaskExecutor batchTaskExecutor) {
        return new StepBuilder("cuentaAnualStep", jobRepository)
                .<CuentaAnualDTO, CuentaAnualEntity>chunk(chunkSize, transactionManager)
                .reader(cuentaAnualItemReaderSincronizado)
                .processor(cuentaAnualItemProcessor)
                .writer(cuentaAnualItemWriter)
                .taskExecutor(batchTaskExecutor)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skip(DataAccessException.class)
                .skipLimit(20)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .listener((StepExecutionListener) cuentaAnualStepListener())
                .listener((SkipListener<Object, Object>) cuentaAnualStepListener())
                .listener(new BatchRetryListener())
                .build();
    }

    @Bean
    public Step resumenAnualStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  DataSource dataSource) {
        return new StepBuilder("resumenAnualStep", jobRepository)
                .tasklet(new ResumenAnualTasklet(dataSource), transactionManager)
                .build();
    }

    @Bean
    public Job cuentasAnualesJob(JobRepository jobRepository,
                                  @Qualifier("cuentaAnualStep") Step cuentaAnualStep,
                                  @Qualifier("resumenAnualStep") Step resumenAnualStep) {
        return new JobBuilder("cuentasAnualesJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(cuentaAnualStep)
                .next(resumenAnualStep)
                .build();
    }
}
