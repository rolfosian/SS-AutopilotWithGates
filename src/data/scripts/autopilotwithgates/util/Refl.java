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
    private static final MethodHandle setMethodAccessible;

    private static final MethodHandle getFieldNameHandle;
    private static final MethodHandle getFieldTypeHandle;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Class<?> methodClass = Class.forName("java.lang.reflect.Method", false, Class.class.getClassLoader());
            Class<?> constructorClass = Class.forName("java.lang.reflect.Constructor", false, Class.class.getClassLoader());
            Class<?> fieldClass = Class.forName("java.lang.reflect.Field", false, Class.class.getClassLoader());

            getFieldNameHandle = lookup.findVirtual(fieldClass, "getName", MethodType.methodType(String.class));
            getFieldTypeHandle = lookup.findVirtual(fieldClass, "getType", MethodType.methodType(Class.class));

            getMethodNameHandle = lookup.findVirtual(methodClass, "getName", MethodType.methodType(String.class));
            getParameterTypesHandle = lookup.findVirtual(methodClass, "getParameterTypes", MethodType.methodType(Class[].class));
            getReturnTypeHandle = lookup.findVirtual(methodClass, "getReturnType", MethodType.methodType(Class.class));
            invokeMethodHandle = lookup.findVirtual(methodClass, "invoke", MethodType.methodType(Object.class, Object.class, Object[].class));
            setMethodAccessible = lookup.findVirtual(methodClass, "setAccessible", MethodType.methodType(void.class, boolean.class));

            constructorNewInstanceHandle = lookup.findVirtual(constructorClass, "newInstance", MethodType.methodType(Object.class, Object[].class));
            getConstructorParameterTypesHandle = lookup.findVirtual(constructorClass, "getParameterTypes", MethodType.methodType(Class[].class));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?> getFieldType(Object field) {
        try {
            return (Class<?>) getFieldTypeHandle.invoke(field);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getFieldByType(Class<?> type, Class<?> cls) {
        try {
            for (Object field : cls.getDeclaredFields()) {
                if (((Class<?>)getFieldTypeHandle.invoke(field)) == type) {
                    return field;
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static Object getFieldByInterface(Class<?> interfc, Class<?> cls) {
        try {
            for (Object field : cls.getDeclaredFields()) {
                if (interfc.isAssignableFrom(((Class<?>)getFieldTypeHandle.invoke(field)))) {
                    return field;
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static String getFieldName(Object field) {
        try {
            return (String) getFieldNameHandle.invoke(field);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getFieldByName(String name, Class<?> cls) {
        try {
            for (Object field : cls.getDeclaredFields()) {
                if (((String)getFieldNameHandle.invoke(field)).equals(name)) {
                    return field;
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return null;
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

    public static Object getMethodDeclared(String methodName, Class<?> cls) {
        for (Object method : cls.getDeclaredMethods()) {
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

    public static Object invokePrivateMethodDirectly(Object method, Object instance, Object... arguments) {
        try {
            setMethodAccessible.invoke(method, true);
            return invokeMethodHandle.invoke(method, instance, arguments);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object invokeMethodDirectly(Object method, Object instance, Object... arguments) {
        try {
            return invokeMethodHandle.invoke(method, instance, arguments);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getMethodDeclaredAndInvokeDirectly(String methodName, Object instance, Object... arguments) {
        Object method = getMethodDeclared(methodName, instance.getClass());
        if (method == null) return null;
        return invokePrivateMethodDirectly(method, instance, arguments);
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

    public static Object getMethodFromSuperClass(String methodName, Class<?> cls) {
        try {
            Class<?> currentClass = cls;

            while (currentClass != null) {
                for (Object method : currentClass.getDeclaredMethods()) {
                    if (getMethodNameHandle.invoke(method).equals(methodName)) {
                        return method;
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static void init() {}
}
