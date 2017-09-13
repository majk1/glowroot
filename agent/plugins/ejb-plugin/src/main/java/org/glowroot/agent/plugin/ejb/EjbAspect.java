package org.glowroot.agent.plugin.ejb;

import org.glowroot.agent.plugin.api.*;
import org.glowroot.agent.plugin.api.weaving.*;

public class EjbAspect {

    public static class EjbClassMeta {

        private String className;

        public EjbClassMeta(Class clz) {
            className = clz.getName();
        }

        public String getClassName() {
            return className;
        }
    }

    @Pointcut(
            classAnnotation = "javax.ejb.Stateless|javax.ejb.Stateful|javax.ejb.Singleton",
            methodModifiers = {MethodModifier.NOT_STATIC},
            methodParameterTypes = {".."},
            timerName = "EJB method"
    )
    public static class EjbAdvice {

        private static final TimerName TIMER_NAME = Agent.getTimerName(EjbAdvice.class);
        private static final boolean TRACE_EJB_INIT = Agent.getConfigService("ejb").getBooleanProperty("traceEjbInitMethods").value();

        @OnBefore
        public static TraceEntry onBefore(ThreadContext context, @BindMethodName String methodName, @BindClassMeta EjbClassMeta classMeta) {
            if (!TRACE_EJB_INIT && "<init>".equals(methodName)) {
                return null;
            }

            // context.setTransactionName(classMeta.getClassName() + "#" + methodName, ThreadContext.Priority.USER_PLUGIN);
            return context.startTraceEntry(MessageSupplier.create("EJB: {}#{}", classMeta.getClassName(), methodName), TIMER_NAME);
        }

        @OnReturn
        public static void onReturn(@BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null) {
                traceEntry.end();
            }
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable t, @BindTraveler TraceEntry traceEntry) {
            if (traceEntry != null) {
                traceEntry.endWithError(t);
            }
        }
    }

}
