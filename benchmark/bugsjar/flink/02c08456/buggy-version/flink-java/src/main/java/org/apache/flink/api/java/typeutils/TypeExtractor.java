/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.java.typeutils;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.CoGroupFunction;
import org.apache.flink.api.common.functions.CrossFunction;
import org.apache.flink.api.common.functions.FlatJoinFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.api.common.functions.InvalidTypesException;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.MapPartitionFunction;
import org.apache.flink.api.common.functions.util.FunctionUtils;
import org.apache.flink.api.common.io.InputFormat;
import org.apache.flink.api.common.typeinfo.BasicArrayTypeInfo;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.PrimitiveArrayTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.types.Value;
import org.apache.flink.util.Collector;
import org.apache.hadoop.io.Writable;

public class TypeExtractor {

	// We need this to detect recursive types and not get caught
	// in an endless recursion
	private Set<Class<?>> alreadySeen;

	private TypeExtractor() {
		alreadySeen = new HashSet<Class<?>>();
	}

	// --------------------------------------------------------------------------------------------
	//  Function specific methods
	// --------------------------------------------------------------------------------------------
	
	public static <IN, OUT> TypeInformation<OUT> getMapReturnTypes(MapFunction<IN, OUT> mapInterface, TypeInformation<IN> inType) {
		return getUnaryOperatorReturnType((Function) mapInterface, MapFunction.class, false, false, inType);
	}
	
	public static <IN, OUT> TypeInformation<OUT> getFlatMapReturnTypes(FlatMapFunction<IN, OUT> flatMapInterface, TypeInformation<IN> inType) {
		return getUnaryOperatorReturnType((Function) flatMapInterface, FlatMapFunction.class, false, true, inType);
	}
	
	public static <IN, OUT> TypeInformation<OUT> getMapPartitionReturnTypes(MapPartitionFunction<IN, OUT> mapPartitionInterface, TypeInformation<IN> inType) {
		return getUnaryOperatorReturnType((Function) mapPartitionInterface, MapPartitionFunction.class, true, true, inType);
	}
	
	public static <IN, OUT> TypeInformation<OUT> getGroupReduceReturnTypes(GroupReduceFunction<IN, OUT> groupReduceInterface,
			TypeInformation<IN> inType) {
		return getUnaryOperatorReturnType((Function) groupReduceInterface, GroupReduceFunction.class, true, true, inType);
	}
	
	public static <IN1, IN2, OUT> TypeInformation<OUT> getFlatJoinReturnTypes(FlatJoinFunction<IN1, IN2, OUT> joinInterface,
			TypeInformation<IN1> in1Type, TypeInformation<IN2> in2Type) {
		return getBinaryOperatorReturnType((Function) joinInterface, FlatJoinFunction.class, false, true, in1Type, in2Type);
	}
	
	public static <IN1, IN2, OUT> TypeInformation<OUT> getJoinReturnTypes(JoinFunction<IN1, IN2, OUT> joinInterface,
			TypeInformation<IN1> in1Type, TypeInformation<IN2> in2Type) {
		return getBinaryOperatorReturnType((Function) joinInterface, JoinFunction.class, false, false, in1Type, in2Type);
	}
	
	public static <IN1, IN2, OUT> TypeInformation<OUT> getCoGroupReturnTypes(CoGroupFunction<IN1, IN2, OUT> coGroupInterface,
			TypeInformation<IN1> in1Type, TypeInformation<IN2> in2Type) {
		return getBinaryOperatorReturnType((Function) coGroupInterface, CoGroupFunction.class, true, true, in1Type, in2Type);
	}
	
	public static <IN1, IN2, OUT> TypeInformation<OUT> getCrossReturnTypes(CrossFunction<IN1, IN2, OUT> crossInterface,
			TypeInformation<IN1> in1Type, TypeInformation<IN2> in2Type) {
		return getBinaryOperatorReturnType((Function) crossInterface, CrossFunction.class, false, false, in1Type, in2Type);
	}
	
	public static <IN, OUT> TypeInformation<OUT> getKeySelectorTypes(KeySelector<IN, OUT> selectorInterface, TypeInformation<IN> inType) {
		return getUnaryOperatorReturnType((Function) selectorInterface, KeySelector.class, false, false, inType);
	}
	
	@SuppressWarnings("unchecked")
	public static <IN> TypeInformation<IN> getInputFormatTypes(InputFormat<IN, ?> inputFormatInterface) {
		if(inputFormatInterface instanceof ResultTypeQueryable) {
			return ((ResultTypeQueryable<IN>) inputFormatInterface).getProducedType();
		}
		return new TypeExtractor().privateCreateTypeInfo(InputFormat.class, inputFormatInterface.getClass(), 0, null, null);
	}
	
	@SuppressWarnings("unchecked")
	private static <IN, OUT> TypeInformation<OUT> getUnaryOperatorReturnType(Function function, Class<?> baseClass, boolean hasIterable, boolean hasCollector, TypeInformation<IN> inType) {
		final Method m = FunctionUtils.checkAndExtractLambdaMethod(function);
		if (m != null) {
			// check for lambda type erasure
			validateLambdaGenericParameters(m);
			
			// parameters must be accessed from behind, since JVM can add additional parameters e.g. when using local variables inside lambda function
			final int paramLen = m.getGenericParameterTypes().length - 1;
			final Type input = (hasCollector)? m.getGenericParameterTypes()[paramLen - 1] : m.getGenericParameterTypes()[paramLen];
			validateInputType((hasIterable)?removeGenericWrapper(input) : input, inType);
			if(function instanceof ResultTypeQueryable) {
				return ((ResultTypeQueryable<OUT>) function).getProducedType();
			}
			return new TypeExtractor().privateCreateTypeInfo((hasCollector)? removeGenericWrapper(m.getGenericParameterTypes()[paramLen]) : m.getGenericReturnType(), inType, null);
		}
		else {
			validateInputType(baseClass, function.getClass(), 0, inType);
			if(function instanceof ResultTypeQueryable) {
				return ((ResultTypeQueryable<OUT>) function).getProducedType();
			}
			return new TypeExtractor().privateCreateTypeInfo(baseClass, function.getClass(), 1, inType, null);
		}
	}
	
	@SuppressWarnings("unchecked")
	private static <IN1, IN2, OUT> TypeInformation<OUT> getBinaryOperatorReturnType(Function function, Class<?> baseClass, boolean hasIterables, boolean hasCollector, TypeInformation<IN1> in1Type, TypeInformation<IN2> in2Type) {
		final Method m = FunctionUtils.checkAndExtractLambdaMethod(function);
		if (m != null) {
			// check for lambda type erasure
			validateLambdaGenericParameters(m);
			
			// parameters must be accessed from behind, since JVM can add additional parameters e.g. when using local variables inside lambda function
			final int paramLen = m.getGenericParameterTypes().length - 1;
			final Type input1 = (hasCollector)? m.getGenericParameterTypes()[paramLen - 2] : m.getGenericParameterTypes()[paramLen - 1];
			final Type input2 = (hasCollector)? m.getGenericParameterTypes()[paramLen - 1] : m.getGenericParameterTypes()[paramLen];
			validateInputType((hasIterables)? removeGenericWrapper(input1) : input1, in1Type);
			validateInputType((hasIterables)? removeGenericWrapper(input2) : input2, in2Type);
			if(function instanceof ResultTypeQueryable) {
				return ((ResultTypeQueryable<OUT>) function).getProducedType();
			}
			return new TypeExtractor().privateCreateTypeInfo((hasCollector)? removeGenericWrapper(m.getGenericParameterTypes()[paramLen]) : m.getGenericReturnType(), in1Type, in2Type);
		}
		else {
			validateInputType(baseClass, function.getClass(), 0, in1Type);
			validateInputType(baseClass, function.getClass(), 1, in2Type);
			if(function instanceof ResultTypeQueryable) {
				return ((ResultTypeQueryable<OUT>) function).getProducedType();
			}
			return new TypeExtractor().privateCreateTypeInfo(baseClass, function.getClass(), 2, in1Type, in2Type);
		}
	}
	
	// --------------------------------------------------------------------------------------------
	//  Create type information
	// --------------------------------------------------------------------------------------------
	
	public static TypeInformation<?> createTypeInfo(Type t) {
		return new TypeExtractor().privateCreateTypeInfo(t);
	}
	
	public static <IN1, IN2, OUT> TypeInformation<OUT> createTypeInfo(Class<?> baseClass, Class<?> clazz, int returnParamPos,
			TypeInformation<IN1> in1Type, TypeInformation<IN2> in2Type) {
		return new TypeExtractor().privateCreateTypeInfo(baseClass, clazz, returnParamPos, in1Type, in2Type);
	}
	
	// ----------------------------------- private methods ----------------------------------------
	
	private TypeInformation<?> privateCreateTypeInfo(Type t) {
		ArrayList<Type> typeHierarchy = new ArrayList<Type>();
		typeHierarchy.add(t);
		return createTypeInfoWithTypeHierarchy(typeHierarchy, t, null, null);
	}
	
	// for (Rich)Functions
	@SuppressWarnings("unchecked")
	private <IN1, IN2, OUT> TypeInformation<OUT> privateCreateTypeInfo(Class<?> baseClass, Class<?> clazz, int returnParamPos,
			TypeInformation<IN1> in1Type, TypeInformation<IN2> in2Type) {
		ArrayList<Type> typeHierarchy = new ArrayList<Type>();
		Type returnType = getParameterType(baseClass, typeHierarchy, clazz, returnParamPos);
		
		TypeInformation<OUT> typeInfo = null;
		
		// return type is a variable -> try to get the type info from the input directly
		if (returnType instanceof TypeVariable<?>) {
			typeInfo = (TypeInformation<OUT>) createTypeInfoFromInputs((TypeVariable<?>) returnType, typeHierarchy, in1Type, in2Type);
			
			if (typeInfo != null) {
				return typeInfo;
			}
		}
		
		// get info from hierarchy
		return (TypeInformation<OUT>) createTypeInfoWithTypeHierarchy(typeHierarchy, returnType, in1Type, in2Type);
	}
	
	// for LambdaFunctions
	@SuppressWarnings("unchecked")
	private <IN1, IN2, OUT> TypeInformation<OUT> privateCreateTypeInfo(Type returnType, TypeInformation<IN1> in1Type, TypeInformation<IN2> in2Type) {
		ArrayList<Type> typeHierarchy = new ArrayList<Type>();
		
		// get info from hierarchy
		return (TypeInformation<OUT>) createTypeInfoWithTypeHierarchy(typeHierarchy, returnType, in1Type, in2Type);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <IN1, IN2, OUT> TypeInformation<OUT> createTypeInfoWithTypeHierarchy(ArrayList<Type> typeHierarchy, Type t,
			TypeInformation<IN1> in1Type, TypeInformation<IN2> in2Type) {
		
		// check if type is a subclass of tuple
		if ((t instanceof Class<?> && Tuple.class.isAssignableFrom((Class<?>) t))
				|| (t instanceof ParameterizedType && Tuple.class.isAssignableFrom((Class<?>) ((ParameterizedType) t).getRawType()))) {
			
			Type curT = t;
			
			// do not allow usage of Tuple as type
			if (curT instanceof Class<?> && ((Class<?>) curT).equals(Tuple.class)) {
				throw new InvalidTypesException(
						"Usage of class Tuple as a type is not allowed. Use a concrete subclass (e.g. Tuple1, Tuple2, etc.) instead.");
			}
			
			// go up the hierarchy until we reach immediate child of Tuple (with or without generics)
			// collect the types while moving up for a later top-down 
			while (!(curT instanceof ParameterizedType && ((Class<?>) ((ParameterizedType) curT).getRawType()).getSuperclass().equals(
					Tuple.class))
					&& !(curT instanceof Class<?> && ((Class<?>) curT).getSuperclass().equals(Tuple.class))) {
				typeHierarchy.add(curT);
				
				// parameterized type
				if (curT instanceof ParameterizedType) {
					curT = ((Class<?>) ((ParameterizedType) curT).getRawType()).getGenericSuperclass();
				}
				// class
				else {
					curT = ((Class<?>) curT).getGenericSuperclass();
				}
			}
			
			// check if immediate child of Tuple has generics
			if (curT instanceof Class<?>) {
				throw new InvalidTypesException("Tuple needs to be parameterized by using generics.");
			}
			
			ParameterizedType tupleChild = (ParameterizedType) curT;
			
			Type[] subtypes = new Type[tupleChild.getActualTypeArguments().length];
			
			// materialize possible type variables
			for (int i = 0; i < subtypes.length; i++) {
				// materialize immediate TypeVariables
				if (tupleChild.getActualTypeArguments()[i] instanceof TypeVariable<?>) {
					subtypes[i] = materializeTypeVariable(typeHierarchy, (TypeVariable<?>) tupleChild.getActualTypeArguments()[i]);
				}
				// class or parameterized type
				else {
					subtypes[i] = tupleChild.getActualTypeArguments()[i];
				}
			}
			
			TypeInformation<?>[] tupleSubTypes = new TypeInformation<?>[subtypes.length];
			for (int i = 0; i < subtypes.length; i++) {
				// sub type could not be determined with materializing
				// try to derive the type info of the TypeVariable from the immediate base child input as a last attempt
				if (subtypes[i] instanceof TypeVariable<?>) {
					tupleSubTypes[i] = createTypeInfoFromInputs((TypeVariable<?>) subtypes[i], typeHierarchy, in1Type, in2Type);
					
					// variable could not be determined
					if (tupleSubTypes[i] == null) {
						throw new InvalidTypesException("Type of TypeVariable '" + ((TypeVariable<?>) subtypes[i]).getName() + "' in '"
								+ ((TypeVariable<?>) subtypes[i]).getGenericDeclaration()
								+ "' could not be determined. This is most likely a type erasure problem. "
								+ "The type extraction currently supports types with generic variables only in cases where "
								+ "all variables in the return type can be deduced from the input type(s).");
					}
				} else {
					tupleSubTypes[i] = createTypeInfoWithTypeHierarchy(new ArrayList<Type>(typeHierarchy), subtypes[i], in1Type, in2Type);
				}
			}
			
			// TODO: Check that type that extends Tuple does not have additional fields.
			// Right now, these fields are not be serialized by the TupleSerializer. 
			// We might want to add an ExtendedTupleSerializer for that. 
			
			if (t instanceof Class<?>) {
				return new TupleTypeInfo(((Class<? extends Tuple>) t), tupleSubTypes);
			} else if (t instanceof ParameterizedType) {
				return new TupleTypeInfo(((Class<? extends Tuple>) ((ParameterizedType) t).getRawType()), tupleSubTypes);
			}
		}
		// type depends on another type
		// e.g. class MyMapper<E> extends MapFunction<String, E>
		else if (t instanceof TypeVariable) {
			Type typeVar = materializeTypeVariable(typeHierarchy, (TypeVariable<?>) t);
			
			if (!(typeVar instanceof TypeVariable)) {
				return createTypeInfoWithTypeHierarchy(typeHierarchy, typeVar, in1Type, in2Type);
			}
			// try to derive the type info of the TypeVariable from the immediate base child input as a last attempt
			else {
				TypeInformation<OUT> typeInfo = (TypeInformation<OUT>) createTypeInfoFromInputs((TypeVariable<?>) t, typeHierarchy, in1Type, in2Type);
				if (typeInfo != null) {
					return typeInfo;
				} else {
					throw new InvalidTypesException("Type of TypeVariable '" + ((TypeVariable<?>) t).getName() + "' in '"
							+ ((TypeVariable<?>) t).getGenericDeclaration() + "' could not be determined. This is most likely a type erasure problem. "
							+ "The type extraction currently supports types with generic variables only in cases where "
							+ "all variables in the return type can be deduced from the input type(s).");
				}
			}
		}
		// arrays with generics 
		else if (t instanceof GenericArrayType) {
			GenericArrayType genericArray = (GenericArrayType) t;
			
			Type componentType = genericArray.getGenericComponentType();
			
			// due to a Java 6 bug, it is possible that the JVM classifies e.g. String[] or int[] as GenericArrayType instead of Class
			if (componentType instanceof Class) {
				
				Class<?> componentClass = (Class<?>) componentType;
				String className;
				// for int[], double[] etc.
				if(componentClass.isPrimitive()) {
					className = encodePrimitiveClass(componentClass);
				}
				// for String[], Integer[] etc.
				else {
					className = "L" + componentClass.getName() + ";";
				}
				
				Class<OUT> classArray = null;
				try {
					classArray = (Class<OUT>) Class.forName("[" + className);
				} catch (ClassNotFoundException e) {
					throw new InvalidTypesException("Could not convert GenericArrayType to Class.");
				}
				return getForClass(classArray);
			}
			
			TypeInformation<?> componentInfo = createTypeInfoWithTypeHierarchy(typeHierarchy, genericArray.getGenericComponentType(),
					in1Type, in2Type);
			return ObjectArrayTypeInfo.getInfoFor(t, componentInfo);
		}
		// objects with generics are treated as raw type
		else if (t instanceof ParameterizedType) {
			return privateGetForClass((Class<OUT>) ((ParameterizedType) t).getRawType());
		}
		// no tuple, no TypeVariable, no generic type
		else if (t instanceof Class) {
			return privateGetForClass((Class<OUT>) t);
		}
		
		throw new InvalidTypesException("Type Information could not be created.");
	}
	
	private <IN1, IN2> TypeInformation<?> createTypeInfoFromInputs(TypeVariable<?> returnTypeVar, ArrayList<Type> returnTypeHierarchy, 
			TypeInformation<IN1> in1TypeInfo, TypeInformation<IN2> in2TypeInfo) {

		Type matReturnTypeVar = materializeTypeVariable(returnTypeHierarchy, returnTypeVar);

		// variable could be resolved
		if (!(matReturnTypeVar instanceof TypeVariable)) {
			return createTypeInfoWithTypeHierarchy(returnTypeHierarchy, matReturnTypeVar, in1TypeInfo, in2TypeInfo);
		}
		else {
			returnTypeVar = (TypeVariable<?>) matReturnTypeVar;
		}

		TypeInformation<?> info = null;
		if (in1TypeInfo != null) {
			// find the deepest type variable that describes the type of input 1
			ParameterizedType baseClass = (ParameterizedType) returnTypeHierarchy.get(returnTypeHierarchy.size() - 1);
			Type in1Type = baseClass.getActualTypeArguments()[0];

			info = createTypeInfoFromInput(returnTypeVar, returnTypeHierarchy, in1Type, in1TypeInfo);
		}

		if (info == null && in2TypeInfo != null) {
			// find the deepest type variable that describes the type of input 2
			ParameterizedType baseClass = (ParameterizedType) returnTypeHierarchy.get(returnTypeHierarchy.size() - 1);
			Type in2Type = baseClass.getActualTypeArguments()[1];

			info = createTypeInfoFromInput(returnTypeVar, returnTypeHierarchy, in2Type, in2TypeInfo);
		}

		if (info != null) {
			return info;
		}

		return null;
	}
	
	private <IN1> TypeInformation<?> createTypeInfoFromInput(TypeVariable<?> returnTypeVar, ArrayList<Type> returnTypeHierarchy, 
			Type inType, TypeInformation<IN1> inTypeInfo) {
		TypeInformation<?> info = null;
		// the input is a type variable
		if (inType instanceof TypeVariable) {
			inType = materializeTypeVariable(returnTypeHierarchy, (TypeVariable<?>) inType);
			info = findCorrespondingInfo(returnTypeVar, inType, inTypeInfo);
		}
		// the input is a tuple that may contains type variables
		else if (inType instanceof ParameterizedType && Tuple.class.isAssignableFrom(((Class<?>)((ParameterizedType) inType).getRawType()))) {
			Type[] tupleElements = ((ParameterizedType) inType).getActualTypeArguments();
			// go thru all tuple elements and search for type variables
			for(int i = 0; i < tupleElements.length; i++) {
				if(tupleElements[i] instanceof TypeVariable) {
					inType = materializeTypeVariable(returnTypeHierarchy, (TypeVariable<?>) tupleElements[i]);
					info = findCorrespondingInfo(returnTypeVar, inType, ((TupleTypeInfo<?>) inTypeInfo).getTypeAt(i));
					if(info != null) {
						break;
					}
				}
			}
		}
		return info;
	}
	
	// --------------------------------------------------------------------------------------------
	//  Extract type parameters
	// --------------------------------------------------------------------------------------------
	
	public static Type getParameterType(Class<?> baseClass, Class<?> clazz, int pos) {
		return getParameterType(baseClass, null, clazz, pos);
	}
	
	private static Type getParameterType(Class<?> baseClass, ArrayList<Type> typeHierarchy, Class<?> clazz, int pos) {
		Type[] interfaceTypes = clazz.getGenericInterfaces();
		
		// search in interfaces for base class
		for (Type t : interfaceTypes) {
			Type parameter = getParameterTypeFromGenericType(baseClass, typeHierarchy, t, pos);
			if (parameter != null) {
				return parameter;
			}
		}
		
		// search in superclass for base class
		Type t = clazz.getGenericSuperclass();
		Type parameter = getParameterTypeFromGenericType(baseClass, typeHierarchy, t, pos);
		if (parameter != null) {
			return parameter;
		}
		
		throw new IllegalArgumentException("The types of the interface " + baseClass.getName() + " could not be inferred. " + 
						"Support for synthetic interfaces, lambdas, and generic types is limited at this point.");
	}
	
	private static Type getParameterTypeFromGenericType(Class<?> baseClass, ArrayList<Type> typeHierarchy, Type t, int pos) {
		// base class
		if (t instanceof ParameterizedType && baseClass.equals((Class<?>) ((ParameterizedType) t).getRawType())) {
			if (typeHierarchy != null) {
				typeHierarchy.add(t);
			}
			ParameterizedType baseClassChild = (ParameterizedType) t;				
			return baseClassChild.getActualTypeArguments()[pos];
		}
		// interface that extended base class as class or parameterized type
		else if (t instanceof ParameterizedType && baseClass.isAssignableFrom((Class<?>) ((ParameterizedType) t).getRawType())) {
			if (typeHierarchy != null) {
				typeHierarchy.add(t);
			}
			return getParameterType(baseClass, typeHierarchy, (Class<?>) ((ParameterizedType) t).getRawType(), pos);
		}			
		else if (t instanceof Class<?> && baseClass.isAssignableFrom((Class<?>) t)) {
			if (typeHierarchy != null) {
				typeHierarchy.add(t);
			}
			return getParameterType(baseClass, typeHierarchy, (Class<?>) t, pos);
		}
		return null;
	}
	
	// --------------------------------------------------------------------------------------------
	//  Validate input
	// --------------------------------------------------------------------------------------------
	
	private static void validateInputType(Type t, TypeInformation<?> inType) {
		ArrayList<Type> typeHierarchy = new ArrayList<Type>();
		try {
			validateInfo(typeHierarchy, t, inType);
		}
		catch(InvalidTypesException e) {
			throw new InvalidTypesException("Input mismatch: " + e.getMessage());
		}
	}
	
	private static void validateInputType(Class<?> baseClass, Class<?> clazz, int inputParamPos, TypeInformation<?> inType) {
		ArrayList<Type> typeHierarchy = new ArrayList<Type>();
		try {
			validateInfo(typeHierarchy, getParameterType(baseClass, typeHierarchy, clazz, inputParamPos), inType);
		}
		catch(InvalidTypesException e) {
			throw new InvalidTypesException("Input mismatch: " + e.getMessage());
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void validateInfo(ArrayList<Type> typeHierarchy, Type type, TypeInformation<?> typeInfo) {
		
		if (type == null) {
			throw new InvalidTypesException("Unknown Error. Type is null.");
		}
		
		if (typeInfo == null) {
			throw new InvalidTypesException("Unknown Error. TypeInformation is null.");
		}
		
		if (!(type instanceof TypeVariable<?>)) {
			// check for basic type
			if (typeInfo.isBasicType()) {
				
				TypeInformation<?> actual = null;
				// check if basic type at all
				if (!(type instanceof Class<?>) || (actual = BasicTypeInfo.getInfoFor((Class<?>) type)) == null) {
					throw new InvalidTypesException("Basic type expected.");
				}
				// check if correct basic type
				if (!typeInfo.equals(actual)) {
					throw new InvalidTypesException("Basic type '" + typeInfo + "' expected but was '" + actual + "'.");
				}
				
			}
			// check for tuple
			else if (typeInfo.isTupleType()) {
				// check if tuple at all
				if (!(type instanceof Class<?> && Tuple.class.isAssignableFrom((Class<?>) type))
						&& !(type instanceof ParameterizedType && Tuple.class.isAssignableFrom((Class<?>) ((ParameterizedType) type)
								.getRawType()))) {
					throw new InvalidTypesException("Tuple type expected.");
				}
				
				// do not allow usage of Tuple as type
				if (type instanceof Class<?> && ((Class<?>) type).equals(Tuple.class)) {
					throw new InvalidTypesException("Concrete subclass of Tuple expected.");
				}
				
				// go up the hierarchy until we reach immediate child of Tuple (with or without generics)
				while (!(type instanceof ParameterizedType && ((Class<?>) ((ParameterizedType) type).getRawType()).getSuperclass().equals(
						Tuple.class))
						&& !(type instanceof Class<?> && ((Class<?>) type).getSuperclass().equals(Tuple.class))) {
					typeHierarchy.add(type);
					// parameterized type
					if (type instanceof ParameterizedType) {
						type = ((Class<?>) ((ParameterizedType) type).getRawType()).getGenericSuperclass();
					}
					// class
					else {
						type = ((Class<?>) type).getGenericSuperclass();
					}
				}
				
				// check if immediate child of Tuple has generics
				if (type instanceof Class<?>) {
					throw new InvalidTypesException("Parameterized Tuple type expected.");
				}
				
				TupleTypeInfo<?> tti = (TupleTypeInfo<?>) typeInfo;
				
				Type[] subTypes = ((ParameterizedType) type).getActualTypeArguments();
				
				if (subTypes.length != tti.getArity()) {
					throw new InvalidTypesException("Tuple arity '" + tti.getArity() + "' expected but was '"
							+ subTypes.length + "'.");
				}
				
				for (int i = 0; i < subTypes.length; i++) {
					validateInfo(new ArrayList<Type>(typeHierarchy), subTypes[i], ((TupleTypeInfo<?>) typeInfo).getTypeAt(i));
				}
			}
			// check for Writable
			else if (typeInfo instanceof WritableTypeInfo<?>) {
				// check if writable at all
				if (!(type instanceof Class<?> && Writable.class.isAssignableFrom((Class<?>) type))) {
					throw new InvalidTypesException("Writable type expected.");
				}
				
				// check writable type contents
				Class<?> clazz = null;
				if (((WritableTypeInfo<?>) typeInfo).getTypeClass() != (clazz = (Class<?>) type)) {
					throw new InvalidTypesException("Writable type '"
							+ ((WritableTypeInfo<?>) typeInfo).getTypeClass().getCanonicalName() + "' expected but was '"
							+ clazz.getCanonicalName() + "'.");
				}
			}
			// check for basic array
			else if (typeInfo instanceof BasicArrayTypeInfo<?, ?>) {
				Type component = null;
				// check if array at all
				if (!(type instanceof Class<?> && ((Class<?>) type).isArray() && (component = ((Class<?>) type).getComponentType()) != null)
						&& !(type instanceof GenericArrayType && (component = ((GenericArrayType) type).getGenericComponentType()) != null)) {
					throw new InvalidTypesException("Array type expected.");
				}
				
				if (component instanceof TypeVariable<?>) {
					component = materializeTypeVariable(typeHierarchy, (TypeVariable<?>) component);
					if (component instanceof TypeVariable) {
						return;
					}
				}
				
				validateInfo(typeHierarchy, component, ((BasicArrayTypeInfo<?, ?>) typeInfo).getComponentInfo());
				
			}
			// check for object array
			else if (typeInfo instanceof ObjectArrayTypeInfo<?, ?>) {
				// check if array at all
				if (!(type instanceof Class<?> && ((Class<?>) type).isArray()) && !(type instanceof GenericArrayType)) {
					throw new InvalidTypesException("Object array type expected.");
				}
				
				// check component
				Type component = null;
				if (type instanceof Class<?>) {
					component = ((Class<?>) type).getComponentType();
				} else {
					component = ((GenericArrayType) type).getGenericComponentType();
				}
				
				if (component instanceof TypeVariable<?>) {
					component = materializeTypeVariable(typeHierarchy, (TypeVariable<?>) component);
					if (component instanceof TypeVariable) {
						return;
					}
				}
				
				validateInfo(typeHierarchy, component, ((ObjectArrayTypeInfo<?, ?>) typeInfo).getComponentInfo());
			}
			// check for value
			else if (typeInfo instanceof ValueTypeInfo<?>) {
				// check if value at all
				if (!(type instanceof Class<?> && Value.class.isAssignableFrom((Class<?>) type))) {
					throw new InvalidTypesException("Value type expected.");
				}
				
				TypeInformation<?> actual = null;
				// check value type contents
				if (!((ValueTypeInfo<?>) typeInfo).equals(actual = ValueTypeInfo.getValueTypeInfo((Class<? extends Value>) type))) {
					throw new InvalidTypesException("Value type '" + typeInfo + "' expected but was '" + actual + "'.");
				}
			}
			// check for POJO
			else if (typeInfo instanceof PojoTypeInfo) {
				Class<?> clazz = null;
				if (!(type instanceof Class<?> && ((PojoTypeInfo<?>) typeInfo).getTypeClass() == (clazz = (Class<?>) type))
						&& !(type instanceof ParameterizedType && (clazz = (Class<?>) ((ParameterizedType) type).getRawType()) == ((PojoTypeInfo<?>) typeInfo)
								.getTypeClass())) {
					throw new InvalidTypesException("POJO type '"
							+ ((PojoTypeInfo<?>) typeInfo).getTypeClass().getCanonicalName() + "' expected but was '"
							+ clazz.getCanonicalName() + "'.");
				}
			}
			// check for generic object
			else if (typeInfo instanceof GenericTypeInfo<?>) {
				Class<?> clazz = null;
				if (!(type instanceof Class<?> && ((GenericTypeInfo<?>) typeInfo).getTypeClass() == (clazz = (Class<?>) type))
						&& !(type instanceof ParameterizedType && (clazz = (Class<?>) ((ParameterizedType) type).getRawType()) == ((GenericTypeInfo<?>) typeInfo)
								.getTypeClass())) {
					throw new InvalidTypesException("Generic object type '"
							+ ((GenericTypeInfo<?>) typeInfo).getTypeClass().getCanonicalName() + "' expected but was '"
							+ clazz.getCanonicalName() + "'.");
				}
			}
		} else {
			type = materializeTypeVariable(typeHierarchy, (TypeVariable<?>) type);
			if (!(type instanceof TypeVariable)) {
				validateInfo(typeHierarchy, type, typeInfo);
			}
		}
	}
	
	// --------------------------------------------------------------------------------------------
	//  Utility methods
	// --------------------------------------------------------------------------------------------
	
	private static Type removeGenericWrapper(Type t) {
		if(t instanceof ParameterizedType 	&& 
				(Collector.class.isAssignableFrom((Class<?>) ((ParameterizedType) t).getRawType())
						|| Iterable.class.isAssignableFrom((Class<?>) ((ParameterizedType) t).getRawType()))) {
			return ((ParameterizedType) t).getActualTypeArguments()[0];
		}
		return t;
	}
	
	private static void validateLambdaGenericParameters(Method m) {
		// check the arguments
		for (Type t : m.getGenericParameterTypes()) {
			validateLambdaGenericParameter(t);
		}

		// check the return type
		validateLambdaGenericParameter(m.getGenericReturnType());
	}

	private static void validateLambdaGenericParameter(Type t) {
		if(!(t instanceof Class)) {
			return;
		}
		final Class<?> clazz = (Class<?>) t;

		if(clazz.getTypeParameters().length > 0) {
			throw new InvalidTypesException("The generic type parameters of '" + clazz.getSimpleName() + "' are missing. \n"
					+ "It seems that your compiler has not stored them into the .class file. \n"
					+ "Currently, only the Eclipse JDT compiler preserves the type information necessary to use the lambdas feature type-safely. \n"
					+ "See the documentation for more information about how to compile jobs containing lambda expressions.");
		}
	}
	
	private static String encodePrimitiveClass(Class<?> primitiveClass) {
		final String name = primitiveClass.getName();
		if (name.equals("boolean")) {
			return "Z";
		}
		else if (name.equals("byte")) {
			return "B";
		}
		else if (name.equals("char")) {
			return "C";
		}
		else if (name.equals("double")) {
			return "D";
		}
		else if (name.equals("float")) {
			return "F";
		}
		else if (name.equals("int")) {
			return "I";
		}
		else if (name.equals("long")) {
			return "J";
		}
		else if (name.equals("short")) {
			return "S";
		}
		throw new InvalidTypesException();
	}
	
	private static TypeInformation<?> findCorrespondingInfo(TypeVariable<?> typeVar, Type type, TypeInformation<?> corrInfo) {
		if (type instanceof TypeVariable) {
			TypeVariable<?> variable = (TypeVariable<?>) type;
			if (variable.getName().equals(typeVar.getName()) && variable.getGenericDeclaration().equals(typeVar.getGenericDeclaration())) {
				return corrInfo;
			}
		} else if (type instanceof ParameterizedType && Tuple.class.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType())) {
			ParameterizedType tuple = (ParameterizedType) type;
			Type[] args = tuple.getActualTypeArguments();
			
			for (int i = 0; i < args.length; i++) {
				TypeInformation<?> info = findCorrespondingInfo(typeVar, args[i], ((TupleTypeInfo<?>) corrInfo).getTypeAt(i));
				if (info != null) {
					return info;
				}
			}
		}
		return null;
	}
	
	private static Type materializeTypeVariable(ArrayList<Type> typeHierarchy, TypeVariable<?> typeVar) {
		TypeVariable<?> inTypeTypeVar = typeVar;
		// iterate thru hierarchy from top to bottom until type variable gets a class assigned
		for (int i = typeHierarchy.size() - 1; i >= 0; i--) {
			Type curT = typeHierarchy.get(i);
			
			// parameterized type
			if (curT instanceof ParameterizedType) {
				Class<?> rawType = ((Class<?>) ((ParameterizedType) curT).getRawType());
				
				for (int paramIndex = 0; paramIndex < rawType.getTypeParameters().length; paramIndex++) {
					
					TypeVariable<?> curVarOfCurT = rawType.getTypeParameters()[paramIndex];
					
					// check if variable names match
					if (curVarOfCurT.getName().equals(inTypeTypeVar.getName())
							&& curVarOfCurT.getGenericDeclaration().equals(inTypeTypeVar.getGenericDeclaration())) {
						Type curVarType = ((ParameterizedType) curT).getActualTypeArguments()[paramIndex];
						
						// another type variable level
						if (curVarType instanceof TypeVariable<?>) {
							inTypeTypeVar = (TypeVariable<?>) curVarType;
						}
						// class
						else {
							return curVarType;
						}
					}
				}
			}
		}
		// can not be materialized, most likely due to type erasure
		// return the type variable of the deepest level
		return inTypeTypeVar;
	}
	
	public static <X> TypeInformation<X> getForClass(Class<X> clazz) {
		return new TypeExtractor().privateGetForClass(clazz);
	}
	
	@SuppressWarnings("unchecked")
	private <X> TypeInformation<X> privateGetForClass(Class<X> clazz) {
		Validate.notNull(clazz);
		
		// check for abstract classes or interfaces
		if (!clazz.isPrimitive() && (Modifier.isInterface(clazz.getModifiers()) || (Modifier.isAbstract(clazz.getModifiers()) && !clazz.isArray()))) {
			throw new InvalidTypesException("Interfaces and abstract classes are not valid types: " + clazz);
		}

		if (clazz.equals(Object.class)) {
			// this will occur when trying to analyze POJOs that have generic, this
			// exception will be caught and a GenericTypeInfo will be created for the type.
			// at some point we might support this using Kryo
			throw new InvalidTypesException("Object is not a valid type.");
		}
		
		// check for arrays
		if (clazz.isArray()) {

			// primitive arrays: int[], byte[], ...
			PrimitiveArrayTypeInfo<X> primitiveArrayInfo = PrimitiveArrayTypeInfo.getInfoFor(clazz);
			if (primitiveArrayInfo != null) {
				return primitiveArrayInfo;
			}
			
			// basic type arrays: String[], Integer[], Double[]
			BasicArrayTypeInfo<X, ?> basicArrayInfo = BasicArrayTypeInfo.getInfoFor(clazz);
			if (basicArrayInfo != null) {
				return basicArrayInfo;
			}
			
			// object arrays
			else {
				return ObjectArrayTypeInfo.getInfoFor(clazz);
			}
		}
		
		// check for writable types
		if(Writable.class.isAssignableFrom(clazz)) {
			return (TypeInformation<X>) WritableTypeInfo.getWritableTypeInfo((Class<? extends Writable>) clazz);
		}
		
		// check for basic types
		TypeInformation<X> basicTypeInfo = BasicTypeInfo.getInfoFor(clazz);
		if (basicTypeInfo != null) {
			return basicTypeInfo;
		}
		
		// check for subclasses of Value
		if (Value.class.isAssignableFrom(clazz)) {
			Class<? extends Value> valueClass = clazz.asSubclass(Value.class);
			return (TypeInformation<X>) ValueTypeInfo.getValueTypeInfo(valueClass);
		}
		
		// check for subclasses of Tuple
		if (Tuple.class.isAssignableFrom(clazz)) {
			throw new InvalidTypesException("Type information extraction for tuples cannot be done based on the class.");
		}


		if (alreadySeen.contains(clazz)) {
			return new GenericTypeInfo<X>(clazz);
		}

		alreadySeen.add(clazz);

		if (clazz.equals(Class.class)) {
			// special case handling for Class, this should not be handled by the POJO logic
			return new GenericTypeInfo<X>(clazz);
		}

//		Disable POJO types for now (see https://mail-archives.apache.org/mod_mbox/incubator-flink-dev/201407.mbox/%3C53D96049.1060509%40cse.uta.edu%3E)
//
//		TypeInformation<X> pojoType =  analyzePojo(clazz);
//		if (pojoType != null) {
//			return pojoType;
//		}

		// return a generic type
		return new GenericTypeInfo<X>(clazz);
	}

	@SuppressWarnings("unused")
	private <X> TypeInformation<X> analyzePojo(Class<X> clazz) {
		List<Field> fields = getAllDeclaredFields(clazz);
		List<PojoField> pojoFields = new ArrayList<PojoField>();
		for (Field field : fields) {
			try {
				if (!Modifier.isTransient(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())) {
					pojoFields.add(new PojoField(field, privateCreateTypeInfo(field.getType())));
				}
			} catch (InvalidTypesException e) {
				// If some of the fields cannot be analyzed, just return a generic type info
				// right now this happens when a field is an interface (collections are the prominent case here) or
				// when the POJO is generic, in which case the fields will have type Object.
				// We might fix that in the future when we use Kryo.
				return new GenericTypeInfo<X>(clazz);
			}
		}

		PojoTypeInfo<X> pojoType = new PojoTypeInfo<X>(clazz, pojoFields);

		List<Method> methods = getAllDeclaredMethods(clazz);
		boolean containsReadObjectOrWriteObject = false;
		for (Method method : methods) {
			if (method.getName().equals("readObject") || method.getName().equals("writeObject")) {
				containsReadObjectOrWriteObject = true;
				break;
			}
		}

		// Try retrieving the default constructor, if it does not have one
		// we cannot use this because the serializer uses it.
		boolean hasDefaultCtor = true;
		try {
			clazz.getDeclaredConstructor();
		} catch (NoSuchMethodException e) {
			hasDefaultCtor = false;
		}


		if (!containsReadObjectOrWriteObject && hasDefaultCtor) {
			return pojoType;
		}

		return null;
	}

	// recursively determine all declared fields
	private static List<Field> getAllDeclaredFields(Class<?> clazz) {
		List<Field> result = new ArrayList<Field>();
		while (clazz != null) {
			Field[] fields = clazz.getDeclaredFields();
			for (Field field : fields) {
				result.add(field);
			}
			clazz = clazz.getSuperclass();
		}
		return result;
	}

	// recursively determine all declared methods
	private static List<Method> getAllDeclaredMethods(Class<?> clazz) {
		List<Method> result = new ArrayList<Method>();
		while (clazz != null) {
			Method[] methods = clazz.getDeclaredMethods();
			for (Method method : methods) {
				result.add(method);
			}
			clazz = clazz.getSuperclass();
		}
		return result;
	}


	public static <X> TypeInformation<X> getForObject(X value) {
		return new TypeExtractor().privateGetForObject(value);

	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <X> TypeInformation<X> privateGetForObject(X value) {
		Validate.notNull(value);
		
		// check if we can extract the types from tuples, otherwise work with the class
		if (value instanceof Tuple) {
			Tuple t = (Tuple) value;
			int numFields = t.getArity();
			
			TypeInformation<?>[] infos = new TypeInformation[numFields];
			for (int i = 0; i < numFields; i++) {
				Object field = t.getField(i);
				
				if (field == null) {
					throw new InvalidTypesException("Automatic type extraction is not possible on candidates with null values. "
							+ "Please specify the types directly.");
				}
				
				infos[i] = privateGetForObject(field);
			}
			
			return (TypeInformation<X>) new TupleTypeInfo(value.getClass(), infos);
		} else {
			return privateGetForClass((Class<X>) value.getClass());
		}
	}
}
