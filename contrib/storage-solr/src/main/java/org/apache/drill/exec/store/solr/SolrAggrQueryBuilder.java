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

import org.apache.drill.common.expression.BooleanOperator;
import org.apache.drill.common.expression.FunctionCall;
import org.apache.drill.common.expression.FunctionHolderExpression;
import org.apache.drill.common.expression.LogicalExpression;
import org.apache.drill.common.expression.visitors.AbstractExprVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrAggrQueryBuilder extends
    AbstractExprVisitor<SolrAggrParam, Void, RuntimeException> {

  static final Logger logger = LoggerFactory.getLogger(SolrQueryBuilder.class);
  final SolrGroupScan groupScan;
  final LogicalExpression le;
  private boolean allExpressionsConverted = true;

  public SolrAggrQueryBuilder(SolrGroupScan solrGroupScan,
      LogicalExpression conditionExp) {
    this.groupScan = solrGroupScan;
    this.le = conditionExp;
    logger.debug("SolrAggrQueryBuilder :: constructor");
  }

  public SolrAggrParam parseTree() {
    logger.debug("SolrQueryBuilder :: parseTree");
    SolrAggrParam parsedSpec = le.accept(this, null);

    return parsedSpec;
  }

  @Override
  public SolrAggrParam visitUnknown(LogicalExpression e, Void valueArg)
      throws RuntimeException {
    logger.info("SolrQueryBuilder :: visitUnknown");
    allExpressionsConverted = false;
    return null;
  }

  public boolean isAllExpressionsConverted() {
    return allExpressionsConverted;
  }

  @Override
  public SolrAggrParam visitFunctionHolderExpression(
      FunctionHolderExpression fhe, Void valueArg) {
    logger.info("SolrQueryBuilder :: visitFunctionHolderExpression");
    allExpressionsConverted = false;
    return null;

  }

  @Override
  public SolrAggrParam visitBooleanOperator(BooleanOperator op, Void valueArg) {
    return null;
  }

  @Override
  public SolrAggrParam visitFunctionCall(FunctionCall call, Void valueArg)
      throws RuntimeException {
    
    SolrAggrFunctionProcessor evaluator = SolrAggrFunctionProcessor
        .process(call);

    logger.debug("SolrQueryBuilder :: visitFunctionCall"+" evaluator isSuccess : "+evaluator.isSuccess()+ " func "+evaluator.getFunctionName() + " path "+evaluator.getPath());
    if (evaluator.isSuccess() && evaluator.getPath() != null) {
      SolrAggrParam solrAggrParam = new SolrAggrParam();
      solrAggrParam.setFieldName(evaluator.getPath().toString()
          .replaceAll("`", ""));
      solrAggrParam.setFunctionName(evaluator.getFunctionName());
      // List<SolrAggrParam> aggrParams = this.groupScan.getSolrScanSpec()
      // .getAggrParams() != null ? this.groupScan.getSolrScanSpec()
      // .getAggrParams() : new ArrayList<SolrAggrParam>();
      // aggrParams.add(solrAggrParam);
      // logger.debug("aggr func name "+evaluator.getFunctionName());
      // SolrScanSpec solrScanSpec = this.groupScan.getSolrScanSpec();
      // solrScanSpec.setAggregateQuery(true);
      // solrScanSpec.setAggrParams(aggrParams);
      // allExpressionsConverted = true;

      return solrAggrParam;
    } else {
      allExpressionsConverted = false;
    }
    return null;
  }
}
