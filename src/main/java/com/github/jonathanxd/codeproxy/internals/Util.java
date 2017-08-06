/*
 *      CodeProxy - Proxy Pattern written on top of CodeAPI! <https://github.com/JonathanxD/CodeProxy>
 *
 *         The MIT License (MIT)
 *
 *      Copyright (c) 2017 TheRealBuggy/JonathanxD (https://github.com/JonathanxD/ & https://github.com/TheRealBuggy/) <jonathan.scripter@programmer.net>
 *      Copyright (c) contributors
 *
 *
 *      Permission is hereby granted, free of charge, to any person obtaining a copy
 *      of this software and associated documentation files (the "Software"), to deal
 *      in the Software without restriction, including without limitation the rights
 *      to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *      copies of the Software, and to permit persons to whom the Software is
 *      furnished to do so, subject to the following conditions:
 *
 *      The above copyright notice and this permission notice shall be included in
 *      all copies or substantial portions of the Software.
 *
 *      THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *      IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *      FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *      AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *      LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *      OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *      THE SOFTWARE.
 */
package com.github.jonathanxd.codeproxy.internals;

import com.github.jonathanxd.codeapi.CodeInstruction;
import com.github.jonathanxd.codeapi.MutableCodeSource;
import com.github.jonathanxd.codeapi.Types;
import com.github.jonathanxd.codeapi.base.Access;
import com.github.jonathanxd.codeapi.base.ArrayConstructor;
import com.github.jonathanxd.codeapi.base.CodeModifier;
import com.github.jonathanxd.codeapi.base.MethodDeclaration;
import com.github.jonathanxd.codeapi.base.MethodInvocation;
import com.github.jonathanxd.codeapi.base.TypeSpec;
import com.github.jonathanxd.codeapi.base.VariableAccess;
import com.github.jonathanxd.codeapi.common.FieldRef;
import com.github.jonathanxd.codeapi.factory.Factories;
import com.github.jonathanxd.codeapi.factory.InvocationFactory;
import com.github.jonathanxd.codeapi.factory.PartFactory;
import com.github.jonathanxd.codeapi.literal.Literals;
import com.github.jonathanxd.codeapi.type.CodeType;
import com.github.jonathanxd.codeapi.type.Generic;
import com.github.jonathanxd.codeapi.util.CodePartUtil;
import com.github.jonathanxd.codeapi.util.ImplicitCodeType;
import com.github.jonathanxd.codeapi.util.conversion.ConversionsKt;
import com.github.jonathanxd.codeproxy.ProxyData;
import com.github.jonathanxd.codeproxy.gen.CustomGen;
import com.github.jonathanxd.codeproxy.gen.CustomHandlerGenerator;
import com.github.jonathanxd.codeproxy.info.MethodInfo;
import com.github.jonathanxd.iutils.collection.Collections3;
import com.github.jonathanxd.iutils.exception.RethrowException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.collections.CollectionsKt;

public class Util {

    /**
     * {@code List<Class<? extends CustomHandlerGenerator>>}
     */
    static CodeType LIST_OF_CUSTOM_HANDLER_GENERATORS = Generic.type(List.class).of(
            Generic.type(Class.class).of(Generic.wildcard().extends$(CustomHandlerGenerator.class)));

    /**
     * {@code List<Class<? extends CustomGen>>}
     */
    static CodeType LIST_OF_CUSTOM_GENERATORS = Generic.type(List.class).of(
            Generic.type(Class.class).of(Generic.wildcard().extends$(CustomGen.class)));

    static CodeInstruction methodToReflectInvocation(Method m, FieldRef lookupFieldRef) {
        return InvocationFactory.invokeConstructor(MethodInfo.class,
                MethodInfo.CONSTRUCTOR_SPEC, // Lookup, Class, String, Class, Class[]
                Collections3.listOf(
                        PartFactory.fieldAccess().base(lookupFieldRef).build(),
                        Literals.CLASS(m.getDeclaringClass()),
                        Literals.STRING(m.getName()),
                        Literals.CLASS(m.getReturnType()),
                        Factories.createArray(
                                Class[].class,
                                Collections.singletonList(Literals.INT(m.getParameterCount())),
                                Arrays.stream(m.getParameterTypes()).map(Literals::CLASS).collect(Collectors.toList())
                        )
                )
        );
    }

    static MethodDeclaration fromMethod(Method m) {
        return MethodDeclaration.Builder.builder()
                .name(m.getName())
                .modifiers(CodeModifier.PUBLIC)
                .parameters(ConversionsKt.getCodeParameters(Arrays.asList(m.getParameters())))
                .returnType(m.getReturnType())
                .body(MutableCodeSource.create())
                .build();
    }

    static List<CodeInstruction> cast(List<CodeInstruction> list, CodeType target) {
        return list.stream().map(argument -> {
            Type type = CodePartUtil.getType(argument);

            if (ImplicitCodeType.isArray(type))
                return argument;

            if (ImplicitCodeType.compareTo(type, target) != 0) {
                return Factories.cast(type, target, argument);
            }

            return argument;
        }).collect(Collectors.toList());
    }

    static CodeInstruction constructProxyData(ProxyData proxyData,
                                              Type IH_TYPE,
                                              String IH_NAME) {

        List<? extends CodeInstruction> arguments =
                Arrays.stream(proxyData.getInterfaces()).map(Literals::CLASS).collect(Collectors.toList());

        ArrayConstructor arrayConstructor = Factories.createArray(
                Types.CLASS.toArray(1),
                Collections.singletonList(Literals.INT(proxyData.getInterfaces().length)),
                arguments);

        return InvocationFactory.invokeConstructor(ProxyData.class,
                Factories.constructorTypeSpec(ClassLoader.class, Types.CLASS.toArray(1), Types.CLASS, IH_TYPE,
                        LIST_OF_CUSTOM_HANDLER_GENERATORS, LIST_OF_CUSTOM_GENERATORS),
                Collections3.listOf(
                        Util.getClassLoader_(),
                        arrayConstructor,
                        Literals.CLASS(proxyData.getSuperClass()),
                        Factories.accessThisField(IH_TYPE, IH_NAME),
                        Util.callListOf(CollectionsKt.map(proxyData.getCustomHandlerGeneratorsView(), Literals::CLASS)),
                        Util.callListOf(CollectionsKt.map(proxyData.getCustomGeneratorsView(), Literals::CLASS))
                )
        );
    }

    private static CodeInstruction callListOf(List<? extends CodeInstruction> lst) {
        return InvocationFactory.invokeStatic(Collections3.class,
                "listOf",
                new TypeSpec(List.class, Collections3.listOf(Generic.type("E").toArray(1))),
                Collections3.listOf(
                        Factories.createArray(Generic.type("E").toArray(1),
                                Collections3.listOf(Literals.INT(lst.size())),
                                lst)
                ));
    }

    private static MethodInvocation getClassLoader_() {
        return InvocationFactory.invokeVirtual(Class.class,
                Util.getClass_(), "getClassLoader", new TypeSpec(ClassLoader.class), Collections.emptyList());
    }

    private static MethodInvocation getClass_() {
        return InvocationFactory.invokeVirtual(
                Object.class,
                Access.THIS,
                "getClass",
                new TypeSpec(Class.class),
                Collections.emptyList()
        );
    }

    static Class<?> injectIntoClassLoader(ClassLoader classLoader, String name, byte[] bytes) {
        try {
            Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);

            defineClass.setAccessible(true);

            return (Class<?>) defineClass.invoke(classLoader, name, bytes, 0, bytes.length);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isEqual(Method o1, Method o2) {
        return o1.getName().equals(o2.getName())
                && o1.getReturnType().equals(o2.getReturnType())
                && Arrays.equals(o1.getParameterTypes(), o2.getParameterTypes());
    }

    static boolean contains(Collection<Method> methods, Method o1) {
        for (Method method : methods) {
            if (method != o1 && Util.isEqual(method, o1))
                return true;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getInstance(Class<T> cl) {
        try {
            return (T) cl.getDeclaredField("INSTANCE").get(null);
        } catch (Exception e) {
            try {
                return cl.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e1) {
                e1.addSuppressed(e);
                throw RethrowException.rethrow(e1);
            }
        }
    }
}
