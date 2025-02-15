/**
 * Copyright (C) 2006-2017 INRIA and contributors
 * Spoon - http://spoon.gforge.inria.fr/
 *
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */
package spoon.reflect.code;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtActualTypeContainer;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.DerivedProperty;
import spoon.reflect.annotations.PropertyGetter;
import spoon.reflect.annotations.PropertySetter;

import java.util.List;

import static spoon.reflect.path.CtRole.EXPRESSION;
import static spoon.reflect.path.CtRole.TYPE_PARAMETER;

/**
 * This code element represents the creation of a anonymous class.
 *
* Example:
 * <pre>
 *    // an anonymous class creation
 *    Runnable r = new Runnable() {
 *     	&#64;Override
 *     	public void run() {
 *     	  System.out.println("foo");
 *     	}
 *    };
 * </pre>
 * @param <T>
 * 		created type
 */
public interface CtNewClass<T> extends CtConstructorCall<T> {
	/**
	 * Delegate to the executable reference of the new class.
	 *
	 * @see CtExecutableReference#getActualTypeArguments()
	 */
	@Override
	@DerivedProperty
	@PropertyGetter(role = TYPE_PARAMETER)
	List<CtTypeReference<?>> getActualTypeArguments();

	/**
	 * Delegate to the executable reference of the new class.
	 *
	 * @see CtExecutableReference#getActualTypeArguments()
	 */
	@Override
	@PropertySetter(role = TYPE_PARAMETER)
	<T extends CtActualTypeContainer> T setActualTypeArguments(List<? extends CtTypeReference<?>> actualTypeArguments);

	/**
	 * Delegate to the executable reference of the new class.
	 *
	 * @see CtExecutableReference#getActualTypeArguments()
	 */
	@Override
	@PropertySetter(role = TYPE_PARAMETER)
	<T extends CtActualTypeContainer> T addActualTypeArgument(CtTypeReference<?> actualTypeArgument);

	/**
	 * Gets the created class.
	 */
	@PropertyGetter(role = EXPRESSION)
	CtClass<?> getAnonymousClass();

	/**
	 * Sets the created class.
	 */
	@PropertySetter(role = EXPRESSION)
	<N extends CtNewClass> N setAnonymousClass(CtClass<?> anonymousClass);

	@Override
	CtNewClass<T> clone();
}
