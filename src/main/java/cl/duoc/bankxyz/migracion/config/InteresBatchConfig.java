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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import cl.duoc.bankxyz.migracion.dtos.InteresDTO;
import cl.duoc.bankxyz.migracion.entities.InteresEntity;
import cl.duoc.bankxyz.migracion.listeners.BatchRetryListener;
import cl.duoc.bankxyz.migracion.listeners.BatchStepListener;
import cl.duoc.bankxyz.migracion.processors.InteresProcessor;

/**
 * Configuracion del Job "interesesJob" (Proceso 2: Calculo de Intereses
 * Mensuales).
 *
 * Un unico Step (interesStep): lee intereses.csv, valida cada cuenta (tipo,
 * edad, saldo, duplicados), calcula el interes segun el tipo de cuenta y
 * persiste el saldo final ya actualizado en interes_procesado. Corre en 3
 * hilos paralelos sobre chunks de 5, con skip/retry y listeners (Semana 2).
 */
@Configuration
public class InteresBatchConfig {

    private static final int CHUNK_SIZE = 5;

    @Value("${bankxyz.archivo-intereses:classpath:data/intereses.csv}")
    private Resource archivoIntereses;

    @Bean
    public FlatFileItemReader<InteresDTO> interesItemReader() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("cuentaId", "nombre", "saldo", "edad", "tipo");

        BeanWrapperFieldSetMapper<InteresDTO> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(InteresDTO.class);

        DefaultLineMapper<InteresDTO> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        return new FlatFileItemReaderBuilder<InteresDTO>()
                .name("interesItemReader")
                .resource(archivoIntereses)
                .linesToSkip(1)
                .lineMapper(lineMapper)
                .build();
    }

    @Bean
    public SynchronizedItemStreamReader<InteresDTO> interesItemReaderSincronizado(
            FlatFileItemReader<InteresDTO> interesItemReader) {
        SynchronizedItemStreamReader<InteresDTO> reader = new SynchronizedItemStreamReader<>();
        reader.setDelegate(interesItemReader);
        return reader;
    }

    @Bean
    public InteresProcessor interesItemProcessor() {
        return new InteresProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<InteresEntity> interesItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<InteresEntity>()
                .dataSource(dataSource)
                .sql("INSERT INTO interes_procesado "
                        + "(cuenta_id, nombre, tipo_cuenta, edad, saldo_original, tasa_interes, interes_calculado, "
                        + "saldo_final, estado, motivo_rechazo) "
                        + "VALUES (:cuentaId, :nombre, :tipoCuenta, :edad, :saldoOriginal, :tasaInteres, "
                        + ":interesCalculado, :saldoFinal, :estado, :motivoRechazo)")
                .beanMapped()
                .build();
    }

    @Bean
    public BatchStepListener interesStepListener() {
        return new BatchStepListener();
    }

    @Bean
    public Step interesStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             SynchronizedItemStreamReader<InteresDTO> interesItemReaderSincronizado,
                             InteresProcessor interesItemProcessor,
                             JdbcBatchItemWriter<InteresEntity> interesItemWriter,
                             TaskExecutor batchTaskExecutor) {
        return new StepBuilder("interesStep", jobRepository)
                .<InteresDTO, InteresEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(interesItemReaderSincronizado)
                .processor(interesItemProcessor)
                .writer(interesItemWriter)
                .taskExecutor(batchTaskExecutor)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skip(DataAccessException.class)
                .skipLimit(20)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .listener((StepExecutionListener) interesStepListener())
                .listener((SkipListener<Object, Object>) interesStepListener())
                .listener(new BatchRetryListener())
                .build();
    }

    @Bean
    public Job interesesJob(JobRepository jobRepository, Step interesStep) {
        return new JobBuilder("interesesJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(interesStep)
                .build();
    }
}
