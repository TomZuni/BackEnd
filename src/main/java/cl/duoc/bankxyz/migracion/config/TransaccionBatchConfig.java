package cl.duoc.bankxyz.migracion.config;

import org.springframework.core.task.TaskExecutor;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
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

import cl.duoc.bankxyz.migracion.dtos.TransaccionDTO;
import cl.duoc.bankxyz.migracion.entities.TransaccionEntity;
import cl.duoc.bankxyz.migracion.listeners.BatchRetryListener;
import cl.duoc.bankxyz.migracion.listeners.BatchStepListener;
import cl.duoc.bankxyz.migracion.processors.TransaccionProcessor;
import cl.duoc.bankxyz.migracion.tasklets.ResumenTransaccionesTasklet;

/**
 * Configuracion del Job "transaccionesJob" (Proceso 1: Reporte de
 * Transacciones Diarias).
 *
 * Step 1 (transaccionStep): lee transacciones.csv, valida cada registro
 * (monto, fecha, tipo, duplicados) y lo persiste en transaccion_procesada.
 * Corre en 3 hilos paralelos sobre chunks de 5, con skip/retry y listeners
 * (Semana 2).
 * Step 2 (resumenTransaccionesStep): compila un resumen de anomalias y
 * totales por tipo en resumen_transacciones_diarias.
 */
@Configuration
public class TransaccionBatchConfig {

    @Value("${bankxyz.batch.chunk-size:5}")
    private int chunkSize;

    @Value("${bankxyz.archivo-transacciones:classpath:data/transacciones.csv}")
    private Resource archivoTransacciones;

    @Bean
    public FlatFileItemReader<TransaccionDTO> transaccionItemReader() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("id", "fecha", "monto", "tipo");

        BeanWrapperFieldSetMapper<TransaccionDTO> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(TransaccionDTO.class);

        DefaultLineMapper<TransaccionDTO> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        return new FlatFileItemReaderBuilder<TransaccionDTO>()
                .name("transaccionItemReader")
                .resource(archivoTransacciones)
                .linesToSkip(1) // encabezado del CSV
                .lineMapper(lineMapper)
                .build();
    }

    /**
     * FlatFileItemReader NO es thread-safe: si el Step usa un taskExecutor
     * con varios hilos, dos hilos podrian llamar a read() al mismo tiempo y
     * corromper la posicion del archivo. SynchronizedItemStreamReader
     * sincroniza el acceso al reader real para que la lectura sea segura;
     * el paralelismo real ocurre en el processor y el writer de cada chunk.
     */
    @Bean
    public SynchronizedItemStreamReader<TransaccionDTO> transaccionItemReaderSincronizado(
            FlatFileItemReader<TransaccionDTO> transaccionItemReader) {
        SynchronizedItemStreamReader<TransaccionDTO> reader = new SynchronizedItemStreamReader<>();
        reader.setDelegate(transaccionItemReader);
        return reader;
    }

    /**
     * @StepScope es obligatorio aqui: el processor guarda un Set de claves
     * vistas (deteccion de duplicados) como estado de instancia. Sin
     * @StepScope, Spring crea un unico bean singleton para toda la vida de
     * la aplicacion, y ese Set arrastraria los duplicados de la PRIMERA
     * ejecucion del Job hacia la segunda (el Job se dispara manualmente via
     * POST, se puede correr mas de una vez). Con @StepScope se crea una
     * instancia nueva (con su Set vacio) en cada ejecucion del Step.
     */
    @Bean
    @StepScope
    public TransaccionProcessor transaccionItemProcessor() {
        return new TransaccionProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<TransaccionEntity> transaccionItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<TransaccionEntity>()
                .dataSource(dataSource)
                .sql("INSERT INTO transaccion_procesada "
                        + "(transaccion_id, fecha, monto, tipo, estado, motivo_rechazo) "
                        + "VALUES (:transaccionId, :fecha, :monto, :tipo, :estado, :motivoRechazo)")
                .beanMapped()
                .build();
    }

    @Bean
    public BatchStepListener transaccionStepListener() {
        return new BatchStepListener();
    }

    @Bean
    public Step transaccionStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 SynchronizedItemStreamReader<TransaccionDTO> transaccionItemReaderSincronizado,
                                 TransaccionProcessor transaccionItemProcessor,
                                 JdbcBatchItemWriter<TransaccionEntity> transaccionItemWriter,
                                 TaskExecutor batchTaskExecutor) {
        return new StepBuilder("transaccionStep", jobRepository)
                .<TransaccionDTO, TransaccionEntity>chunk(chunkSize, transactionManager)
                .reader(transaccionItemReaderSincronizado)
                .processor(transaccionItemProcessor)
                .writer(transaccionItemWriter)
                // Escalamiento: 3 hilos en paralelo procesando chunks de 5
                .taskExecutor(batchTaskExecutor)
                .faultTolerant()
                // Tolerancia a fallos - omision: CSV con una linea mal formada
                // (columna de mas/de menos) no debe tumbar todo el Job
                .skip(FlatFileParseException.class)
                // ni un fallo puntual de BD que persiste tras los reintentos
                .skip(DataAccessException.class)
                .skipLimit(20)
                // Tolerancia a fallos - reintento: perdida de conexion o
                // timeout transitorio con la BD si vale la pena reintentar
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                // BatchStepListener implementa 2 interfaces (StepExecutionListener
                // y SkipListener); se castea explicito para que el compilador
                // sepa cual overload de .listener() usar en cada llamada.
                .listener((StepExecutionListener) transaccionStepListener())
                .listener((SkipListener<Object, Object>) transaccionStepListener())
                .listener(new BatchRetryListener())
                .build();
    }

    @Bean
    public Step resumenTransaccionesStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          DataSource dataSource) {
        return new StepBuilder("resumenTransaccionesStep", jobRepository)
                .tasklet(new ResumenTransaccionesTasklet(dataSource), transactionManager)
                .build();
    }

    @Bean
    public Job transaccionesJob(JobRepository jobRepository,
                                 @Qualifier("transaccionStep") Step transaccionStep,
                                 @Qualifier("resumenTransaccionesStep") Step resumenTransaccionesStep) {
        return new JobBuilder("transaccionesJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(transaccionStep)
                .next(resumenTransaccionesStep)
                .build();
    }
}
