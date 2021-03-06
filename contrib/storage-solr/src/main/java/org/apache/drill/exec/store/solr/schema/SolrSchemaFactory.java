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

import java.io.IOException;
import java.util.List;

import org.apache.calcite.schema.SchemaPlus;
import org.apache.drill.exec.store.SchemaConfig;
import org.apache.drill.exec.store.SchemaFactory;
import org.apache.drill.exec.store.solr.SolrStoragePlugin;
import org.apache.drill.exec.store.solr.SolrStoragePluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class SolrSchemaFactory implements SchemaFactory {
  static final Logger logger = LoggerFactory.getLogger(SolrSchemaFactory.class);

  private final SolrStoragePlugin solrStorage;
  private final String storageName;

  public SolrSchemaFactory(SolrStoragePlugin solrStorage, String storageName) {
    this.solrStorage = solrStorage;
    this.storageName = storageName;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.apache.drill.exec.store.SchemaFactory#registerSchemas(org.apache.drill
   * .exec.store.SchemaConfig, org.apache.calcite.schema.SchemaPlus)
   */
  @Override
  public void registerSchemas(SchemaConfig schemaConfig, SchemaPlus parent)
      throws IOException {
    List<String> schemaPath = Lists.newArrayList();
    schemaPath.add(SolrStoragePluginConfig.NAME);
    SolrSchema schema = new SolrSchema(schemaPath, "root", solrStorage);
    SchemaPlus hPlus = parent.add(this.storageName, schema);

  }

}
