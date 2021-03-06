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

import com.fasterxml.jackson.annotation.JsonProperty;

public class SolrStorageProperties {
  @JsonProperty
  private String solrSchemaUrl = "";
  @JsonProperty
  private boolean createViews = false;
  @JsonProperty
  private int maxRowSize = 0;
  @JsonProperty
  private boolean useSolrStream = false;

  public void setMaxRowSize(int maxRowSize) {
    this.maxRowSize = maxRowSize;
  }

  public boolean isCreateViews() {
    return createViews;
  }

  public void setCreateViews(boolean createViews) {
    this.createViews = createViews;
  }

  public String getSolrSchemaUrl() {
    return solrSchemaUrl;
  }

  public void setSolrSchemaUrl(String solrSchemaUrl) {
    this.solrSchemaUrl = solrSchemaUrl;
  }

  public boolean isUseSolrStream() {
    return useSolrStream;
  }

  public void setUseSolrStream(boolean useSolrStream) {
    this.useSolrStream = useSolrStream;
  }

}
