/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.reporting.sql.flowconfighistory;

import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
import org.apache.calcite.adapter.enumerable.PhysType;
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
import org.apache.calcite.linq4j.tree.Blocks;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.Primitive;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;

import java.util.List;

/**
 * Relational expression representing a query for bulletin information.
 *
 * <p>
 * Like any table scan, it serves as a leaf node of a query tree.
 * </p>
 */
public class FlowConfigHistoryTableScan extends TableScan implements EnumerableRel {
    final FlowConfigHistoryTable flowConfigHistoryTable;
    final int[] fields;

    protected FlowConfigHistoryTableScan(final RelOptCluster cluster, final RelOptTable table, final FlowConfigHistoryTable flowConfigHistoryTable, final int[] fields) {
        super(cluster, cluster.traitSetOf(EnumerableConvention.INSTANCE), table);

        this.flowConfigHistoryTable = flowConfigHistoryTable;
        this.fields = fields;
    }

    @Override
    public RelNode copy(final RelTraitSet traitSet, final List<RelNode> inputs) {
        return new FlowConfigHistoryTableScan(getCluster(), table, flowConfigHistoryTable, fields);
    }

    @Override
    public RelWriter explainTerms(final RelWriter pw) {
        return super.explainTerms(pw).item("fields", Primitive.asList(fields));
    }

    @Override
    public RelDataType deriveRowType() {
        final List<RelDataTypeField> fieldList = table.getRowType().getFieldList();
        final RelDataTypeFactory.Builder builder = getCluster().getTypeFactory().builder();
        for (int field : fields) {
            builder.add(fieldList.get(field));
        }
        return builder.build();
    }

    @Override
    public void register(RelOptPlanner planner) {
        planner.addRule(FlowConfigHistoryProjectTableScanRule.INSTANCE);
    }

    @Override
    public Result implement(EnumerableRelImplementor implementor, Prefer pref) {
        PhysType physType = PhysTypeImpl.of(implementor.getTypeFactory(), getRowType(), pref.preferArray());

        return implementor.result(physType, Blocks.toBlock(
            Expressions.call(table.getExpression(FlowConfigHistoryTable.class), "project", Expressions.constant(fields))));
    }

}
