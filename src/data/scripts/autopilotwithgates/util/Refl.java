package data.scripts.autopilotwithgates.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Refl {
    private static final MethodHandle getMethodNameHandle;
    private static final MethodHandle getParameterTypesHandle;
    private static final MethodHandle getReturnTypeHandle;
    private static final MethodHandle getConstructorParameterTypesHandle;
    private static final MethodHandle constructorNewInstanceHandle;
    private static final MethodHandle invokeMethodHandle;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> methodClass = Class.forName("java.lang.reflect.Method", false, Class.class.getClassLoader());
            Class<?> constructorClass = Class.forName("java.lang.reflect.Constructor", false, Class.class.getClassLoader());

            getMethodNameHandle = lookup.findVirtual(methodClass, "getName", MethodType.methodType(String.class));
            getParameterTypesHandle = lookup.findVirtual(methodClass, "getParameterTypes", MethodType.methodType(Class[].class));
            getReturnTypeHandle = lookup.findVirtual(methodClass, "getReturnType", MethodType.methodType(Class.class));
            invokeMethodHandle = lookup.findVirtual(methodClass, "invoke", MethodType.methodType(Object.class, Object.class, Object[].class));

            constructorNewInstanceHandle = lookup.findVirtual(constructorClass, "newInstance", MethodType.methodType(Object.class, Object[].class));
            getConstructorParameterTypesHandle = lookup.findVirtual(constructorClass, "getParameterTypes", MethodType.methodType(Class[].class));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?> getReturnType(Object method) {
        try {
            return (Class<?>) getReturnTypeHandle.invoke(method);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object instantiateClass(Object ctor, Object... args) {
        try {
            return constructorNewInstanceHandle.invoke(ctor, args);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getConstructor(Class<?> cls, Class<?>[] paramTypes) {
        try {
            return cls.getConstructor(paramTypes);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getMethodExplicit(String methodName, Class<?> cls, Class<?>[] parameterTypes) {
        for (Object method : cls.getDeclaredMethods()) {
            try {
                if (((String) getMethodNameHandle.invoke(method)).equals(methodName)) {
                    Class<?>[] targetParameterTypes = (Class<?>[]) getParameterTypesHandle.invoke(method);
                    if (targetParameterTypes.length != parameterTypes.length)
                        continue;
    
                    boolean match = true;
                    for (int i = 0; i < targetParameterTypes.length; i++) {
                        if (!targetParameterTypes[i].getCanonicalName().equals(parameterTypes[i].getCanonicalName())) {
                            match = false;
                            break;
                        }
                    }
    
                    if (match) return method;
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static Object getMethod(String methodName, Class<?> cls) {
        for (Object method : cls.getMethods()) {
            try {
                if (((String)getMethodNameHandle.invoke(method)).equals(methodName)) {
                    return method;
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static Object invokeMethodDirectly(Object method, Object instance, Object... arguments) {
        try {
            return invokeMethodHandle.invoke(method, instance, arguments);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getMethodAndInvokeDirectly(String methodName, Object instance, Object... arguments) {
        Object method = getMethod(methodName, instance.getClass());
        if (method == null) return null;
        return invokeMethodDirectly(method, instance, arguments);
    }

    public static Class<?>[] getMethodParamTypes(Object method) {
        try {
            return (Class<?>[]) getParameterTypesHandle.invoke(method);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?>[] getConstructorParamTypes(Object ctor) {
        try {
            return (Class<?>[]) getConstructorParameterTypesHandle.invoke(ctor);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void init() {}
}
