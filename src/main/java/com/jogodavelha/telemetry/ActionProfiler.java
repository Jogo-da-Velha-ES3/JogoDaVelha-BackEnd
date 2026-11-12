package com.jogodavelha.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Profiling leve do fluxo STOMP de gameplay. O contexto vive em ThreadLocal: como os
 * listeners de eventos de aplicação rodam na mesma thread do {@code @MessageMapping},
 * todas as etapas de uma ação compartilham o mesmo {@code actionId}.
 * Chamadas fora de uma ação em andamento (timers, schedulers) são ignoradas.
 */
public final class ActionProfiler {

    private static final Logger log = LoggerFactory.getLogger(ActionProfiler.class);
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private ActionProfiler() {
    }

    /** Inicia o perfil da ação na thread atual. Devolve o contexto para uso em finally. */
    public static Context begin(String action) {
        Context context = new Context(action, UUID.randomUUID().toString(), System.nanoTime());
        CURRENT.set(context);
        return context;
    }

    /** Registra a duração de um estágio. {@code startNanos} deve vir de {@link System#nanoTime()}. */
    public static void add(String stage, long startNanos) {
        Context context = CURRENT.get();
        if (context == null) {
            return;
        }
        context.stages.merge(stage, System.nanoTime() - startNanos, Long::sum);
    }

    /** Encerra o perfil e registra uma linha estruturada com o total e cada estágio em ms. */
    public static void end() {
        Context context = CURRENT.get();
        if (context == null) {
            return;
        }
        CURRENT.remove();
        long totalMillis = (System.nanoTime() - context.startNanos) / 1_000_000;
        StringBuilder stages = new StringBuilder();
        context.stages.forEach((stage, nanos) -> {
            if (stages.length() > 0) {
                stages.append(' ');
            }
            stages.append(stage).append("Ms=").append(nanos / 1_000_000);
        });
        log.info("gameplay.action action={} actionId={} totalMs={} {}",
                context.action, context.actionId, totalMillis, stages);
    }

    public static final class Context {
        final String action;
        final String actionId;
        final long startNanos;
        final Map<String, Long> stages = new LinkedHashMap<>();

        private Context(String action, String actionId, long startNanos) {
            this.action = action;
            this.actionId = actionId;
            this.startNanos = startNanos;
        }
    }
}
