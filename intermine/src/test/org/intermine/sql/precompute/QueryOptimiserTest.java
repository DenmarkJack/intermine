package org.flymine.sql.precompute;

import junit.framework.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.HashSet;
import org.flymine.sql.query.*;
import org.flymine.util.StringUtil;
import org.flymine.util.ConsistentSet;

public class QueryOptimiserTest extends TestCase
{
    private Query singleTableQuery, singleTableQueryWithFieldAlias;
    private Query singleTableQueryWithTableAlias, singleTableQueryNoConstraints;
    private Query twoSameTableQuery;

    public QueryOptimiserTest(String arg1) {
        super(arg1);
    }

    public void setUp() throws Exception {
        singleTableQuery = new Query("SELECT mytable.a FROM mytable WHERE mytable.a = 1");
        singleTableQueryWithFieldAlias = new Query("SELECT mytable.a AS alias FROM mytable WHERE mytable.a = 1");
        singleTableQueryWithTableAlias = new Query("SELECT table1.a FROM mytable table1 WHERE table1.a = 1");
        singleTableQueryNoConstraints = new Query("SELECT mytable.a FROM mytable");
        twoSameTableQuery = new Query("SELECT table1.b, table2.a FROM mytable table1, mytable table2 WHERE table1.a = 1 AND table2.b < 3 AND table1.a = table2.join");
    }

    public void testRemapAliases() throws Exception {
        AbstractTable t1 = new Table("table1", "alias1");
        AbstractTable t2 = new Table("table2", "alias2");
        AbstractTable t3 = new Table("table3", "alias3");
        AbstractTable t4 = new Table("table1", "alias4");
        AbstractTable t5 = new Table("table2", "alias5");
        AbstractTable t6 = new Table("table3", "alias6");
        AbstractTable t7 = new Table("table4", "alias1");

        Map map = new HashMap();
        map.put(t1, t4);
        map.put(t2, t5);
        map.put(t3, t6);

        Set tables = new ConsistentSet();
        tables.add(t4);
        tables.add(t5);
        tables.add(t6);
        tables.add(t7);

        QueryOptimiser.remapAliases(map, tables);

        assertEquals(t1, map.get(t1));
        assertEquals(t2, map.get(t2));
        assertEquals(t3, map.get(t3));
        assertTrue(!"alias1".equals(t7.getAlias()));
    }

    public void testCompareConstraints() throws Exception {
        Set equalsSet = new HashSet();
        assertTrue(QueryOptimiser.compareConstraints(twoSameTableQuery.getWhere(),
                                                     twoSameTableQuery.getWhere(), equalsSet));
        assertEquals(twoSameTableQuery.getWhere(), equalsSet);
        equalsSet = new HashSet();
        assertTrue(QueryOptimiser.compareConstraints(singleTableQueryNoConstraints.getWhere(),
                                                     singleTableQuery.getWhere(), equalsSet));
        assertEquals(singleTableQueryNoConstraints.getWhere(), equalsSet);
        equalsSet = new HashSet();
        assertTrue(QueryOptimiser.compareConstraints(singleTableQueryWithTableAlias.getWhere(),
                                                     twoSameTableQuery.getWhere(), equalsSet));
        assertEquals(singleTableQueryWithTableAlias.getWhere(), equalsSet);

        equalsSet = new HashSet();
        assertFalse(QueryOptimiser.compareConstraints(singleTableQuery.getWhere(),
                                                      singleTableQueryNoConstraints.getWhere(),
                                                      equalsSet));
        // equalsSet is now undefined.
        equalsSet = new HashSet();
        assertFalse(QueryOptimiser.compareConstraints(twoSameTableQuery.getWhere(),
                                                      singleTableQueryWithTableAlias.getWhere(),
                                                      equalsSet));
        // equalsSet is now undefined.
    }

    public void testCompareSelectLists() throws Exception {
        SelectValue v1 = new SelectValue(new Constant("hello"), "alias1");
        SelectValue v2 = new SelectValue(new Constant("hello"), "alias2");
        SelectValue v3 = new SelectValue(new Constant("a"), "alias3");
        SelectValue v4 = new SelectValue(new Constant("a"), "alias4");
        SelectValue v5 = new SelectValue(new Constant("a"), "alias5");
        SelectValue v6 = new SelectValue(new Constant("flibble"), "alias6");

        List l1 = new ArrayList();
        l1.add(v1);
        l1.add(v3);
        List l2 = new ArrayList();
        l2.add(v2);
        l2.add(v4);
        l2.add(v5);
        List l3 = new ArrayList();
        l3.add(v2);
        l3.add(v4);
        l3.add(v6);
        List l4 = new ArrayList();

        assertTrue(QueryOptimiser.compareSelectLists(l1, l2));
        assertTrue(QueryOptimiser.compareSelectLists(l2, l1));
        assertTrue(QueryOptimiser.compareSelectLists(l1, l1));
        assertTrue(QueryOptimiser.compareSelectLists(l1, l3));
        assertFalse(QueryOptimiser.compareSelectLists(l3, l1));
        assertTrue(QueryOptimiser.compareSelectLists(l4, l1));
        assertFalse(QueryOptimiser.compareSelectLists(l1, l4));
        assertTrue(QueryOptimiser.compareSelectLists(l4, l4));
    }

    public void testFindTableForAlias() throws Exception {
        Set set = new HashSet();
        Table t1 = new Table("table1", "alias1");
        Table t2 = new Table("table2", "alias2");
        Table t3 = new Table("table2", "alias3");
        set.add(t1);
        set.add(t2);
        set.add(t3);

        assertEquals(t1, QueryOptimiser.findTableForAlias("alias1", set));
        assertEquals(t2, QueryOptimiser.findTableForAlias("alias2", set));
        assertEquals(t3, QueryOptimiser.findTableForAlias("alias3", set));
        assertNull(QueryOptimiser.findTableForAlias("alias4", set));
    }

    public void testReconstructAbstractValue() throws Exception {
        Collection values = new HashSet();
        Table t1 = new Table("table1", "tablealias1");
        Table t2 = new Table("table2", "tablealias2");
        AbstractValue v1 = new Constant("c1");
        AbstractValue v2 = new Field("field1", t1);
        Function v3 = new Function(Function.PLUS);
        v3.add(v1);
        v3.add(v2);
        AbstractValue v4 = new Constant("c4");
        Function v6 = new Function(Function.MINUS);
        v6.add(v1);
        v6.add(v2);
        Function v7 = new Function(Function.MAX);
        v7.add(v2);
        AbstractValue v8 = new Function(Function.COUNT);
        AbstractValue v9 = new Field("field2", t1);
        AbstractValue v10 = new Field("field3", t2);
        Function v11 = new Function(Function.MULTIPLY);
        v11.add(v9);
        v11.add(v10);
        SelectValue s1 = new SelectValue(v1, "alias1");
        SelectValue s2 = new SelectValue(v2, "alias2");
        SelectValue s3 = new SelectValue(v3, "alias3");
        Map valueMap = new HashMap();
        valueMap.put(v1, s1);
        valueMap.put(v2, s2);
        valueMap.put(v3, s3);

        Table precomputedSqlTable = new Table("precomp1", "precompalias1");
        Set tableSet = new HashSet();
        tableSet.add(t1);

        AbstractValue ev1 = new Field("alias1", precomputedSqlTable);
        AbstractValue ev2 = new Field("alias2", precomputedSqlTable);
        AbstractValue ev3 = new Field("alias3", precomputedSqlTable);
        AbstractValue ev4 = v4;
        Function ev6 = new Function(Function.MINUS);
        ev6.add(new Field("alias1", precomputedSqlTable));
        ev6.add(new Field("alias2", precomputedSqlTable));
        Function ev7 = new Function(Function.MAX);
        ev7.add(new Field("alias2", precomputedSqlTable));
        AbstractValue ev8 = v8;
        AbstractValue ev10 = v10;

        assertEquals(ev1, QueryOptimiser.reconstructAbstractValue(v1, precomputedSqlTable, valueMap, tableSet, true));
        assertEquals(ev1, QueryOptimiser.reconstructAbstractValue(v1, precomputedSqlTable, valueMap, tableSet, false));
        assertEquals(ev2, QueryOptimiser.reconstructAbstractValue(v2, precomputedSqlTable, valueMap, tableSet, true));
        assertEquals(ev2, QueryOptimiser.reconstructAbstractValue(v2, precomputedSqlTable, valueMap, tableSet, false));
        assertEquals(ev3, QueryOptimiser.reconstructAbstractValue(v3, precomputedSqlTable, valueMap, tableSet, true));
        assertEquals(ev3, QueryOptimiser.reconstructAbstractValue(v3, precomputedSqlTable, valueMap, tableSet, false));
        assertEquals(ev4, QueryOptimiser.reconstructAbstractValue(v4, precomputedSqlTable, valueMap, tableSet, true));
        assertEquals(ev4, QueryOptimiser.reconstructAbstractValue(v4, precomputedSqlTable, valueMap, tableSet, false));
        assertEquals(ev6, QueryOptimiser.reconstructAbstractValue(v6, precomputedSqlTable, valueMap, tableSet, true));
        assertEquals(ev6, QueryOptimiser.reconstructAbstractValue(v6, precomputedSqlTable, valueMap, tableSet, false));
        try {
            QueryOptimiser.reconstructAbstractValue(v7, precomputedSqlTable, valueMap, tableSet, true);
            fail("Expected QueryOptimiserException");
        } catch (QueryOptimiserException e) {
        }
        assertEquals(ev7, QueryOptimiser.reconstructAbstractValue(v7, precomputedSqlTable, valueMap, tableSet, false));
        try {
            QueryOptimiser.reconstructAbstractValue(v8, precomputedSqlTable, valueMap, tableSet, true);
            fail("Expected QueryOptimiserException");
        } catch (QueryOptimiserException e) {
        }
        assertEquals(ev8, QueryOptimiser.reconstructAbstractValue(v8, precomputedSqlTable, valueMap, tableSet, false));
        try {
            QueryOptimiser.reconstructAbstractValue(v9, precomputedSqlTable, valueMap, tableSet, true);
            fail("Expected QueryOptimiserException");
        } catch (QueryOptimiserException e) {
        }
        try {
            QueryOptimiser.reconstructAbstractValue(v9, precomputedSqlTable, valueMap, tableSet, false);
            fail("Expected QueryOptimiserException");
        } catch (QueryOptimiserException e) {
        }
        assertEquals(ev10, QueryOptimiser.reconstructAbstractValue(v10, precomputedSqlTable, valueMap, tableSet, true));
        assertEquals(ev10, QueryOptimiser.reconstructAbstractValue(v10, precomputedSqlTable, valueMap, tableSet, false));
        try {
            QueryOptimiser.reconstructAbstractValue(v11, precomputedSqlTable, valueMap, tableSet, true);
            fail("Expected QueryOptimiserException");
        } catch (QueryOptimiserException e) {
        }
        try {
            QueryOptimiser.reconstructAbstractValue(v11, precomputedSqlTable, valueMap, tableSet, false);
            fail("Expected QueryOptimiserException");
        } catch (QueryOptimiserException e) {
        }

        List list = new ArrayList();
        list.add(v1);
        list.add(v2);
        list.add(v3);
        list.add(v4);
        list.add(v6);
        list.add(v7);
        list.add(v8);
        list.add(v10);
        List elist = new ArrayList();
        elist.add(ev1);
        elist.add(ev2);
        elist.add(ev3);
        elist.add(ev4);
        elist.add(ev6);
        elist.add(ev7);
        elist.add(ev8);
        elist.add(ev10);
        List newList = new ArrayList();
        QueryOptimiser.reconstructAbstractValues(list, precomputedSqlTable, valueMap, tableSet, false, newList);
        assertEquals(elist, newList);
    }

    public void testReconstructSelectValues() throws Exception {
        Collection values = new HashSet();
        AbstractValue v1 = new Constant("c1");
        AbstractValue v2 = new Constant("c2");
        AbstractValue v3 = new Constant("c3");
        AbstractValue v4 = new Constant("c4");
        SelectValue sq1 = new SelectValue(v1, "queryalias1");
        SelectValue sq2 = new SelectValue(v2, "queryalias2");
        SelectValue sq3 = new SelectValue(v3, "queryalias3");
        SelectValue sq4 = new SelectValue(v4, "queryalias4");
        SelectValue sp1 = new SelectValue(v1, "precompalias1");
        SelectValue sp2 = new SelectValue(v2, "precompalias2");
        Map valueMap = new HashMap();
        valueMap.put(v1, sp1);
        valueMap.put(v2, sp2);
        List oldSelect = new ArrayList();
        oldSelect.add(sq1);
        oldSelect.add(sq2);
        oldSelect.add(sq3);
        oldSelect.add(sq4);
        Query newQuery = new Query();
        Table precomputedSqlTable = new Table("precomp1", "precomptablealias1");
        Set tableSet = new HashSet();
        QueryOptimiser.reconstructSelectValues(oldSelect, precomputedSqlTable, valueMap, tableSet, true, newQuery);

        SelectValue se1 = new SelectValue(new Field("precompalias1", precomputedSqlTable), "queryalias1");
        SelectValue se2 = new SelectValue(new Field("precompalias2", precomputedSqlTable), "queryalias2");
        SelectValue se3 = new SelectValue(v3, "queryalias3");
        SelectValue se4 = new SelectValue(v4, "queryalias4");
        List expectedSelect = new ArrayList();
        expectedSelect.add(se1);
        expectedSelect.add(se2);
        expectedSelect.add(se3);
        expectedSelect.add(se4);

        assertEquals(expectedSelect, newQuery.getSelect());
    }

    public void testReconstructAbstractConstraint() throws Exception {
        Table precomputedSqlTable = new Table("precomp1", "precomptablelias");
        Table t1 = new Table("table1", "tableAlias1");
        AbstractValue v1 = new Field("field1", t1);
        AbstractValue v2 = new Field("field2", t1);
        AbstractValue v3 = new Field("field3", t1);
        AbstractValue v4 = new Field("field4", t1);
        SelectValue s1 = new SelectValue(v1, "alias1");
        SelectValue s2 = new SelectValue(v2, "alias2");
        SelectValue s3 = new SelectValue(v3, "alias3");
        SelectValue s4 = new SelectValue(v4, "alias4");
        Map valueMap = new HashMap();
        valueMap.put(v1, s1);
        valueMap.put(v2, s2);
        valueMap.put(v3, s3);
        valueMap.put(v4, s4);
        Set tableSet = new HashSet();
        tableSet.add(t1);
        AbstractValue ev1 = new Field("alias1", precomputedSqlTable);
        AbstractValue ev2 = new Field("alias2", precomputedSqlTable);
        AbstractValue ev3 = new Field("alias3", precomputedSqlTable);
        AbstractValue ev4 = new Field("alias4", precomputedSqlTable);

        Constraint c1 = new Constraint(v1, Constraint.EQ, v2);
        NotConstraint c2 = new NotConstraint(c1);
        Constraint c3 = new Constraint(v3, Constraint.LT, v4);
        ConstraintSet c4 = new ConstraintSet();
        c4.add(c1);
        c4.add(c3);

        Constraint ec1 = new Constraint(ev1, Constraint.EQ, ev2);
        NotConstraint ec2 = new NotConstraint(ec1);
        Constraint ec3 = new Constraint(ev3, Constraint.LT, ev4);
        ConstraintSet ec4 = new ConstraintSet();
        ec4.add(ec1);
        ec4.add(ec3);

        assertEquals(ec1, QueryOptimiser.reconstructAbstractConstraint(c1, precomputedSqlTable, valueMap, tableSet, false));
        assertEquals(ec2, QueryOptimiser.reconstructAbstractConstraint(c2, precomputedSqlTable, valueMap, tableSet, false));
        assertEquals(ec3, QueryOptimiser.reconstructAbstractConstraint(c3, precomputedSqlTable, valueMap, tableSet, false));
        assertEquals(ec4, QueryOptimiser.reconstructAbstractConstraint(c4, precomputedSqlTable, valueMap, tableSet, false));

        Set set = new HashSet();
        set.add(c1);
        set.add(c2);
        set.add(c3);
        set.add(c4);
        Set eset = new HashSet();
        eset.add(ec1);
        eset.add(ec2);
        eset.add(ec3);
        eset.add(ec4);
        Set newSet = new HashSet();
        Set constraintEqualsSet = new HashSet();
        QueryOptimiser.reconstructAbstractConstraints(set, precomputedSqlTable, valueMap, tableSet, false, newSet, constraintEqualsSet);
        assertEquals(eset, newSet);

        newSet = new HashSet();
        eset = new HashSet();
        eset.add(ec1);
        eset.add(ec2);
        eset.add(ec4);
        constraintEqualsSet.add(c3);
        QueryOptimiser.reconstructAbstractConstraints(set, precomputedSqlTable, valueMap, tableSet, false, newSet, constraintEqualsSet);
        assertEquals(eset, newSet);
    }

    public void testMergeGroupByFits() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, count(*) as stuff from table as table1, somethingelse as table2 WHERE table1.c = table2.a GROUP BY table1.a, table1.b, table1.d HAVING table1.d = 'five' ORDER BY table1.a LIMIT 100 OFFSET 0");
        Query pq1 = new Query("SELECT table3.a AS sahjg, table3.b AS aytq, count(*) AS hksf, table3.d AS fdjsa FROM table AS table3, somethingelse AS table4 WHERE table3.c = table4.a GROUP BY table3.a, table3.b, table3.d");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Query eq1 = new Query("SELECT P42.sahjg AS t1_a, P42.aytq AS t1_b, P42.hksf AS stuff from precomp1 AS P42 WHERE P42.fdjsa = 'five' ORDER BY P42.sahjg LIMIT 100 OFFSET 0");
        Set eSet = new HashSet();
        eSet.add(eq1);

        StringUtil.setNextUniqueNumber(42);
        Set newSet = QueryOptimiser.mergeGroupBy(pt1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeGroupByWrongTables() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, count(*) as stuff from table as table1, somethingdifferent as table2 WHERE table1.c = table2.a GROUP BY table1.a, table1.b, table1.d HAVING table1.d = 'five' ORDER BY table1.a LIMIT 100 OFFSET 0");
        Query pq1 = new Query("SELECT table3.a AS sahjg, table3.b AS aytq, count(*) AS hksf, table3.d AS fdjsa FROM table AS table3, somethingelse AS table4 WHERE table3.c = table4.a GROUP BY table3.a, table3.b, table3.d");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Set eSet = new HashSet();

        Set newSet = QueryOptimiser.mergeGroupBy(pt1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeGroupByExtraSelect() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, table1.e AS t1_e, count(*) as stuff from table as table1, somethingelse as table2 WHERE table1.c = table2.a GROUP BY table1.a, table1.b, table1.d HAVING table1.d = 'five' ORDER BY table1.a LIMIT 100 OFFSET 0");
        Query pq1 = new Query("SELECT table3.a AS sahjg, table3.b AS aytq, count(*) AS hksf, table3.d AS fdjsa FROM table AS table3, somethingelse AS table4 WHERE table3.c = table4.a GROUP BY table3.a, table3.b, table3.d");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Set eSet = new HashSet();

        Set newSet = QueryOptimiser.mergeGroupBy(pt1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeGroupByDifferentWhere() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, count(*) as stuff from table as table1, somethingelse as table2 WHERE table1.c = table2.b GROUP BY table1.a, table1.b, table1.d HAVING table1.d = 'five' ORDER BY table1.a LIMIT 100 OFFSET 0");
        Query pq1 = new Query("SELECT table3.a AS sahjg, table3.b AS aytq, count(*) AS hksf, table3.d AS fdjsa FROM table AS table3, somethingelse AS table4 WHERE table3.c = table4.a GROUP BY table3.a, table3.b, table3.d");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Set eSet = new HashSet();

        Set newSet = QueryOptimiser.mergeGroupBy(pt1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeGroupByDifferentGroupBy() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, count(*) as stuff from table as table1, somethingelse as table2 WHERE table1.c = table2.a GROUP BY table1.a, table1.b, table1.d, table1.e HAVING table1.d = 'five' ORDER BY table1.a LIMIT 100 OFFSET 0");
        Query pq1 = new Query("SELECT table3.a AS sahjg, table3.b AS aytq, count(*) AS hksf, table3.d AS fdjsa FROM table AS table3, somethingelse AS table4 WHERE table3.c = table4.a GROUP BY table3.a, table3.b, table3.d");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Set eSet = new HashSet();

        Set newSet = QueryOptimiser.mergeGroupBy(pt1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeGroupByWrongHaving() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, count(*) as stuff from table as table1, somethingelse as table2 WHERE table1.c = table2.a GROUP BY table1.a, table1.b, table1.d HAVING table1.d = 'five' ORDER BY table1.a LIMIT 100 OFFSET 0");
        Query pq1 = new Query("SELECT table3.a AS sahjg, table3.b AS aytq, count(*) AS hksf, table3.d AS fdjsa FROM table AS table3, somethingelse AS table4 WHERE table3.c = table4.a GROUP BY table3.a, table3.b, table3.d HAVING table3.b = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Set eSet = new HashSet();

        Set newSet = QueryOptimiser.mergeGroupBy(pt1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeGroupByDifferentDistinct() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, count(*) as stuff from table as table1, somethingelse as table2 WHERE table1.c = table2.a GROUP BY table1.a, table1.b, table1.d HAVING table1.d = 'five' ORDER BY table1.a LIMIT 100 OFFSET 0");
        Query pq1 = new Query("SELECT DISTINCT table3.a AS sahjg, table3.b AS aytq, count(*) AS hksf, table3.d AS fdjsa FROM table AS table3, somethingelse AS table4 WHERE table3.c = table4.a GROUP BY table3.a, table3.b, table3.d");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Set eSet = new HashSet();

        Set newSet = QueryOptimiser.mergeGroupBy(pt1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeGroupByWithUselessConstraint() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, count(*) as stuff from table as table1, somethingelse as table2 WHERE table1.c = table2.a GROUP BY table1.a, table1.b, table1.d HAVING table1.d = 'five' ORDER BY table1.a LIMIT 100 OFFSET 0");
        Query pq1 = new Query("SELECT table3.a AS sahjg, table3.b AS aytq, count(*) AS hksf, table3.d AS fdjsa FROM table AS table3, somethingelse AS table4 WHERE table3.c = table4.a GROUP BY table3.a, table3.b, table3.d HAVING table3.d = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Query eq1 = new Query("SELECT P42.sahjg AS t1_a, P42.aytq AS t1_b, P42.hksf AS stuff from precomp1 AS P42 ORDER BY P42.sahjg LIMIT 100 OFFSET 0");
        Set eSet = new HashSet();
        eSet.add(eq1);

        StringUtil.setNextUniqueNumber(42);
        Set newSet = QueryOptimiser.mergeGroupBy(pt1, q1);

        assertEquals(eSet, newSet);
    }

    public void testAddNonCoveredFrom() throws Exception {
        Set input = new HashSet();
        Set subtract = new HashSet();
        input.add("item1");
        input.add("item2");
        input.add("item3");
        subtract.add("item2");
        Set output = new HashSet();
        QueryOptimiser.addNonCoveredFrom(input, subtract, output);

        Set expectedOutput = new HashSet();
        expectedOutput.add("item1");
        expectedOutput.add("item3");

        assertEquals(expectedOutput, output);
    }

    public void testMergeSimple() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b from table as table1 WHERE table1.c = 'five'");
        Query pq1 = new Query("SELECT table2.a AS kjfd, table2.b AS ddfw FROM table as table2 WHERE table2.c = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Query eq1 = new Query("SELECT P42.kjfd AS t1_a, P42.ddfw AS t1_b FROM precomp1 AS P42");

        Set eSet = new HashSet();
        eSet.add(eq1);

        StringUtil.setNextUniqueNumber(42);
        Set newSet = QueryOptimiser.merge(pt1, q1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeOkayDistinct1() throws Exception {
        Query q1 = new Query("SELECT DISTINCT table1.a AS t1_a, table1.b AS t1_b from table as table1 WHERE table1.c = 'five'");
        Query pq1 = new Query("SELECT table2.a AS kjfd, table2.b AS ddfw FROM table as table2 WHERE table2.c = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Query eq1 = new Query("SELECT DISTINCT P42.kjfd AS t1_a, P42.ddfw AS t1_b FROM precomp1 AS P42");

        Set eSet = new HashSet();
        eSet.add(eq1);

        StringUtil.setNextUniqueNumber(42);
        Set newSet = QueryOptimiser.merge(pt1, q1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeOkayDistinct2() throws Exception {
        Query q1 = new Query("SELECT DISTINCT table1.a AS t1_a, table1.b AS t1_b from table as table1 WHERE table1.c = 'five'");
        Query pq1 = new Query("SELECT DISTINCT table2.a AS kjfd, table2.b AS ddfw FROM table as table2 WHERE table2.c = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Query eq1 = new Query("SELECT DISTINCT P42.kjfd AS t1_a, P42.ddfw AS t1_b FROM precomp1 AS P42");

        Set eSet = new HashSet();
        eSet.add(eq1);

        StringUtil.setNextUniqueNumber(42);
        Set newSet = QueryOptimiser.merge(pt1, q1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeWrongDistinct() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b from table as table1 WHERE table1.c = 'five'");
        Query pq1 = new Query("SELECT DISTINCT table2.a AS kjfd, table2.b AS ddfw FROM table as table2 WHERE table2.c = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");

        Set eSet = new HashSet();

        Set newSet = QueryOptimiser.merge(pt1, q1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeWrongWhere() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b from table as table1 WHERE table1.c = 'five'");
        Query pq1 = new Query("SELECT table2.a AS kjfd, table2.b AS ddfw FROM table as table2 WHERE table2.b = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");

        Set eSet = new HashSet();

        Set newSet = QueryOptimiser.merge(pt1, q1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeWrongSelect() throws Exception {
        Query q1 = new Query("SELECT table1.c AS t1_c, table1.b AS t1_b from table as table1");
        Query pq1 = new Query("SELECT table2.a AS kjfd, table2.b AS ddfw FROM table as table2");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");

        Set eSet = new HashSet();

        Set newSet = QueryOptimiser.merge(pt1, q1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeOkayGroupBy() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b from table as table1 WHERE table1.c = 'five' GROUP BY table1.a, table1.b HAVING table1.a = 'six'");
        Query pq1 = new Query("SELECT table2.a AS kjfd, table2.b AS ddfw FROM table as table2 WHERE table2.c = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Query eq1 = new Query("SELECT P42.kjfd AS t1_a, P42.ddfw AS t1_b FROM precomp1 AS P42 GROUP BY P42.kjfd, P42.ddfw HAVING P42.kjfd = 'six'");

        Set eSet = new HashSet();
        eSet.add(eq1);

        StringUtil.setNextUniqueNumber(42);
        Set newSet = QueryOptimiser.merge(pt1, q1, q1);

        assertEquals(eSet, newSet);
    }

    public void testMergeOkayOverlapping() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, table2.a AS t2_a, table2.b AS t2_b from table as table1, table as table2 WHERE table1.c = 'five' AND table2.c = 'five'");
        Query pq1 = new Query("SELECT table3.a AS kjfd, table3.b AS ddfw FROM table as table3 WHERE table3.c = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Query eq1 = new Query("SELECT P44.kjfd AS t1_a, P44.ddfw AS t1_b, P46.kjfd AS t2_a, P46.ddfw AS t2_b FROM precomp1 AS P44, precomp1 AS P46");
        Query eq2 = new Query("SELECT P49.a AS t1_a, P49.b AS t1_b, P50.kjfd AS t2_a, P50.ddfw AS t2_b FROM table AS P49, precomp1 AS P50 WHERE P49.c = 'five'");
        Query eq3 = new Query("SELECT P48.kjfd AS t1_a, P48.ddfw AS t1_b, table3.a AS t2_a, table3.b AS t2_b FROM precomp1 AS P48, table AS table3 WHERE table3.c = 'five'");

        Set eSet = new HashSet();
        eSet.add(eq1);
        eSet.add(eq2);
        eSet.add(eq3);

        StringUtil.setNextUniqueNumber(42);
        Set newSet = QueryOptimiser.merge(pt1, q1, q1);
/*
        String expected = "";
        Iterator expectedIter = eSet.iterator();
        boolean needComma = false;
        while (expectedIter.hasNext()) {
            expected += (needComma ? ", " : "");
            needComma = true;
            expected += ((Query) expectedIter.next()).getSQLString();
        }

        String got = "";
        Iterator gotIter = newSet.iterator();
        needComma = false;
        while (gotIter.hasNext()) {
            got += (needComma ? ", " : "");
            needComma = true;
            got += ((Query) gotIter.next()).getSQLString();
        }

        assertEquals(expected, got);*/
        assertEquals(eSet, newSet);
    }

    public void testMergeOkayNonOverlapping() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, table2.a AS t2_a, table2.b AS t2_b, table4.a AS t4_a from table as table1, table as table2, anothertable AS table4 WHERE table1.c = 'five' AND table2.c = 'five' AND table4.a = table1.a AND table4.a = table2.a");
        Query pq1 = new Query("SELECT table3.a AS kjfd, table3.b AS ddfw, table5.a AS fhds FROM table as table3, anothertable AS table5 WHERE table3.c = 'five' AND table3.a = table5.a");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Query eq1 = new Query("SELECT P46.kjfd AS t1_a, P46.ddfw AS t1_b, P45.a AS t2_a, P45.b AS t2_b, P46.fhds AS t4_a FROM precomp1 AS P46, table AS P45 WHERE P45.a = P46.fhds AND P45.c = 'five'");
        Query eq2 = new Query("SELECT table3.a AS t1_a, table3.b AS t1_b, P44.kjfd AS t2_a, P44.ddfw AS t2_b, P44.fhds AS t4_a FROM table AS table3, precomp1 AS P44 WHERE table3.a = P44.fhds AND table3.c = 'five'");

        Set eSet = new HashSet();
        eSet.add(eq1);
        eSet.add(eq2);

        StringUtil.setNextUniqueNumber(42);
        Set newSet = QueryOptimiser.merge(pt1, q1, q1);

        String expected = "";
        Iterator expectedIter = eSet.iterator();
        boolean needComma = false;
        while (expectedIter.hasNext()) {
            expected += (needComma ? ", " : "");
            needComma = true;
            expected += ((Query) expectedIter.next()).getSQLString();
        }

        String got = "";
        Iterator gotIter = newSet.iterator();
        needComma = false;
        while (gotIter.hasNext()) {
            got += (needComma ? ", " : "");
            needComma = true;
            got += ((Query) gotIter.next()).getSQLString();
        }

        //assertEquals(expected, got);
        System.out.println(expected);
        System.out.println(got);

        assertEquals(eSet, newSet);
    }
   /* 
    public void testMergeClashingAliases() throws Exception {
        Query q1 = new Query("SELECT P42.a AS t1_a, P42.b AS t1_b, table1.a AS t2_a from tab as P42, table as table1 WHERE table1.c = 'five'");
        Query pq1 = new Query("SELECT table2.a AS kjfd, table2.b AS ddfw FROM table as table2 WHERE table2.c = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Query eq1 = new Query("SELECT P42.a AS t1_a, P42.b AS t1_b, P42.kjfd AS t2_a FROM precomp1 AS P42, tab AS P42");

        Set eSet = new HashSet();
        eSet.add(eq1);

        StringUtil.setNextUniqueNumber(42);
        Set newSet = QueryOptimiser.merge(pt1, q1, q1);

        assertEquals(eSet, newSet);
    }
*/

    public void testMergeMultiple() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, table2.a AS t2_a, table2.b AS t2_b FROM table1, table2 WHERE table1.c = 'five' AND table2.c = 'six'");
        Query pq1 = new Query("SELECT table1.a AS fhjs, table1.b AS sjhf FROM table1 WHERE table1.c = 'five'");
        Query pq2 = new Query("SELECT table2.a AS kjsd, table2.b AS hjas FROM table2 WHERE table2.c = 'six'");
        Query pq3 = new Query("SELECT table2.a AS kjsd, table2.b AS hjas FROM table2 WHERE table2.c = 'seven'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        PrecomputedTable pt2 = new PrecomputedTable(pq2, "precomp2");
        PrecomputedTable pt3 = new PrecomputedTable(pq3, "precomp3");
        Set precomps = new HashSet();
        precomps.add(pt1);
        precomps.add(pt2);
        precomps.add(pt3);
        
        Query eq1 = new Query("SELECT P42.fhjs AS t1_a, P42.sjhf AS t1_b, table2.a AS t2_a, table2.b AS t2_b FROM precomp1 AS P42, table2 WHERE table2.c = 'six'");
        Query eq2 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, P43.kjsd AS t2_a, P43.hjas AS t2_b FROM table1, precomp2 AS P43 WHERE table1.c = 'five'");
        Map eMap = new HashMap();
        eMap.put(pt1, Collections.singleton(eq1));
        eMap.put(pt2, Collections.singleton(eq2));

        StringUtil.setNextUniqueNumber(42);
        SortedMap newMap = QueryOptimiser.mergeMultiple(precomps, q1, q1);

        assertEquals(eMap, newMap);
    }

    public void testRecursiveOptimise1() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, table2.a AS t2_a, table2.b AS t2_b FROM table1, table2 WHERE table1.c = 'five' AND table2.c = 'six'");
        Query pq1 = new Query("SELECT table1.a AS fhjs, table1.b AS sjhf FROM table1 WHERE table1.c = 'five'");
        Query pq2 = new Query("SELECT table2.a AS kjsd, table2.b AS hjas FROM table2 WHERE table2.c = 'six'");
        Query pq3 = new Query("SELECT table2.a AS kjsd, table2.b AS hjas FROM table2 WHERE table2.c = 'seven'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        PrecomputedTable pt2 = new PrecomputedTable(pq2, "precomp2");
        PrecomputedTable pt3 = new PrecomputedTable(pq3, "precomp3");
        Set precomps = new HashSet();
        precomps.add(pt1);
        precomps.add(pt2);
        precomps.add(pt3);
        
        Query eq1 = new Query("SELECT P42.fhjs AS t1_a, P42.sjhf AS t1_b, table2.a AS t2_a, table2.b AS t2_b FROM precomp1 AS P42, table2 WHERE table2.c = 'six'");
        Query eq2 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, P43.kjsd AS t2_a, P43.hjas AS t2_b FROM table1, precomp2 AS P43 WHERE table1.c = 'five'");
        Query eq3 = new Query("SELECT P44.fhjs AS t1_a, P44.sjhf AS t1_b, P43.kjsd AS t2_a, P43.hjas AS t2_b FROM precomp1 AS P44, precomp2 AS P43");
        Set eSet = new ConsistentSet();
        eSet.add(q1);
        eSet.add(eq1);
        eSet.add(eq2);
        eSet.add(eq3);

        StringUtil.setNextUniqueNumber(42);
        BestQueryStorer bestQuery = new BestQueryStorer();
        QueryOptimiser.recursiveOptimise(precomps, q1, bestQuery, q1);

        assertEquals(eSet, bestQuery.getQueries());
    }

    public void testRecursiveOptimise2() throws Exception {
        Query q1 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, table2.a AS t2_a, table2.b AS t2_b FROM table AS table1, table AS table2 WHERE table1.c = 'five' AND table2.c = 'five'");
        Query pq1 = new Query("SELECT table1.a AS fhjs, table1.b AS sjhf FROM table AS table1 WHERE table1.c = 'five'");
        PrecomputedTable pt1 = new PrecomputedTable(pq1, "precomp1");
        Set precomps = new HashSet();
        precomps.add(pt1);
        
        Query eq1 = new Query("SELECT P51.fhjs AS t1_a, P51.sjhf AS t1_b, P50.a AS t2_a, P50.b AS t2_b FROM precomp1 AS P51, table AS P50 WHERE P50.c = 'five'");
        Query eq2 = new Query("SELECT table1.a AS t1_a, table1.b AS t1_b, P49.fhjs AS t2_a, P49.sjhf AS t2_b FROM table AS table1, precomp1 AS P49 WHERE table1.c = 'five'");
        Query eq3 = new Query("SELECT P47.fhjs AS t1_a, P47.sjhf AS t1_b, P45.fhjs AS t2_a, P45.sjhf AS t2_b FROM precomp1 AS P47, precomp1 AS P45");
        Set eSet = new ConsistentSet();
        eSet.add(q1);
        eSet.add(eq1);
        eSet.add(eq2);
        eSet.add(eq3);

        StringUtil.setNextUniqueNumber(42);
        BestQueryStorer bestQuery = new BestQueryStorer();
        QueryOptimiser.recursiveOptimise(precomps, q1, bestQuery, q1);

        assertEquals(eSet, bestQuery.getQueries());
    }

    public void testRemapAliasesToAvoidPrecomputePrefix() throws Exception {
        Query q1 = new Query("SELECT table1.a, Putty.b FROM table1, table AS Putty");
        Query eq1 = new Query("SELECT table1.a, P42.b FROM table1, table AS P42");

        StringUtil.setNextUniqueNumber(42);
        QueryOptimiser.remapAliasesToAvoidPrecomputePrefix(q1);

        assertEquals(eq1, q1);
    }
}
