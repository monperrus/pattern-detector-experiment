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
package spoon.support.visitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spoon.SpoonException;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtFormalTypeDeclarer;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeInformation;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.chain.CtConsumer;
import spoon.reflect.visitor.chain.ScanningMode;
import spoon.reflect.visitor.filter.SuperInheritanceHierarchyFunction;

/**
 * Helper class created from type X or reference to X.
 * It provides access to actual type arguments
 * of any super type of type X adapted to type X.<br>
 * Example:<br>
 * <pre>
 * //reference to `ArrayList` with actual type argument `Integer`
 * CtTypeReference arrayListRef = ... //ArrayList&lt;Integer&gt;
 * //type java.util.List with type parameter `E`
 * CtType list = ... //List&lt;E&gt;
 * //adapting of type parameter `E` to scope of arrayListRef
 * CtTypeReference typeParamE_adaptedTo_arrayListRef = new ClassTypingContext(arrayListRef).adaptType(list.getFormalCtTypeParameters().get(0))
 * //the value of `E` in scope of arrayListRef is `Integer`
 * assertEquals(Integer.class.getName(), typeParamE_adaptedTo_arrayListRef.getQualifiedName());
 * </pre>
 */
public class ClassTypingContext implements GenericTypeAdapter {
	/*
	 * super type hierarchy of the enclosing class
	 */
	private ClassTypingContext enclosingClassTypingContext;

	/*
	 * maps qualified name of the type to the actual type arguments of this type in `scope`
	 */
	private Map<String, List<CtTypeReference<?>>> typeToArguments = new HashMap<>();
	/**
	 * remember which super class was last visited.
	 * The next super class scanning will start here
	 */
	private CtTypeInformation lastResolvedSuperclass;
	/**
	 * the set of qualified names of all visited classes and interfaces, which assures that interfaces are visited only once
	 */
	private Set<String> visitedSet;

	/**
	 * @param typeReference {@link CtTypeReference} whose actual type arguments are used for resolving of input type parameters
	 */
	public ClassTypingContext(CtTypeReference<?> typeReference) {
		lastResolvedSuperclass = typeReference;
		CtTypeReference<?> enclosing = getEnclosingType(typeReference);
		if (enclosing != null) {
			enclosingClassTypingContext = createEnclosingHierarchy(enclosing);
		}
		typeToArguments.put(typeReference.getQualifiedName(), typeReference.getActualTypeArguments());
	}

	/**
	 * @param type {@link CtType} whose formal type parameters are transformed to {@link CtTypeReference}s,
	 * which plays role of actual type arguments, used for resolving of input type parameters
	 */
	public ClassTypingContext(CtType<?> type) {
		lastResolvedSuperclass = type;
		CtType<?> enclosing = getEnclosingType(type);
		if (enclosing != null) {
			enclosingClassTypingContext = createEnclosingHierarchy(enclosing);
		}
		typeToArguments.put(type.getQualifiedName(), getTypeReferences(type.getFormalCtTypeParameters()));
	}

	@Override
	public CtTypeReference<?> adaptType(CtTypeInformation type) {
		if (type instanceof CtTypeReference<?>) {
			if (type instanceof CtTypeParameterReference) {
				return adaptTypeParameter(((CtTypeParameterReference) type).getDeclaration());
			}
			return (CtTypeReference<?>) type;
		}
		if (type instanceof CtTypeParameter) {
			return adaptTypeParameter((CtTypeParameter) type);
		}
		return ((CtType<?>) type).getReference();
	}

	/**
	 * detects if `superTypeRef` is a super type of the type or type reference,
	 * which was send to constructor of this instance.
	 * It takes into account the actual type arguments of this type and `superTypeRef`
	 *
	 * So for example:<br>
	 * <pre>
	 * CtTypeReference listInteger = ...//List&lt;Integer&gt;
	 * CtTypeReference listString = ...//List&lt;Integer&gt;
	 * assertFalse(new ClassTypingContext(listInteger).isSubtypeOf(listString))
	 * CtTypeReference listExtendsNumber = ...//List&lt;? extends Number&gt;
	 * assertTrue(new ClassTypingContext(listInteger).isSubtypeOf(listExtendsNumber))
	 * </pre>
	 * @param superTypeRef the reference
	 * @return true if this type (including actual type arguments) is a sub type of superTypeRef
	 */
	public boolean isSubtypeOf(CtTypeReference<?> superTypeRef) {
		List<CtTypeReference<?>> adaptedArgs = resolveActualTypeArgumentsOf(superTypeRef);
		if (adaptedArgs == null) {
			//the superTypeRef was not found in super type hierarchy
			return false;
		}
		if (isSubTypeByActualTypeArguments(superTypeRef, adaptedArgs) == false) {
			return false;
		}
		CtTypeReference<?> enclosingTypeRef = getEnclosingType(superTypeRef);
		if (enclosingTypeRef != null) {
			if (enclosingClassTypingContext == null) {
				return false;
			}
			return enclosingClassTypingContext.isSubtypeOf(enclosingTypeRef);
		}
		return true;
	}

	/**
	 * resolve actual type argument values of the provided type reference
	 * @param typeRef the reference to the type
	 * 	whose actual type argument values has to be resolved in scope of `scope` type
	 * @return actual type arguments of `typeRef` in scope of `scope` element or null if typeRef is not a super type of `scope`
	 */
	public List<CtTypeReference<?>> resolveActualTypeArgumentsOf(CtTypeReference<?> typeRef) {
		final String typeQualifiedName = typeRef.getQualifiedName();
		List<CtTypeReference<?>> args = typeToArguments.get(typeQualifiedName);
		if (args != null) {
			//the actual type arguments of `type` are already resolved
			return args;
		}
		//resolve hierarchy of enclosing class first.
		CtTypeReference<?> enclosingTypeRef = getEnclosingType(typeRef);
		if (enclosingTypeRef != null) {
			if (enclosingClassTypingContext == null) {
				return null;
			}
			//`type` is inner class. Resolve it's enclosing class arguments first
			if (enclosingClassTypingContext.resolveActualTypeArgumentsOf(enclosingTypeRef) == null) {
				return null;
			}
		}
		/*
		 * the `type` is either top level, static or resolved inner class.
		 * So it has no parent actual type arguments or they are resolved now
		 */
		/*
		 * detect where to start/continue with resolving of super classes and super interfaces
		 * to found actual type arguments of input `type`
		 */
		if (lastResolvedSuperclass == null) {
			/*
			 * whole super inheritance hierarchy was already resolved for this level.
			 * It means that `type` is not a super type of `scope` on the level `level`
			 */
			return null;
		}
		final HierarchyListener listener = new HierarchyListener(getVisitedSet());
		/*
		 * visit super inheritance class hierarchy of lastResolve type of level of `type` to found it's actual type arguments.
		 */
		((CtElement) lastResolvedSuperclass).map(new SuperInheritanceHierarchyFunction()
				.includingSelf(false)
				.returnTypeReferences(true)
				.setListener(listener))
		.forEach(new CtConsumer<CtTypeReference<?>>() {
			@Override
			public void accept(CtTypeReference<?> typeRef) {
				/*
				 * typeRef is a reference from sub type to super type.
				 * It contains actual type arguments in scope of sub type,
				 * which are going to be substituted as arguments to formal type parameters of super type
				 */
				String superTypeQualifiedName = typeRef.getQualifiedName();
				List<CtTypeReference<?>> superTypeActualTypeArgumentsResolvedFromSubType = resolveTypeParameters(typeRef.getActualTypeArguments());
				//Remember actual type arguments of `type`
				typeToArguments.put(superTypeQualifiedName, superTypeActualTypeArgumentsResolvedFromSubType);
				if (typeQualifiedName.equals(superTypeQualifiedName)) {
					/*
					 * we have found actual type arguments of input `type`
					 * We can finish. But only after all interfaces of last visited class are processed too
					 */
					listener.foundArguments = superTypeActualTypeArgumentsResolvedFromSubType;
				}
			}
		});
		return listener.foundArguments;
	}

	@Override
	public ClassTypingContext getEnclosingGenericTypeAdapter() {
		return enclosingClassTypingContext;
	}

	/**
	 * might be used to create custom chain of super type hierarchies
	 */
	protected ClassTypingContext createEnclosingHierarchy(CtType<?> enclosingType) {
		return new ClassTypingContext(enclosingType);
	}
	/**
	 * might be used to create custom chain of super type hierarchies
	 */
	protected ClassTypingContext createEnclosingHierarchy(CtTypeReference<?> enclosingTypeRef) {
		return new ClassTypingContext(enclosingTypeRef);
	}

	static List<CtTypeReference<?>> getTypeReferences(List<? extends CtType<?>> types) {
		List<CtTypeReference<?>> refs = new ArrayList<>(types.size());
		for (CtType<?> type : types) {
			refs.add(type.getReference());
		}
		return refs;
	}

	/**
	 * @param type the potential inner class, whose enclosing type should be returned
	 * @return enclosing type of a `type` is an inner type or null if `type` is explicitly or implicitly static or top level type
	 */
	private CtType<?> getEnclosingType(CtType<?> type) {
		if (type.hasModifier(ModifierKind.STATIC)) {
			return null;
		}
		CtType<?> declType = type.getDeclaringType();
		if (declType == null) {
			return null;
		}
		if (declType.isInterface()) {
			//nested types of interfaces are static
			return null;
		}
		return declType;
	}

	/**
	 * @param typeRef the potential inner class, whose enclosing type should be returned
	 * @return enclosing type of a `type` is an inner type or null if `type` is explicitly or implicitly static or top level type
	 */
	private CtTypeReference<?> getEnclosingType(CtTypeReference<?> typeRef) {
		CtType<?> type = typeRef.getTypeDeclaration();
		if (type.hasModifier(ModifierKind.STATIC)) {
			return null;
		}
		CtType<?> declType = type.getDeclaringType();
		if (declType == null) {
			return null;
		}
		if (declType.isInterface()) {
			//nested types of interfaces are static
			return null;
		}
		return typeRef.getDeclaringType();
	}

	/**
	 * adapts `typeParam` to the {@link CtTypeReference}
	 * of scope of this {@link ClassTypingContext}
	 * In can be {@link CtTypeParameterReference} again - depending actual type arguments of this {@link ClassTypingContext}.
	 *
	 * @param typeParam to be resolved {@link CtTypeParameter}
	 * @return {@link CtTypeReference} or {@link CtTypeParameterReference} adapted to scope of this {@link ClassTypingContext}
	 *  or null if `typeParam` cannot be adapted to target `scope`
	 */
	private CtTypeReference<?> adaptTypeParameter(CtTypeParameter typeParam) {
		CtFormalTypeDeclarer declarer = typeParam.getTypeParameterDeclarer();
		if ((declarer instanceof CtType<?>) == false) {
			return null;
		}
		//get the actual type argument values for the declarer of `typeParam`
		List<CtTypeReference<?>> actualTypeArguments = resolveActualTypeArgumentsOf(((CtType<?>) declarer).getReference());
		if (actualTypeArguments == null) {
			if (enclosingClassTypingContext != null) {
				//try to adapt parameter using enclosing class typing context
				return enclosingClassTypingContext.adaptType(typeParam);
			}
			return null;
		}
		return getValue(actualTypeArguments, typeParam, declarer);
	}

	/**
	 * Create visitedSet lazily
	 */
	private Set<String> getVisitedSet() {
		if (visitedSet == null) {
			visitedSet = new HashSet<>();
		}
		return visitedSet;
	}

	/**
	 * the listener which assures that
	 * - each interface of super inheritance hierarchy is visited only once
	 * - the scanning of super inheritance hierarchy early stops when we have found
	 */
	private class HierarchyListener extends SuperInheritanceHierarchyFunction.DistinctTypeListener {
		List<CtTypeReference<?>> foundArguments;
		HierarchyListener(Set<String> visitedSet) {
			super(visitedSet);
		}
		@Override
		public ScanningMode enter(CtElement element) {
			ScanningMode mode = super.enter(element);
			if (mode == ScanningMode.SKIP_ALL) {
				//this interface was already visited. Do not visit it again
				return mode;
			}
			CtType<?> type = ((CtTypeReference<?>) element).getTypeDeclaration();
			if (type instanceof CtClass) {
				if (foundArguments != null) {
					//we have found result then we can finish before entering super class. All interfaces of found type should be still visited
					//skip before super class (and it's interfaces) of found type is visited
					return ScanningMode.SKIP_ALL;
				}
				/*
				 * we are visiting class (not interface)
				 * Remember that, so we can continue at this place if needed.
				 * If we enter class, then this listener assures that that class and all it's not yet visited interfaces are visited
				 */
				lastResolvedSuperclass = type;
			}
			//this type was not visited yet. Visit it normally
			return ScanningMode.NORMAL;
		}
	}

	/**
	 * resolve typeRefs declared in scope of declarer using actual type arguments registered in typeScopeToActualTypeArguments
	 * @param typeRefs to be resolved type references
	 * @return resolved type references - one for each `typeRefs`
	 * @throws SpoonException if they cannot be resolved. It should not normally happen. If it happens then spoon AST model is probably not consistent.
	 */
	private List<CtTypeReference<?>> resolveTypeParameters(List<CtTypeReference<?>> typeRefs) {
		List<CtTypeReference<?>> result = new ArrayList<>(typeRefs.size());
		for (CtTypeReference<?> typeRef : typeRefs) {
			if (typeRef instanceof CtTypeParameterReference) {
				CtTypeParameterReference typeParamRef = (CtTypeParameterReference) typeRef;
				CtTypeParameter typeParam = typeParamRef.getDeclaration();
				CtFormalTypeDeclarer declarer = typeParam.getTypeParameterDeclarer();
				if ((declarer instanceof CtType<?>) == false) {
					throw new SpoonException("Cannot adapt type parameters of non type scope");
				}
				CtType<?> typeDeclarer = (CtType<?>) declarer;
				List<CtTypeReference<?>> actualTypeArguments = typeToArguments.get(typeDeclarer.getQualifiedName());
				if (actualTypeArguments == null) {
					/*
					 * the actualTypeArguments of this declarer cannot be resolved.
					 * There is probably a model inconsistency
					 */
					throw new SpoonException("Cannot resolve " + (result.size() + 1) + ") type parameter <" + typeParamRef.getSimpleName() + ">  of declarer " + declarer);
				}
				if (actualTypeArguments.size() != typeDeclarer.getFormalCtTypeParameters().size()) {
					if (actualTypeArguments.isEmpty() == false) {
						throw new SpoonException("Unexpected actual type arguments " + actualTypeArguments + " on " + typeDeclarer);
					}
					/*
					 * the scope type was delivered as type reference without appropriate type arguments.
					 * Use references to formal type parameters
					 */
					actualTypeArguments = getTypeReferences(typeDeclarer.getFormalCtTypeParameters());
					typeToArguments.put(typeDeclarer.getQualifiedName(), actualTypeArguments);
				}
				typeRef = getValue(actualTypeArguments, typeParam, declarer);
			}
			result.add(typeRef);
		}
		return result;
	}

	private static CtTypeReference<?> getValue(List<CtTypeReference<?>> arguments, CtTypeParameter typeParam, CtFormalTypeDeclarer declarer) {
		if (declarer.getFormalCtTypeParameters().size() != arguments.size()) {
			throw new SpoonException("Unexpected count of actual type arguments");
		}
		int typeParamIdx = declarer.getFormalCtTypeParameters().indexOf(typeParam);
		return arguments.get(typeParamIdx);
	}

	/**
	 * Substitutes the typeParameter by its value
	 * @param typeParameter - to be substituted parameter
	 * @param declarer - the declarer of typeParameter
	 * @param values - the list of parameter values
	 * @return the value from values on the same position as typeParameter in declarer.getFormalCtTypeParameters()
	 */
	static <T, U extends List<T>> T substituteBy(CtTypeParameter typeParameter, CtFormalTypeDeclarer declarer, U values) {
		List<CtTypeParameter> typeParams = declarer.getFormalCtTypeParameters();
		int position = typeParams.indexOf(typeParameter);
		if (position == -1) {
			throw new SpoonException("Type parameter <" + typeParameter.getSimpleName() + " not found in scope " + declarer.getShortRepresentation());
		}
		if (values.size() != typeParams.size()) {
			throw new SpoonException("Unexpected count of parameters");
		}
		return values.get(position);
	}

	/**
	 * @return true if actualType arguments of `scope` are fitting as a subtype of superTypeArgs
	 */
	private boolean isSubTypeByActualTypeArguments(CtTypeReference<?> superTypeRef, List<CtTypeReference<?>> expectedSuperTypeArguments) {
		List<CtTypeReference<?>> superTypeArgs = superTypeRef.getActualTypeArguments();
		if (superTypeArgs.isEmpty()) {
			//the raw type or not a generic type. Arguments are ignored in sub type detection
			return true;
		}
		List<CtTypeReference<?>> subTypeArgs = expectedSuperTypeArguments;
		if (subTypeArgs.isEmpty()) {
			//the raw type or not a generic type
			return true;
		}
		if (subTypeArgs.size() != superTypeArgs.size()) {
			//the number of arguments is not same - it should not happen ...
			return false;
		}
		for (int i = 0; i < subTypeArgs.size(); i++) {
			CtTypeReference<?> superArg = superTypeArgs.get(i);
			CtTypeReference<?> subArg = subTypeArgs.get(i);
			if (isSubTypeArg(subArg, superArg) == false) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return true if actualType argument `subArg` is fitting as a subtype of actual type argument `superArg`
	 */
	private boolean isSubTypeArg(CtTypeReference<?> subArg, CtTypeReference<?> superArg) {
		if (superArg instanceof CtWildcardReference) {
			CtWildcardReference wr = (CtWildcardReference) superArg;
			CtTypeReference<?> superBound = wr.getBoundingType();
			if (superBound == null) {
				//everything extends from object, nothing is super of Object
				return wr.isUpper();
			}
			if (subArg instanceof CtWildcardReference) {
				CtWildcardReference subWr = (CtWildcardReference) subArg;
				CtTypeReference<?> subBound = subWr.getBoundingType();
				if (subBound == null) {
					//nothing is super of object
					return false;
				}
				if (wr.isUpper() != subWr.isUpper()) {
					//one is "super" second is "extends"
					return false;
				}
				if (wr.isUpper()) {
					//both are extends
					return subBound.isSubtypeOf(superBound);
				}
				//both are super
				return superBound.isSubtypeOf(subBound);
			}
			if (wr.isUpper()) {
				return subArg.isSubtypeOf(superBound);
			} else {
				return superBound.isSubtypeOf(subArg);
			}
		}
		//superArg is not a wildcard. Only same type is matching
		return subArg.equals(superArg);
	}
}
