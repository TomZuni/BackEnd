package cl.duoc.bankxyz.migracion.config;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import cl.duoc.bankxyz.migracion.dtos.CuentaAnualDTO;
import cl.duoc.bankxyz.migracion.dtos.InteresDTO;
import cl.duoc.bankxyz.migracion.dtos.TransaccionDTO;
import cl.duoc.bankxyz.migracion.entities.CuentaAnualEntity;
import cl.duoc.bankxyz.migracion.entities.InteresEntity;
import cl.duoc.bankxyz.migracion.entities.TransaccionEntity;
import cl.duoc.bankxyz.migracion.listeners.ResumenAnualListener;
import cl.duoc.bankxyz.migracion.listeners.ResumenTransaccionesListener;
import cl.duoc.bankxyz.migracion.processors.CuentaAnualProcessor;
import cl.duoc.bankxyz.migracion.processors.InteresProcessor;
import cl.duoc.bankxyz.migracion.processors.TransaccionProcessor;

/**
 * Configuracion del Job unico "migracionBancoXyzJob", que encadena los 3
 * procesos de migracion del Banco XYZ como 3 Steps de un mismo Job:
 *
 * 1. transaccionStep   - Reporte de Transacciones Diarias. Lee
 *    transacciones.csv, valida cada registro (monto, fecha, tipo,
 *    duplicados) y lo persiste en transaccion_procesada. Al cerrar el Step,
 *    ResumenTransaccionesListener genera el resumen de anomalias y totales
 *    por tipo en resumen_transacciones_diarias.
 *
 * 2. interesStep       - Calculo de Intereses Mensuales. Lee
 *    intereses.csv, valida cada cuenta (tipo, edad, saldo, duplicados),
 *    calcula el interes segun el tipo de cuenta y persiste el saldo final
 *    ya actualizado en interes_procesado.
 *
 * 3. cuentaAnualStep   - Generacion de Estados de Cuenta Anuales. Lee
 *    cuentas_anuales.csv, valida cada movimiento (fecha, monto,
 *    descripcion) y lo persiste en movimiento_anual_procesado. Al cerrar el
 *    Step, ResumenAnualListener compila el informe de auditoria por cuenta
 *    en resumen_anual_cuenta.
 *
 * Los resumenes ya no son Steps aparte (Tasklets): se generan como
 * StepExecutionListener#afterStep de cada Step de lectura/validacion, para
 * mantener exactamente 1 Step por proceso (3 Steps en total).
 */
@Configuration
public class MigracionBancoXyzBatchConfig {

    private static final int CHUNK_SIZE = 5;

    // ------------------------------------------------------------------
    // Proceso 1: Reporte de Transacciones Diarias
    // ------------------------------------------------------------------

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

    @Bean
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
    public Step transaccionStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 FlatFileItemReader<TransaccionDTO> transaccionItemReader,
                                 TransaccionProcessor transaccionItemProcessor,
                                 JdbcBatchItemWriter<TransaccionEntity> transaccionItemWriter,
                                 DataSource dataSource) {
        return new StepBuilder("transaccionStep", jobRepository)
                .<TransaccionDTO, TransaccionEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(transaccionItemReader)
                .processor(transaccionItemProcessor)
                .writer(transaccionItemWriter)
                .listener(new ResumenTransaccionesListener(dataSource))
                .build();
    }

    // ------------------------------------------------------------------
    // Proceso 2: Calculo de Intereses Mensuales
    // ------------------------------------------------------------------

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
    public Step interesStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             FlatFileItemReader<InteresDTO> interesItemReader,
                             InteresProcessor interesItemProcessor,
                             JdbcBatchItemWriter<InteresEntity> interesItemWriter) {
        return new StepBuilder("interesStep", jobRepository)
                .<InteresDTO, InteresEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(interesItemReader)
                .processor(interesItemProcessor)
                .writer(interesItemWriter)
                .build();
    }

    // ------------------------------------------------------------------
    // Proceso 3: Generacion de Estados de Cuenta Anuales
    // ------------------------------------------------------------------

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
    public Step cuentaAnualStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 FlatFileItemReader<CuentaAnualDTO> cuentaAnualItemReader,
                                 CuentaAnualProcessor cuentaAnualItemProcessor,
                                 JdbcBatchItemWriter<CuentaAnualEntity> cuentaAnualItemWriter,
                                 DataSource dataSource) {
        return new StepBuilder("cuentaAnualStep", jobRepository)
                .<CuentaAnualDTO, CuentaAnualEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(cuentaAnualItemReader)
                .processor(cuentaAnualItemProcessor)
                .writer(cuentaAnualItemWriter)
                .listener(new ResumenAnualListener(dataSource))
                .build();
    }

    // ------------------------------------------------------------------
    // Job unico: encadena los 3 Steps, uno por proceso de migracion
    // ------------------------------------------------------------------

    @Bean
    public Job migracionBancoXyzJob(JobRepository jobRepository,
                                     @Qualifier("transaccionStep") Step transaccionStep,
                                     @Qualifier("interesStep") Step interesStep,
                                     @Qualifier("cuentaAnualStep") Step cuentaAnualStep) {
        return new JobBuilder("migracionBancoXyzJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(transaccionStep)
                .next(interesStep)
                .next(cuentaAnualStep)
                .build();
    }
}
