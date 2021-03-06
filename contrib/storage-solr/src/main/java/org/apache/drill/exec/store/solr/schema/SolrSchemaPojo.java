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
package org.apache.drill.exec.store.solr.schema;

import java.util.ArrayList;
import java.util.List;

public class SolrSchemaPojo {
  protected List<SolrSchemaField> schemaFields = null;

  protected List<SolrSchemaField> dynSchemaFields = null;

  protected List<String> fieldTypes = null;

  protected String uniqueKey = null;

  protected String defaultSearchField = null;

  public SolrSchemaPojo() {
  }

  public List<SolrSchemaField> getSchemaFields(boolean init) {
    if (init) {
      return getSchemaFields();
    } else {
      return this.schemaFields;
    }
  }

  public List<SolrSchemaField> getSchemaFields() {
    if (schemaFields == null) {
      this.schemaFields = new ArrayList<SolrSchemaField>();
    }
    return this.schemaFields;
  }

  public void setSchemaFields(List<SolrSchemaField> schemaFields) {
    this.schemaFields = schemaFields;

  }

  public List<SolrSchemaField> getDynSchemaFields(boolean init) {
    if (init) {
      return getDynSchemaFields();
    } else {
      return this.dynSchemaFields;
    }
  }

  public List<SolrSchemaField> getDynSchemaFields() {
    if (dynSchemaFields == null) {
      this.dynSchemaFields = new ArrayList<SolrSchemaField>();
    }
    return this.dynSchemaFields;
  }

  public void setDynSchemaFields(List<SolrSchemaField> dynSchemaFields) {
    this.dynSchemaFields = dynSchemaFields;

  }

  public List<String> getFieldTypes(boolean init) {
    if (init) {
      return getFieldTypes();
    } else {
      return this.fieldTypes;
    }
  }

  public List<String> getFieldTypes() {
    if (fieldTypes == null) {
      this.fieldTypes = new ArrayList<String>();
    }
    return this.fieldTypes;
  }

  public void setFieldTypes(List<String> fieldTypes) {
    this.fieldTypes = fieldTypes;

  }

  public String getUniqueKey(boolean init) {
    if (init) {
      return getUniqueKey();
    } else {
      return this.uniqueKey;
    }
  }

  public String getUniqueKey() {
    if (uniqueKey == null) {
      this.uniqueKey = "";
    }
    return this.uniqueKey;
  }

  public void setUniqueKey(String uniqueKey) {
    this.uniqueKey = uniqueKey;

  }

  public String getDefaultSearchField(boolean init) {
    if (init) {
      return getDefaultSearchField();
    } else {
      return this.defaultSearchField;
    }
  }

  public String getDefaultSearchField() {
    if (defaultSearchField == null) {
      this.defaultSearchField = "";
    }
    return this.defaultSearchField;
  }

  public void setDefaultSearchField(String defaultSearchField) {
    this.defaultSearchField = defaultSearchField;

  }

  public static List<String> getAllFields() {
    ArrayList<String> list = new ArrayList<String>();
    list.add(Fields.SCHEMA_FIELDS);
    list.add(Fields.DYN_SCHEMA_FIELDS);
    list.add(Fields.FIELD_TYPES);
    list.add(Fields.UNIQUE_KEY);
    list.add(Fields.DEFAULT_SEARCH_FIELD);
    list.add(Fields.ERROR_OBJ);
    return list;
  }

  public static interface Fields {
    String SCHEMA_FIELDS = "schemaFields";
    String DYN_SCHEMA_FIELDS = "dynSchemaFields";
    String FIELD_TYPES = "fieldTypes";
    String UNIQUE_KEY = "uniqueKey";
    String DEFAULT_SEARCH_FIELD = "defaultSearchField";
    String ERROR_OBJ = "errorObj";
  }

}
