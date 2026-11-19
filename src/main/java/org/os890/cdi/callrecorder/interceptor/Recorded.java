// SPDX-License-Identifier: Apache-2.0
package org.os890.cdi.callrecorder.interceptor;import jakarta.annotation.Priority;import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.*;import java.io.Serializable;import java.lang.annotation.*;import org.os890.cdi.callrecorder.core.CallRecorder;
import static java.lang.annotation.ElementType.*;import static java.lang.annotation.RetentionPolicy.RUNTIME;
@Inherited@InterceptorBinding@Target({TYPE,METHOD})@Retention(RUNTIME)public@interface Recorded{@Nonbinding Mode mode()default Mode.MINIMAL;
enum Mode{MINIMAL,DETAILED}@Recorded@Interceptor@Priority(Interceptor.Priority.LIBRARY_BEFORE)public static class CallRecorderInterceptor implements
Serializable{private static final long serialVersionUID=1L;@AroundInvoke public Object record(InvocationContext ctx)throws Exception{
return CallRecorder.get().record(ctx.getTarget().getClass(),ctx.getMethod(),isDetailed(ctx),ctx::proceed);
}private static boolean isDetailed(InvocationContext ctx){Recorded recorded=ctx.getMethod().getAnnotation(Recorded.class);
if(recorded==null){recorded=ctx.getTarget().getClass().getAnnotation(Recorded.class);}return recorded!=null&&recorded.mode()==Recorded.Mode.DETAILED;
}}}
