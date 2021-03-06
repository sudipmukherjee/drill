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

import java.text.MessageFormat;
import java.util.List;

import org.apache.drill.common.expression.BooleanOperator;
import org.apache.drill.common.expression.FunctionCall;
import org.apache.drill.common.expression.FunctionHolderExpression;
import org.apache.drill.common.expression.LogicalExpression;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.expression.visitors.AbstractExprVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
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
    logger.debug("SolrQueryBuilder :: constructor");
  }

  public SolrScanSpec parseTree() {
    logger.debug("SolrQueryBuilder :: parseTree");
    SolrScanSpec parsedSpec = le.accept(this, null);
    if (parsedSpec != null) {
      parsedSpec = mergeScanSpecs("booleanAnd",
          this.groupScan.getSolrScanSpec(), parsedSpec);
    }
    return parsedSpec;
  }

  public SolrScanSpec mergeScanSpecs(String functionName,
      SolrScanSpec leftScanSpec, SolrScanSpec rightScanSpec) {
    SolrFilterParam solrFilter = new SolrFilterParam();
    logger.info("mergeScanSpecs : init");
    switch (functionName) {
    case "booleanAnd":
      if (leftScanSpec.getFilter() != null && rightScanSpec.getFilter() != null) {
        solrFilter.add(Joiner.on("").join(leftScanSpec.getFilter()));
        solrFilter.add(" AND ");
        solrFilter.add(Joiner.on("").join(rightScanSpec.getFilter()));

      } else if (leftScanSpec.getFilter() != null) {
        solrFilter = leftScanSpec.getFilter();
      } else {
        solrFilter = rightScanSpec.getFilter();
      }
      break;
    case "booleanOr":
      solrFilter.add(Joiner.on("").join(leftScanSpec.getFilter()));
      solrFilter.add(" OR ");
      solrFilter.add(Joiner.on("").join(rightScanSpec.getFilter()));
    }
    SolrScanSpec solrScanSpec = new SolrScanSpec(groupScan.getSolrScanSpec()
        .getSolrCoreName(), solrFilter);
    solrScanSpec.setCvSchema(this.groupScan.getSolrScanSpec().getCvSchema());

    return solrScanSpec;
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
    logger.debug("SolrQueryBuilder :: visitBooleanOperator");
    List<LogicalExpression> args = op.args;
    String functionName = op.getName();
    SolrScanSpec nodeScanSpec = null;
    logger.debug("functionName :: " + functionName);
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
    return nodeScanSpec;
  }

  @Override
  public SolrScanSpec visitFunctionCall(FunctionCall call, Void valueArg)
      throws RuntimeException {
    logger.debug("SolrQueryBuilder :: visitFunctionCall");
    SolrScanSpec nodeScanSpec = null;
    String functionName = call.getName();
    ImmutableList<LogicalExpression> args = call.args;
    LogicalExpression nameVal = call.args.get(0);
    LogicalExpression valueVal = null;
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

    if (nodeScanSpec == null) {
      allExpressionsConverted = false;
    }
    return nodeScanSpec;

  }

  public SolrScanSpec createSolrScanSpec(String functionName, SchemaPath field,
      Object fieldValue) {
    // extract the field name
    String fieldName = field.getAsUnescapedPath();

    String operator = null;
    switch (functionName) {
    case "equal":
      operator = ":{0}";
      break;
    case "not_equal":
      break;
    case "greater_than_or_equal_to":
      operator = "[{0} TO *]";
      break;
    case "greater_than":

    case "less_than_or_equal_to":

    case "less_than":

    case "isnull":
    case "isNull":
    case "is null":

    case "isnotnull":
    case "isNotNull":
    case "is not null":
    default:
      allExpressionsConverted = false;
      break;
    }
    if (operator != null) {
      fieldValue = MessageFormat.format(operator, fieldValue.toString());
      SolrFilterParam filterParam = new SolrFilterParam(fieldName,
          fieldValue.toString());

      SolrScanSpec solrScanSpec = new SolrScanSpec(this.groupScan
          .getSolrScanSpec().getSolrCoreName(), filterParam);
      solrScanSpec.setCvSchema(this.groupScan.getSolrScanSpec().getCvSchema());
      return solrScanSpec;
    }
    logger.debug("createSolrScanSpec :: fieldName " + fieldName
        + " :: functionName " + functionName);
    SolrScanSpec solrScanSpec = new SolrScanSpec(this.groupScan
        .getSolrScanSpec().getSolrCoreName());
    solrScanSpec.setCvSchema(this.groupScan.getSolrScanSpec().getCvSchema());
    return solrScanSpec;
  }
}
