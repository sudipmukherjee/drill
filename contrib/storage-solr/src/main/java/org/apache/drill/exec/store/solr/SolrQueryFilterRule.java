/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.exec.store.solr;

import java.util.List;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rex.RexNode;
import org.apache.drill.common.expression.LogicalExpression;
import org.apache.drill.exec.planner.logical.DrillAggregateRel;
import org.apache.drill.exec.planner.logical.DrillFilterRel;
import org.apache.drill.exec.planner.logical.DrillOptiq;
import org.apache.drill.exec.planner.logical.DrillParseContext;
import org.apache.drill.exec.planner.logical.DrillProjectRel;
import org.apache.drill.exec.planner.logical.DrillRel;
import org.apache.drill.exec.planner.logical.DrillScanRel;
import org.apache.drill.exec.planner.logical.DrillTable;
import org.apache.drill.exec.planner.logical.RelOptHelper;
import org.apache.drill.exec.planner.physical.PrelUtil;
import org.apache.drill.exec.planner.physical.PrelUtil.ProjectPushInfo;
import org.apache.drill.exec.store.StoragePluginOptimizerRule;
import org.apache.drill.exec.store.solr.schema.SolrSchemaPojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public abstract class SolrQueryFilterRule extends StoragePluginOptimizerRule {
  static final Logger logger = LoggerFactory
      .getLogger(SolrQueryFilterRule.class);

  public static final StoragePluginOptimizerRule FILTER_ON_SCAN = new SolrQueryFilterRule(
      RelOptHelper.some(DrillFilterRel.class,
          RelOptHelper.any(DrillScanRel.class)),
      "SolrQueryFilterRule:Filter_On_Scan") {
    @Override
    public void onMatch(RelOptRuleCall call) {
      final DrillFilterRel filterRel = (DrillFilterRel) call.rel(0);
      final DrillScanRel scan = (DrillScanRel) call.rel(1);
      doOnMatch(call, filterRel, null, scan);

    }

    @Override
    public boolean matches(RelOptRuleCall call) {
      final DrillScanRel scan = (DrillScanRel) call.rel(1);
      if (scan.getGroupScan() instanceof SolrGroupScan) {
        return super.matches(call);
      }
      return false;
    }
  };

  public static final StoragePluginOptimizerRule FILTER_ON_PROJECT = new SolrQueryFilterRule(
      RelOptHelper.some(DrillProjectRel.class,
          RelOptHelper.any(DrillScanRel.class)),
      "StoragePluginOptimizerRule:Filter_On_Project") {
    @Override
    public void onMatch(RelOptRuleCall call) {
      logger.debug("SolrQueryFilterRule :: onMatch:: Filter_On_Project");
      final Project proj = (Project) call.rel(0);
      final TableScan scan = (TableScan) call.rel(1);

      try {
        ProjectPushInfo columnInfo = PrelUtil.getColumns(scan.getRowType(),
            proj.getProjects());
        if (columnInfo == null || columnInfo.isStarQuery() //
            || !scan.getTable().unwrap(DrillTable.class) //
                .getGroupScan().canPushdownProjects(columnInfo.columns)) {
          return;
        }
        SolrGroupScan solrGroupScan = (SolrGroupScan) scan.getTable()
            .unwrap(DrillTable.class).getGroupScan();
        solrGroupScan.setColumns(columnInfo.columns);
        // DrillRel inputRel = null;
        //
        //
        // final DrillScanRel newScan = new DrillScanRel(scan.getCluster(), scan
        // .getTraitSet().plus(DrillRel.DRILL_LOGICAL), scan.getTable(),
        // solrGroupScan, columnInfo.createNewRowType(proj.getInput()
        // .getCluster().getTypeFactory()), columnInfo.columns);
        //
        // List<RexNode> newProjects = Lists.newArrayList();
        // for (RexNode n : proj.getChildExps()) {
        // newProjects.add(n.accept(columnInfo.getInputRewriter()));
        // }
        //
        // final DrillProjectRel newProj = DrillProjectRel.create(
        // proj.getCluster(), proj.getTraitSet().plus(DrillRel.DRILL_LOGICAL),
        // newScan, newProjects, proj.getRowType());
        //
        // inputRel = newProj != null ? newProj : newScan;
        // if (inputRel != null) {
        // call.transformTo(inputRel);
        // }
      } catch (Exception e) {

      }

    }

    @Override
    public boolean matches(RelOptRuleCall call) {
      final DrillScanRel scan = (DrillScanRel) call.rel(1);
      if (scan.getGroupScan() instanceof SolrGroupScan) {
        return super.matches(call);
      }
      return false;
    }
  };
  /*
   * Move this class out
   */
  public static final StoragePluginOptimizerRule AGG_PUSH_DOWN = new SolrQueryFilterRule(
      RelOptHelper.some(
          DrillAggregateRel.class,
          RelOptHelper.some(DrillProjectRel.class,
              RelOptHelper.any(DrillScanRel.class))),
      "StoragePluginOptimizerRule:AGG_PUSH_DOWN") {

    @Override
    public void onMatch(RelOptRuleCall call) {
      logger.debug("SolrQueryFilterRule :: onMatch :: Agg_Push_Down");
      final DrillAggregateRel aggrRel = (DrillAggregateRel) call.rel(0);
      final DrillProjectRel projectRel = (DrillProjectRel) call.rel(1);
      final DrillScanRel scanRel = (DrillScanRel) call.rel(2);

      // optimize the solr query for different funcType
      doOnAggrMatch(call, aggrRel, projectRel, scanRel);
    }

    @Override
    public boolean matches(RelOptRuleCall call) {
      final DrillScanRel scan = (DrillScanRel) call.rel(2);
      if (scan.getGroupScan() instanceof SolrGroupScan) {
        return super.matches(call);
      }
      return false;
    }

    protected void doOnAggrMatch(RelOptRuleCall call,
        DrillAggregateRel aggrRel, DrillProjectRel projectRel,
        DrillScanRel scanRel) {

      DrillRel inputRel = projectRel != null ? projectRel : scanRel;
      SolrGroupScan solrGroupScan = (SolrGroupScan) scanRel.getGroupScan();
      List<AggregateCall> aggrCallList = aggrRel.getAggCallList();
      List<String> aggrFields = aggrRel.getInput().getRowType().getFieldNames();

      SolrScanSpec solrScanSpec = solrGroupScan.getSolrScanSpec();

      String solrCoreName = solrGroupScan.getSolrScanSpec().getSolrCoreName();
      List<SolrAggrParam> aggrParamLst = solrScanSpec.getAggrParams();
      SolrSchemaPojo cvSchema = solrScanSpec.getCvSchema();
      SolrFilterParam filters = solrScanSpec.getFilter();
      SolrScanSpec newScanSpec = null;
      for (AggregateCall aggrCall : aggrCallList) {
        LogicalExpression logicalExp = DrillAggregateRel.toDrill(aggrCall,
            aggrFields, null);

        SolrAggrQueryBuilder sAggrBuilder = new SolrAggrQueryBuilder(
            solrGroupScan, logicalExp);
        newScanSpec = sAggrBuilder.parseTree();
        if (newScanSpec == null)
          return;

        newScanSpec.setAggregateQuery(true);
        SolrGroupScan newGroupScan = new SolrGroupScan(
            solrGroupScan.getUserName(), solrGroupScan.getSolrPlugin(),
            newScanSpec, solrGroupScan.getColumns());

        DrillScanRel newScanRel = new DrillScanRel(scanRel.getCluster(),
            scanRel.getTraitSet().plus(DrillRel.DRILL_LOGICAL),
            scanRel.getTable(), newGroupScan, scanRel.getRowType(),
            scanRel.getColumns());

        inputRel = newScanRel;
        call.transformTo(aggrRel.copy(aggrRel.getTraitSet(),
            ImmutableList.of((RelNode) inputRel)));
      }

    }
  };

  public SolrQueryFilterRule(RelOptRuleOperand operand, String description) {
    super(operand, description);
    logger.debug("SolrQueryFilterRule :: contructor");
  }

  protected void doOnMatch(RelOptRuleCall call, DrillFilterRel filterRel,
      DrillProjectRel projectRel, DrillScanRel scanRel) {

    DrillRel inputRel = projectRel != null ? projectRel : scanRel;
    SolrGroupScan solrGroupScan = (SolrGroupScan) scanRel.getGroupScan();
    final RexNode condition = filterRel.getCondition();

    LogicalExpression conditionExp = DrillOptiq.toDrill(new DrillParseContext(
        PrelUtil.getPlannerSettings(call.getPlanner())), scanRel, condition);

    SolrQueryBuilder sQueryBuilder = new SolrQueryBuilder(solrGroupScan,
        conditionExp);
    SolrScanSpec newScanSpec = sQueryBuilder.parseTree();
    if (newScanSpec == null)
      return;
    SolrGroupScan newGroupScan = new SolrGroupScan(solrGroupScan.getUserName(),
        solrGroupScan.getSolrPlugin(), newScanSpec, solrGroupScan.getColumns());

    DrillScanRel newScanRel = new DrillScanRel(scanRel.getCluster(), scanRel
        .getTraitSet().plus(DrillRel.DRILL_LOGICAL), scanRel.getTable(),
        newGroupScan, scanRel.getRowType(), scanRel.getColumns());
    if (projectRel != null) {
      DrillProjectRel newProjectRel = DrillProjectRel.create(
          projectRel.getCluster(), projectRel.getTraitSet(), newScanRel,
          projectRel.getProjects(), projectRel.getRowType());
      inputRel = newProjectRel;
    } else {
      inputRel = newScanRel;
    }

    if (sQueryBuilder.isAllExpressionsConverted()) {
      logger.debug("all expressions converted.. ");
      call.transformTo(inputRel);
    } else {
      call.transformTo(filterRel.copy(filterRel.getTraitSet(),
          ImmutableList.of((RelNode) inputRel)));
    }
  }

}