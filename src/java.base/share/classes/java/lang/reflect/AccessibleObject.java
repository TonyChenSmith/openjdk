/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang.reflect;

import java.lang.annotation.Annotation;
import java.security.AccessController;

import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.reflect.ReflectionFactory;

/**
 * The {@code AccessibleObject} class is the base class for {@code Field},
 * {@code Method}, and {@code Constructor} objects (known as <em>reflected
 * objects</em>). It provides the ability to flag a reflected object as
 * suppressing checks for Java language access control when it is used. This
 * permits sophisticated applications with sufficient privilege, such as Java
 * Object Serialization or other persistence mechanisms, to manipulate objects
 * in a manner that would normally be prohibited.
 *
 * <p> Java language access control prevents use of private members outside
 * their class; package access members outside their package; protected members
 * outside their package or subclasses; and public members outside their
 * module unless they are declared in an {@link Module#isExported(String,Module)
 * exported} package and the user {@link Module#canRead reads} their module. By
 * default, Java language access control is enforced (with one variation) when
 * {@code Field}s, {@code Method}s, or {@code Constructor}s are used to get or
 * set fields, to invoke methods, or to create and initialize new instances of
 * classes, respectively. Every reflected object checks that the code using it
 * is in an appropriate class, package, or module. </p>
 *
 * <p> The one variation from Java language access control is that the checks
 * by reflected objects assume readability. That is, the module containing
 * the use of a reflected object is assumed to read the module in which
 * the underlying field, method, or constructor is declared. </p>
 *
 * <p> Whether the checks for Java language access control can be suppressed
 * (and thus, whether access can be enabled) depends on whether the reflected
 * object corresponds to a member in an exported or open package
 * (see {@link #setAccessible(boolean)}). </p>
 *
 * @jls 6.6
 * @since 1.2
 * @revised 9
 * @spec JPMS
 */
public class AccessibleObject implements AnnotatedElement {

    /**
     * The Permission object that is used to check whether a client
     * has sufficient privilege to defeat Java language access
     * control checks.
     */
    private static final java.security.Permission ACCESS_PERMISSION =
        new ReflectPermission("suppressAccessChecks");

    static void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(ACCESS_PERMISSION);
    }

    /**
     * Convenience method to set the {@code accessible} flag for an
     * array of reflected objects with a single security check (for efficiency).
     *
     * <p> This method may be used to enable access to all reflected objects in
     * the array when access to each reflected object can be enabled as
     * specified by {@link #setAccessible(boolean) setAccessible(boolean)}. </p>
     *
     * <p>If there is a security manager, its
     * {@code checkPermission} method is first called with a
     * {@code ReflectPermission("suppressAccessChecks")} permission.
     *
     * <p>A {@code SecurityException} is also thrown if any of the elements of
     * the input {@code array} is a {@link java.lang.reflect.Constructor}
     * object for the class {@code java.lang.Class} and {@code flag} is true.
     *
     * @param array the array of AccessibleObjects
     * @param flag  the new value for the {@code accessible} flag
     *              in each object
     * @throws InaccessibleObjectException if access cannot be enabled for all
     *         objects in the array
     * @throws SecurityException if the request is denied by the security manager
     *         or an element in the array is a constructor for {@code
     *         java.lang.Class}
     * @see SecurityManager#checkPermission
     * @see ReflectPermission
     * @revised 9
     * @spec JPMS
     */
    @CallerSensitive
    public static void setAccessible(AccessibleObject[] array, boolean flag) {
        checkPermission();
        if (flag) {
            Class<?> caller = Reflection.getCallerClass();
            array = array.clone();
            for (AccessibleObject ao : array) {
                ao.checkCanSetAccessible(caller);
            }
        }
        for (AccessibleObject ao : array) {
            ao.setAccessible0(flag);
        }
    }

    /**
     * Set the {@code accessible} flag for this reflected object to
     * the indicated boolean value.  A value of {@code true} indicates that
     * the reflected object should suppress checks for Java language access
     * control when it is used. A value of {@code false} indicates that
     * the reflected object should enforce checks for Java language access
     * control when it is used, with the variation noted in the class description.
     *
     * <p> This method may be used by a caller in class {@code C} to enable
     * access to a {@link Member member} of {@link Member#getDeclaringClass()
     * declaring class} {@code D} if any of the following hold: </p>
     *
     * <ul>
     *     <li> {@code C} and {@code D} are in the same module. </li>
     *
     *     <li> The member is {@code public} and {@code D} is {@code public} in
     *     a package that the module containing {@code D} {@link
     *     Module#isExported(String,Module) exports} to at least the module
     *     containing {@code C}. </li>
     *
     *     <li> The member is {@code protected} {@code static}, {@code D} is
     *     {@code public} in a package that the module containing {@code D}
     *     exports to at least the module containing {@code C}, and {@code C}
     *     is a subclass of {@code D}. </li>
     *
     *     <li> {@code D} is in a package that the module containing {@code D}
     *     {@link Module#isOpen(String,Module) opens} to at least the module
     *     containing {@code C}.
     *     All packages in unnamed and open modules are open to all modules and
     *     so this method always succeeds when {@code D} is in an unnamed or
     *     open module. </li>
     * </ul>
     *
     * <p> This method cannot be used to enable access to private members,
     * members with default (package) access, protected instance members, or
     * protected constructors when the declaring class is in a different module
     * to the caller and the package containing the declaring class is not open
     * to the caller's module. </p>
     *
     * <p> If there is a security manager, its
     * {@code checkPermission} method is first called with a
     * {@code ReflectPermission("suppressAccessChecks")} permission.
     *
     * @param flag the new value for the {@code accessible} flag
     * @throws InaccessibleObjectException if access cannot be enabled
     * @throws SecurityException if the request is denied by the security manager
     * @see #trySetAccessible
     * @see java.lang.invoke.MethodHandles#privateLookupIn
     * @revised 9
     * @spec JPMS
     */
    public void setAccessible(boolean flag) {
        AccessibleObject.checkPermission();
        setAccessible0(flag);
    }

    /**
     * Sets the accessible flag and returns the new value
     */
    boolean setAccessible0(boolean flag) {
        this.override = flag;
        return flag;
    }

    /**
     * Set the {@code accessible} flag for this reflected object to {@code true}.
     * This method sets the {@code accessible} flag as if by invocation of
     * {@link #setAccessible(boolean) setAccessible(true)} where the caller of
     * {@code setAccessible} is deemed to be the caller of
     * {@code trySetAccessible} and returns the new value for the
     * {@code accessible} flag. If access cannot be enabled i.e. the checks
     * for Java language access control cannot be suppressed, this method
     * returns {@code false} as opposed to {@link #setAccessible(boolean)}
     * that throws an {@code InaccessibleObjectException}.
     *
     * <p> This method is a no-op if the {@code accessible} flag for
     * this reflected object is {@code true}.
     *
     * <p>For example, a caller can invoke {@code trySetAccessible}
     * on a {@code Method} object for a private instance method
     * {@code p.T::privateMethod} to suppress the checks for Java language access
     * control when the {@code Method} is invoked.
     * If {@code p.T} class is in a different module to the caller and
     * package {@code p} is open to at least the caller's module,
     * the code below successfully sets the {@code accessible} flag
     * to {@code true}.
     *
     * <pre>
     * {@code
     *     p.T obj = ....;  // instance of p.T
     *     :
     *     Method m = p.T.class.getDeclaredMethod("privateMethod");
     *     if (m.trySetAccessible()) {
     *         m.invoke(obj);
     *     } else {
     *         // package p is not opened to the caller to access private member of T
     *         ...
     *     }
     * }</pre>
     *
     * <p> If there is a security manager, its {@code checkPermission} method
     * is first called with a {@code ReflectPermission("suppressAccessChecks")}
     * permission. </p>
     *
     * @return {@code true} if the {@code accessible} flag is set to {@code true};
     *         {@code false} if access cannot be enabled.
     * @throws SecurityException if the request is denied by the security manager
     *
     * @since 9
     * @spec JPMS
     * @see java.lang.invoke.MethodHandles#privateLookupIn
     */
    @CallerSensitive
    public final boolean trySetAccessible() {
        AccessibleObject.checkPermission();

        if (override == true) return true;

        // if it's not a Constructor, Method, Field then no access check
        if (!Member.class.isInstance(this)) {
            return setAccessible0(true);
        }

        // does not allow to suppress access check for Class's constructor
        Class<?> declaringClass = ((Member) this).getDeclaringClass();
        if (declaringClass == Class.class && this instanceof Constructor) {
            return false;
        }

        boolean canSet;   // true if access can be enabled
        if (true) {
            canSet = checkCanSetAccessible(Reflection.getCallerClass(),
                                           declaringClass,
                                           false);
        } else {
            canSet = true;
        }

        return canSet ? setAccessible0(true) : false;
    }


   /**
    * If the given AccessibleObject is a {@code Constructor}, {@code Method}
    * or {@code Field} then checks that its declaring class is in a package
    * that can be accessed by the given caller of setAccessible.
    */
    void checkCanSetAccessible(Class<?> caller) {
        // do nothing, needs to be overridden by Constructor, Method, Field
    }


    void checkCanSetAccessible(Class<?> caller, Class<?> declaringClass) {
        checkCanSetAccessible(caller, declaringClass, true);
    }

    private boolean checkCanSetAccessible(Class<?> caller,
                                          Class<?> declaringClass,
                                          boolean throwExceptionIfDenied) {
        Module callerModule = caller.getModule();
        Module declaringModule = declaringClass.getModule();

        if (callerModule == declaringModule) return true;
        if (callerModule == Object.class.getModule()) return true;
        if (!declaringModule.isNamed()) return true;

        // package is open to caller
        String pn = packageName(declaringClass);
        if (declaringModule.isOpen(pn, callerModule)) {
            dumpStackIfOpenedReflectively(declaringModule, pn, callerModule);
            return true;
        }

        // package is exported to caller
        boolean isExported = declaringModule.isExported(pn, callerModule);
        boolean isClassPublic = Modifier.isPublic(declaringClass.getModifiers());
        int modifiers;
        if (this instanceof Executable) {
            modifiers = ((Executable) this).getModifiers();
        } else {
            modifiers = ((Field) this).getModifiers();
        }
        if (isExported && isClassPublic) {

            // member is public
            if (Modifier.isPublic(modifiers)) {
                dumpStackIfExportedReflectively(declaringModule, pn, callerModule);
                return true;
            }

            // member is protected-static
            if (Modifier.isProtected(modifiers)
                && Modifier.isStatic(modifiers)
                && isSubclassOf(caller, declaringClass)) {
                dumpStackIfExportedReflectively(declaringModule, pn, callerModule);
                return true;
            }
        }

        if (throwExceptionIfDenied) {
            // not accessible
            String msg = "Unable to make ";
            if (this instanceof Field)
                msg += "field ";
            msg += this + " accessible: " + declaringModule + " does not \"";
            if (isClassPublic && Modifier.isPublic(modifiers))
                msg += "exports";
            else
                msg += "opens";
            msg += " " + pn + "\" to " + callerModule;
            InaccessibleObjectException e = new InaccessibleObjectException(msg);
            if (Reflection.printStackTraceWhenAccessFails()) {
                e.printStackTrace(System.err);
            }
            throw e;
        }
        return false;
    }

    private boolean isSubclassOf(Class<?> queryClass, Class<?> ofClass) {
        while (queryClass != null) {
            if (queryClass == ofClass) {
                return true;
            }
            queryClass = queryClass.getSuperclass();
        }
        return false;
    }

    private void dumpStackIfOpenedReflectively(Module module,
                                               String pn,
                                               Module other) {
        dumpStackIfExposedReflectively(module, pn, other, true);
    }

    private void dumpStackIfExportedReflectively(Module module,
                                                 String pn,
                                                 Module other) {
        dumpStackIfExposedReflectively(module, pn, other, false);
    }

    private void dumpStackIfExposedReflectively(Module module,
                                                String pn,
                                                Module other,
                                                boolean open)
    {
        if (Reflection.printStackTraceWhenAccessSucceeds()
                && !module.isStaticallyExportedOrOpen(pn, other, open))
        {
            String msg = other + " allowed to invoke setAccessible on ";
            if (this instanceof Field)
                msg += "field ";
            msg += this;
            new Exception(msg) {
                private static final long serialVersionUID = 42L;
                public String toString() {
                    return "WARNING: " + getMessage();
                }
            }.printStackTrace(System.err);
        }
    }

    /**
     * Returns the package name of the given class.
     */
    private static String packageName(Class<?> c) {
        while (c.isArray()) {
            c = c.getComponentType();
        }
        String pn = c.getPackageName();
        return (pn != null) ? pn : "";
    }

    /**
     * Get the value of the {@code accessible} flag for this reflected object.
     *
     * @return the value of the object's {@code accessible} flag
     *
     * @deprecated
     * This method is deprecated because its name hints that it checks
     * if the reflected object is accessible when it actually indicates
     * if the checks for Java language access control are suppressed.
     * This method may return {@code false} on a reflected object that is
     * accessible to the caller. To test if this reflected object is accessible,
     * it should use {@link #canAccess(Object)}.
     *
     * @revised 9
     */
    @Deprecated(since="9")
    public boolean isAccessible() {
        return override;
    }

    /**
     * Test if the caller can access this reflected object. Specifically,
     * if this reflected object corresponds to an instance method or field,
     * this method tests if the caller can access the given {@code obj} with
     * the reflected object.  The given {@code obj} argument must be an
     * instance object of the {@link Member#getDeclaringClass() declaring class}
     * on which this method checks if the caller can reflectively access.
     *
     * <p> This method returns {@code true} if the {@code accessible} flag
     * is set to {@code true} i.e. the checks for Java language access control
     * are suppressed, or if the caller can access the member as
     * specified in <cite>The Java&trade; Language Specification</cite>,
     * with the variation noted in the class description. </p>
     *
     *
     * @param obj an instance object of the declaring class of this reflected
     *            object if it is an instance method or field
     *
     * @return {@code true} if the caller can access this reflected object.
     *
     * @throws IllegalArgumentException
     *         <ul>
     *         <li> if this reflected object is a static member or constructor and
     *              the given {@code obj} is non-{@code null}, or </li>
     *         <li> if this reflected object is an instance method or field
     *              and the given {@code obj} is {@code null} or of type
     *              that is not a subclass of the {@link Member#getDeclaringClass()
     *              declaring class} of the member.</li>
     *         </ul>
     *
     * @since 9
     * @spec JPMS
     *
     * @see #trySetAccessible
     * @see #setAccessible(boolean)
     */
    @CallerSensitive
    public final boolean canAccess(Object obj) {
        if (!Member.class.isInstance(this)) {
            return override;
        }

        Class<?> declaringClass = ((Member) this).getDeclaringClass();
        int modifiers = ((Member) this).getModifiers();
        if (!Modifier.isStatic(modifiers) &&
                (this instanceof Method || this instanceof Field)) {
            if (obj == null) {
                throw new IllegalArgumentException("null object for "
                    + this.toString());
            }
            // if this object is an instance member, the given object
            // must be a subclass of the declaring class of this reflected object
            if (!declaringClass.isAssignableFrom(obj.getClass())) {
                throw new IllegalArgumentException("object is not an instance of "
                    + declaringClass.getName());
            }
        } else if (obj != null) {
            throw new IllegalArgumentException("non-null object for "
                    + this.toString());
        }

        // access check is suppressed
        if (override) return true;

        Class<?> caller = Reflection.getCallerClass();
        Class<?> targetClass;
        if (this instanceof Constructor) {
            targetClass = declaringClass;
        } else {
            targetClass = Modifier.isStatic(modifiers) ? null : obj.getClass();
        }
        return Reflection.verifyMemberAccess(caller,
                                             declaringClass,
                                             targetClass,
                                             modifiers);
    }

    /**
     * Constructor: only used by the Java Virtual Machine.
     */
    protected AccessibleObject() {}

    // Indicates whether language-level access checks are overridden
    // by this object. Initializes to "false". This field is used by
    // Field, Method, and Constructor.
    //
    // NOTE: for security purposes, this field must not be visible
    // outside this package.
    boolean override;

    // Reflection factory used by subclasses for creating field,
    // method, and constructor accessors. Note that this is called
    // very early in the bootstrapping process.
    static final ReflectionFactory reflectionFactory =
        AccessController.doPrivileged(
            new ReflectionFactory.GetReflectionFactoryAction());

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw new AssertionError("All subclasses should override this method");
    }

    /**
     * {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return AnnotatedElement.super.isAnnotationPresent(annotationClass);
    }

   /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        throw new AssertionError("All subclasses should override this method");
    }

    /**
     * @since 1.5
     */
    public Annotation[] getAnnotations() {
        return getDeclaredAnnotations();
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        // Only annotations on classes are inherited, for all other
        // objects getDeclaredAnnotation is the same as
        // getAnnotation.
        return getAnnotation(annotationClass);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
        // Only annotations on classes are inherited, for all other
        // objects getDeclaredAnnotationsByType is the same as
        // getAnnotationsByType.
        return getAnnotationsByType(annotationClass);
    }

    /**
     * @since 1.5
     */
    public Annotation[] getDeclaredAnnotations()  {
        throw new AssertionError("All subclasses should override this method");
    }


    // Shared access checking logic.

    // For non-public members or members in package-private classes,
    // it is necessary to perform somewhat expensive security checks.
    // If the security check succeeds for a given class, it will
    // always succeed (it is not affected by the granting or revoking
    // of permissions); we speed up the check in the common case by
    // remembering the last Class for which the check succeeded.
    //
    // The simple security check for Constructor is to see if
    // the caller has already been seen, verified, and cached.
    // (See also Class.newInstance(), which uses a similar method.)
    //
    // A more complicated security check cache is needed for Method and Field
    // The cache can be either null (empty cache), a 2-array of {caller,targetClass},
    // or a caller (with targetClass implicitly equal to memberClass).
    // In the 2-array case, the targetClass is always different from the memberClass.
    volatile Object securityCheckCache;

    final void checkAccess(Class<?> caller, Class<?> memberClass,
                           Class<?> targetClass, int modifiers)
        throws IllegalAccessException
    {
        if (caller == memberClass) {  // quick check
            return;             // ACCESS IS OK
        }
        Object cache = securityCheckCache;  // read volatile
        if (targetClass != null // instance member or constructor
            && Modifier.isProtected(modifiers)
            && targetClass != memberClass) {
            // Must match a 2-list of { caller, targetClass }.
            if (cache instanceof Class[]) {
                Class<?>[] cache2 = (Class<?>[]) cache;
                if (cache2[1] == targetClass &&
                    cache2[0] == caller) {
                    return;     // ACCESS IS OK
                }
                // (Test cache[1] first since range check for [1]
                // subsumes range check for [0].)
            }
        } else if (cache == caller) {
            // Non-protected case (or targetClass == memberClass or static member).
            return;             // ACCESS IS OK
        }

        // If no return, fall through to the slow path.
        slowCheckMemberAccess(caller, memberClass, targetClass, modifiers);
    }

    // Keep all this slow stuff out of line:
    void slowCheckMemberAccess(Class<?> caller, Class<?> memberClass,
                               Class<?> targetClass, int modifiers)
        throws IllegalAccessException
    {
        Reflection.ensureMemberAccess(caller, memberClass, targetClass, modifiers);

        // Success: Update the cache.
        Object cache = (targetClass != null
                        && Modifier.isProtected(modifiers)
                        && targetClass != memberClass)
                        ? new Class<?>[] { caller, targetClass }
                        : caller;

        // Note:  The two cache elements are not volatile,
        // but they are effectively final.  The Java memory model
        // guarantees that the initializing stores for the cache
        // elements will occur before the volatile write.
        securityCheckCache = cache;         // write volatile
    }
}
