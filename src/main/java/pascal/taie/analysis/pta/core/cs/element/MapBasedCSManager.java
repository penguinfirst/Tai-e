/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2020-- Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020-- Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * Tai-e is only for educational and academic purposes,
 * and any form of commercial use is disallowed.
 * Distribution of Tai-e is disallowed without the approval.
 */

package pascal.taie.analysis.pta.core.cs.element;

import pascal.taie.World;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassNames;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.Indexer;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.TwoKeyMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages data by maintaining the data and their context-sensitive
 * counterparts by maps.
 */
public class MapBasedCSManager implements CSManager {

    private final PointerManager ptrManager = new PointerManager();

    private final CSObjManager objManager = new CSObjManager();

    private final CSMethodManager mtdManager = new CSMethodManager();

    private final TwoKeyMap<Invoke, Context, CSCallSite> callSites = Maps.newTwoKeyMap();

    @Override
    public CSVar getCSVar(Context context, Var var) {
        return ptrManager.getCSVar(context, var);
    }

    @Override
    public StaticField getStaticField(JField field) {
        return ptrManager.getStaticField(field);
    }

    @Override
    public InstanceField getInstanceField(CSObj base, JField field) {
        return ptrManager.getInstanceField(base, field);
    }

    @Override
    public ArrayIndex getArrayIndex(CSObj array) {
        return ptrManager.getArrayIndex(array);
    }

    @Override
    public Collection<Var> getVars() {
        return ptrManager.getVars();
    }

    @Override
    public Collection<CSVar> getCSVars() {
        return ptrManager.getCSVars();
    }

    @Override
    public Collection<CSVar> getCSVarsOf(Var var) {
        return ptrManager.getCSVarsOf(var);
    }

    @Override
    public Collection<StaticField> getStaticFields() {
        return ptrManager.getStaticFields();
    }

    @Override
    public Collection<InstanceField> getInstanceFields() {
        return ptrManager.getInstanceFields();
    }

    @Override
    public Collection<ArrayIndex> getArrayIndexes() {
        return ptrManager.getArrayIndexes();
    }

    @Override
    public CSObj getCSObj(Context heapContext, Obj obj) {
        return objManager.getCSObj(heapContext, obj);
    }

    @Override
    public Collection<CSObj> getObjects() {
        return objManager.getObjects();
    }

    @Override
    public Indexer<CSObj> getObjectIndexer() {
        return objManager;
    }

    @Override
    public CSCallSite getCSCallSite(Context context, Invoke callSite) {
        return callSites.computeIfAbsent(callSite, context, CSCallSite::new);
    }

    @Override
    public CSMethod getCSMethod(Context context, JMethod method) {
        return mtdManager.getCSMethod(context, method);
    }

    @Override
    public Indexer<CSMethod> getMethodIndexer() {
        return mtdManager;
    }

    private static class PointerManager {

        private final TwoKeyMap<Var, Context, CSVar> vars = Maps.newTwoKeyMap();

        private final Map<JField, StaticField> staticFields = Maps.newMap();

        private final TwoKeyMap<CSObj, JField, InstanceField> instanceFields = Maps.newTwoKeyMap();

        private final Map<CSObj, ArrayIndex> arrayIndexes = Maps.newMap();

        /**
         * Counter for assigning unique indexes to Pointers.
         */
        private int counter = 0;

        private CSVar getCSVar(Context context, Var var) {
            return vars.computeIfAbsent(var, context,
                    (v, c) -> new CSVar(v, c, counter++));
        }

        private StaticField getStaticField(JField field) {
            return staticFields.computeIfAbsent(field,
                    f -> new StaticField(f, counter++));
        }

        private InstanceField getInstanceField(CSObj base, JField field) {
            return instanceFields.computeIfAbsent(base, field,
                    (b, f) -> new InstanceField(b, f, counter++));
        }

        private ArrayIndex getArrayIndex(CSObj array) {
            return arrayIndexes.computeIfAbsent(array,
                    a -> new ArrayIndex(a, counter++));
        }

        private Collection<Var> getVars() {
            return vars.keySet();
        }

        private Collection<CSVar> getCSVars() {
            return vars.values();
        }

        private Collection<CSVar> getCSVarsOf(Var var) {
            var csVars = vars.get(var);
            return csVars != null ? csVars.values() : Set.of();
        }

        private Collection<StaticField> getStaticFields() {
            return Collections.unmodifiableCollection(staticFields.values());
        }

        private Collection<InstanceField> getInstanceFields() {
            return instanceFields.values();
        }

        private Collection<ArrayIndex> getArrayIndexes() {
            return Collections.unmodifiableCollection(arrayIndexes.values());
        }
    }

    private static class CSObjManager implements Indexer<CSObj> {

        private final TwoKeyMap<Obj, Context, CSObj> objMap = Maps.newTwoKeyMap();

        private final TypeSystem typeSystem = World.get().getTypeSystem();

        private final Type throwable = typeSystem.getClassType(ClassNames.THROWABLE);

        /**
         * Counter for assign unique indexes to throwable objects.
         */
        private int throwableCounter = 0;

        /**
         * Number of indexes reserved for throwable objects.
         */
        private static final int THROWABLE_BUDGET = 2048;

        /**
         * Counter for assigning unique indexes to other CSObjs.
         */
        private int counter = THROWABLE_BUDGET;

        /**
         * Maps index to CSObj.
         * Since there are empty slots, using array (instead of List)
         * is more convenient.
         */
        private CSObj[] objs = new CSObj[65536];

        CSObj getCSObj(Context heapContext, Obj obj) {
            return objMap.computeIfAbsent(obj, heapContext, (o, c) -> {
                int index = getCSObjIndex(o);
                CSObj csObj = new CSObj(o, c, index);
                storeCSObj(csObj, index);
                return csObj;
            });
        }

        private int getCSObjIndex(Obj obj) {
            if (typeSystem.isSubtype(throwable, obj.getType()) &&
                    throwableCounter < THROWABLE_BUDGET) {
                return throwableCounter++;
            } else {
                return counter++;
            }
        }

        /**
         * Stores {@code csObj} to the {@code objs} array with the position
         * specified by {@code index}.
         */
        private void storeCSObj(CSObj csObj, int index) {
            if (index >= objs.length) {
                int newLength = Math.max(index + 1, (int) (objs.length * 1.5));
                CSObj[] oldArray = objs;
                objs = new CSObj[newLength];
                System.arraycopy(oldArray, 0, objs, 0, oldArray.length);
            }
            objs[index] = csObj;
        }

        Collection<CSObj> getObjects() {
            return objMap.values();
        }

        @Override
        public int getIndex(CSObj o) {
            return o.getIndex();
        }

        @Override
        public CSObj getObject(int index) {
            return objs[index];
        }
    }

    private static class CSMethodManager implements Indexer<CSMethod> {

        private final TwoKeyMap<JMethod, Context, CSMethod> methodMap = Maps.newTwoKeyMap();

        /**
         * Counter for assigning unique indexes to CSMethods.
         */
        private int counter = 0;

        private final List<CSMethod> methods = new ArrayList<>(65536);

        private CSMethod getCSMethod(Context context, JMethod method) {
            return methodMap.computeIfAbsent(method, context, (m, c) -> {
                CSMethod csMethod = new CSMethod(m, c, counter++);
                methods.add(csMethod);
                return csMethod;
            });
        }

        @Override
        public int getIndex(CSMethod m) {
            return m.getIndex();
        }

        @Override
        public CSMethod getObject(int index) {
            return methods.get(index);
        }
    }
}
