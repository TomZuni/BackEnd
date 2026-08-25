package cl.duoc.bankxyz.migracion.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/**
 * Sin este listener los reintentos configurados con .retry()/.retryLimit()
 * en cada Step igual ocurren, pero no quedan registrados en ningun lado.
 * onError() se dispara una vez por cada intento fallido (antes de decidir
 * si reintenta de nuevo o si ya se agoto el retryLimit).
 */
public class BatchRetryListener implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(BatchRetryListener.class);

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
                                                   Throwable throwable) {
        log.warn("Reintento #{} en hilo '{}' tras error transitorio: {}",
                context.getRetryCount(), Thread.currentThread().getName(), throwable.getMessage());
    }
}
