package com.github.t1.deployer.tools;

import static java.util.Collections.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Map;

import javax.interceptor.*;

import org.mockito.internal.util.MockUtil;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.*;

/** Adds an {@link Interceptor} around a target object by using Mockito. */
public class InterceptorMock<T> {
    public static <T> InterceptorMock<T> intercept(T target) {
        return new InterceptorMock<>(target);
    }

    private final T target;
    private final Class<T> type;
    private final T mock;

    private InterceptorMock(T target) {
        this.target = target;
        this.type = type(target);
        this.mock = mock(type);
    }

    @SuppressWarnings("unchecked")
    private Class<T> type(T target) {
        if (mockingDetails(target).isMock()) {
            // getTypeToMock should be supplied by MockingDetails
            return new MockUtil().getMockSettings(target).getTypeToMock();
        } else {
            return (Class<T>) target.getClass();
        }
    }

    public T with(Object interceptor) {
        Answer<?> answer = new InterceptorAnswer(interceptor);
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())
                    || Object.class.equals(method.getDeclaringClass()))
                continue;
            stub(answer, method);
        }
        return mock;
    }

    private void stub(Answer<?> answer, Method method) {
        Stubber stubber = isIntercepted(method) ? doAnswer(answer) : doAnswer(callTarget);
        try {
            method.invoke(stubber.when(mock), args(method.getParameterTypes().length));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** this is not the complete story (according to the spec), but good enough for me and now */
    private boolean isIntercepted(Method method) {
        for (Annotation annotation : method.getAnnotations())
            if (annotation.annotationType().getAnnotation(InterceptorBinding.class) != null)
                return true;
        return false;
    }

    private Object[] args(int n) {
        Object[] args = new Object[n];
        for (int i = 0; i < n; i++) {
            args[i] = anyObject();
        }
        return args;
    }

    private final Answer<Object> callTarget = new Answer<Object>() {
        @Override
        public Object answer(InvocationOnMock invocation) throws ReflectiveOperationException {
            return invocation.getMethod().invoke(target, invocation.getArguments());
        }
    };

    private class InterceptorAnswer implements Answer<Object> {
        private final Object interceptor;
        private final Method aroundInvokeMethod;

        public InterceptorAnswer(Object interceptor) {
            this.interceptor = interceptor;
            this.aroundInvokeMethod = findAroundInvoke(interceptor);
        }

        private Method findAroundInvoke(Object interceptor) {
            for (Method method : interceptor.getClass().getDeclaredMethods())
                if (method.isAnnotationPresent(AroundInvoke.class)) {
                    method.setAccessible(true);
                    return method;
                }
            throw new IllegalArgumentException("no @AroundInvoke in " + interceptor.getClass());
        }

        @Override
        public Object answer(final InvocationOnMock invocation) throws Exception {
            return aroundInvokeMethod.invoke(interceptor, new InvocationContext() {
                private Object[] parameters = invocation.getArguments();

                @Override
                public void setParameters(Object[] params) {
                    this.parameters = params;
                }

                @Override
                public Object proceed() throws Exception {
                    try {
                        return invocation.getMethod().invoke(target, parameters);
                    } catch (InvocationTargetException e) {
                        Throwable targetException = e.getTargetException();
                        targetException.printStackTrace();
                        throw new RuntimeException("can't proceed", targetException);
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    }
                }

                @Override
                public Object getTimer() {
                    return null;
                }

                @Override
                public Object getTarget() {
                    return target;
                }

                @Override
                public Object[] getParameters() {
                    return invocation.getArguments();
                }

                @Override
                public Method getMethod() {
                    return invocation.getMethod();
                }

                @Override
                public Map<String, Object> getContextData() {
                    return emptyMap();
                }

                @Override
                public Constructor<?> getConstructor() {
                    return null;
                }
            });
        }
    }
}