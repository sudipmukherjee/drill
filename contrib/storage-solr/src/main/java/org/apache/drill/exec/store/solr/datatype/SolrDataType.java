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
package org.apache.drill.exec.store.solr.datatype;

import java.util.List;

import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.drill.exec.store.RecordDataType;
import org.apache.drill.exec.store.solr.schema.SolrSchemaPojo;
import org.apache.drill.exec.store.solr.schema.SolrSchemaField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class SolrDataType extends RecordDataType {
  private final List<SqlTypeName> types = Lists.newArrayList();
  private final List<String> names = Lists.newArrayList();
  private final SolrSchemaPojo cvSchema;
  static final Logger logger = LoggerFactory.getLogger(SolrDataType.class);

  public SolrDataType(SolrSchemaPojo cvSchema) {
    this.cvSchema = cvSchema;
    for (SolrSchemaField cvSchemaField : cvSchema.getSchemaFields()) {
      if (!cvSchemaField.isSkipdelete()) {// not
                                          // adding
                                          // cv
                                          // fields.
        names.add(cvSchemaField.getFieldName());
        String solrFieldType = cvSchemaField.getType();
        if (solrFieldType.equals("string")
            || solrFieldType.equals("commaDelimited")
            || solrFieldType.equals("text_general")
            || solrFieldType.equals("currency") || solrFieldType.equals("uuid")) {
          types.add(SqlTypeName.VARCHAR);
        } else if (solrFieldType.equals("int") || solrFieldType.equals("tint")
            || solrFieldType.equals("pint")) {
          types.add(SqlTypeName.INTEGER);
        } else if (solrFieldType.equals("boolean")) {
          types.add(SqlTypeName.BOOLEAN);
        } else if (solrFieldType.equals("double")
            || solrFieldType.equals("pdouble")
            || solrFieldType.equals("tdouble") || solrFieldType.equals("tlong")
            || solrFieldType.equals("rounded1024")
            || solrFieldType.equals("long")) {
          types.add(SqlTypeName.DOUBLE);
        } else if (solrFieldType.equals("date")
            || solrFieldType.equals("tdate")
            || solrFieldType.equals("timestamp")) {
          types.add(SqlTypeName.TIMESTAMP);
        } else if (solrFieldType.equals("float")
            || solrFieldType.equals("tfloat")) {
          types.add(SqlTypeName.DECIMAL);
        } else {
          logger
              .trace(String
                  .format(
                      "PojoDataType doesn't yet support conversions from type [%s] for field [%s].Defaulting to varchar.",
                      solrFieldType, cvSchemaField.getFieldName()));
          types.add(SqlTypeName.VARCHAR);
        }
      }

    }
  }

  @Override
  public List<SqlTypeName> getFieldSqlTypeNames() {
    return types;
  }

  @Override
  public List<String> getFieldNames() {
    return names;
  }

}
