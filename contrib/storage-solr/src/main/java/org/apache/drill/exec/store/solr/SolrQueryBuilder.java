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
import java.util.Queue;

import org.apache.drill.common.expression.BooleanOperator;
import org.apache.drill.common.expression.FunctionCall;
import org.apache.drill.common.expression.FunctionHolderExpression;
import org.apache.drill.common.expression.LogicalExpression;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.expression.visitors.AbstractExprVisitor;
import org.apache.drill.exec.store.solr.SolrScanSpec.SolrFilters;
import org.apache.solr.client.solrj.SolrQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.ImmutableList;

public class SolrQueryBuilder extends
    AbstractExprVisitor<SolrScanSpec, Void, RuntimeException> {
  static final Logger logger = LoggerFactory.getLogger(SolrQueryBuilder.class);
  final SolrGroupScan groupScan;
  final LogicalExpression le;
  private boolean allExpressionsConverted = true;

  public SolrQueryBuilder(SolrGroupScan solrGroupScan,
      LogicalExpression conditionExp) {
    this.groupScan = solrGroupScan;
    this.le = conditionExp;
    logger.info("SolrQueryBuilder :: constructor");
  }

  public SolrScanSpec parseTree() {
    logger.info("SolrQueryBuilder :: parseTree");
    SolrScanSpec parsedSpec = le.accept(this, null);
    if (parsedSpec != null) {
      logger.info("parsedSpec : " + parsedSpec);
      parsedSpec = mergeScanSpecs("booleanAnd",
          this.groupScan.getSolrScanSpec(), parsedSpec);
    }
    return parsedSpec;
  }

  public SolrScanSpec mergeScanSpecs(String functionName,
      SolrScanSpec leftScanSpec, SolrScanSpec rightScanSpec) {
    List<SolrFilters> solrFilterList;
    logger.info("mergeScanSpecs : init");
    switch (functionName) {
    case "booleanAnd":
      if (leftScanSpec.getFilter() != null && rightScanSpec.getFilter() != null) {
        logger.info("mergeScanSpecs : 1");
        solrFilterList = leftScanSpec.getFilter();
      } else if (leftScanSpec.getFilter() != null) {
        logger.info("mergeScanSpecs : 2");
      } else {
        logger.info("mergeScanSpecs : 3");
      }
      break;
    case "booleanOr":

    }
    return new SolrScanSpec(groupScan.getSolrScanSpec().getSolrCoreName());
  }

  @Override
  public SolrScanSpec visitUnknown(LogicalExpression e, Void valueArg)
      throws RuntimeException {
    logger.info("SolrQueryBuilder :: visitUnknown");
    allExpressionsConverted = false;
    return null;
  }

  public boolean isAllExpressionsConverted() {
    return allExpressionsConverted;
  }

  @Override
  public SolrScanSpec visitFunctionHolderExpression(
      FunctionHolderExpression fhe, Void valueArg) {
    logger.info("SolrQueryBuilder :: visitFunctionHolderExpression");

    return null;

  }

  @Override
  public SolrScanSpec visitBooleanOperator(BooleanOperator op, Void valueArg) {
    logger.info("SolrQueryBuilder :: visitBooleanOperator");
    List<LogicalExpression> args = op.args;
    String functionName = op.getName();
    SolrScanSpec nodeScanSpec = null;
    logger.info("functionName :: " + functionName);
    for (int i = 0; i < args.size(); ++i) {
      logger.info(" args " + args.get(i));
      switch (functionName) {
      case "booleanAnd":
      case "booleanOr":
        if (nodeScanSpec == null) {
          nodeScanSpec = args.get(i).accept(this, valueArg);
        } else {
          SolrScanSpec scanSpec = args.get(i).accept(this, valueArg);
          if (scanSpec != null) {
            nodeScanSpec = mergeScanSpecs(functionName, nodeScanSpec, scanSpec);
          } else {
            allExpressionsConverted = false;
          }
        }
        logger.info(" expression converted!");
        break;
      }
    }
    logger.info("nodeScanSpec :: " + nodeScanSpec);
    return nodeScanSpec;
  }

  @Override
  public SolrScanSpec visitFunctionCall(FunctionCall call, Void valueArg)
      throws RuntimeException {
    logger.info("SolrQueryBuilder :: visitFunctionCall");
    SolrScanSpec nodeScanSpec = null;
    String functionName = call.getName();
    ImmutableList<LogicalExpression> args = call.args;
    LogicalExpression nameVal = call.args.get(0);
    LogicalExpression valueVal = null;
    StringBuilder strBuilder = new StringBuilder();
    if (call.args.size() >= 2) {
      valueVal = call.args.get(1);
    }
    if (SolrCompareFunctionProcessor.isCompareFunction(functionName)) {
      SolrCompareFunctionProcessor evaluator = SolrCompareFunctionProcessor
          .process(call);
      if (evaluator.isSuccess()) {
        try {
          nodeScanSpec = createSolrScanSpec(evaluator.getFunctionName(),
              evaluator.getPath(), evaluator.getValue());

        } catch (Exception e) {
          logger.debug("Failed to create filters ", e);
        }
      }
    } else {
      switch (functionName) {
      case "booleanAnd":
      case "booleanOr":
        SolrScanSpec leftScanSpec = args.get(0).accept(this, null);
        SolrScanSpec rightScanSpec = args.get(1).accept(this, null);
        if (leftScanSpec != null && rightScanSpec != null) {
          nodeScanSpec = mergeScanSpecs(functionName, leftScanSpec,
              rightScanSpec);
        } else {
          allExpressionsConverted = false;
          if ("booleanAnd".equals(functionName)) {
            nodeScanSpec = leftScanSpec == null ? rightScanSpec : leftScanSpec;
          }
        }
        break;
      }
    }
    logger.info("functionName:" + functionName);
    logger.info("Name Val:" + nameVal.toString());
    logger.info("Value Val:" + valueVal.toString());

    if (nodeScanSpec == null) {
      allExpressionsConverted = false;
    }

    return nodeScanSpec;

  }

  public SolrScanSpec createSolrScanSpec(String functionName, SchemaPath field,
      Object fieldValue) {
    // extract the field name
    String fieldName = field.getAsUnescapedPath();
    List<SolrFilters> solrFilters=Lists.newArrayList();
    SolrFilters solrFilter = new SolrFilters();
    switch (functionName) {
    case "equal":
      break;
    case "not_equal":
      break;
    case "greater_than_or_equal_to":
      break;
    case "greater_than":
      break;
    case "less_than_or_equal_to":
      break;
    case "less_than":
      break;
    case "isnull":
    case "isNull":
    case "is null":
      break;
    case "isnotnull":
    case "isNotNull":
    case "is not null":
      break;
    }
    logger.info("createSolrScanSpec :: fieldName " + fieldName
        + " :: functionName " + functionName);
    return new SolrScanSpec(this.groupScan.getSolrScanSpec().getSolrCoreName());
  }
}