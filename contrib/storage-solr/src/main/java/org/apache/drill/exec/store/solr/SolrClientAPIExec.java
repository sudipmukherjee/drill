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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.drill.exec.store.solr.schema.SolrSchemaPojo;
import org.apache.drill.exec.store.solr.schema.SolrSchemaField;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.io.stream.SolrStream;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;
import org.apache.solr.common.params.GroupParams;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class SolrClientAPIExec {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
      .getLogger(SolrClientAPIExec.class);
  private SolrClient solrClient;

  public SolrClient getSolrClient() {
    return solrClient;
  }

  public void setSolrClient(SolrClient solrClient) {
    this.solrClient = solrClient;
  }

  public SolrClientAPIExec(SolrClient solrClient) {
    this.solrClient = solrClient;
  }

  public SolrClientAPIExec() {

  }

  public Set<String> getSolrCoreList() {
    // Request core list
    logger.debug("getting cores from solr..");
    CoreAdminRequest request = new CoreAdminRequest();
    request.setAction(CoreAdminAction.STATUS);
    Set<String> coreList = null;
    try {
      CoreAdminResponse cores = request.process(solrClient);
      coreList = new HashSet<String>(cores.getCoreStatus().size());
      for (int i = 0; i < cores.getCoreStatus().size(); i++) {
        String coreName = cores.getCoreStatus().getName(i);
        coreList.add(coreName);
      }
    } catch (SolrServerException | IOException e) {
      logger.info("error getting core info from solr server...");
    }
    return coreList;
  }

  public SolrSchemaPojo getSchemaForCore(String coreName, String solrServerUrl,
      String schemaUrl) {
    //coreName = coreName.replaceAll("`", "");
    // schemaUrl = MessageFormat.format(schemaUrl,solrServerUrl, coreName);
    schemaUrl = MessageFormat.format(schemaUrl, coreName);
    logger.debug("getting schema information from :: " + schemaUrl);
    HttpClient client = HttpClientBuilder.create().build();
    HttpGet request = new HttpGet(schemaUrl);
    SolrSchemaPojo oCVSchema = null;
    request.setHeader("Content-Type", "application/json");
    try {
      HttpResponse response = client.execute(request);
      BufferedReader rd = new BufferedReader(new InputStreamReader(response
          .getEntity().getContent()));
      StringBuffer result = new StringBuffer();
      String line = "";
      while ((line = rd.readLine()) != null) {
        result.append(line);
      }
      ObjectMapper mapper = new ObjectMapper();
      mapper
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      oCVSchema = mapper.readValue(result.toString(), SolrSchemaPojo.class);
    } catch (Exception e) {
      logger.info("exception occured while fetching schema details..."
          + e.getMessage());
    }
    return oCVSchema;
  }

  public QueryResponse getSolrDocs(String solrServer, String solrCoreName,
      String uniqueKey, List<String> fields, Integer solrDocFectCount,
      StringBuilder filters, List<SolrAggrParam> solrAggrParams, boolean isGroup) {
    //solrCoreName = solrCoreName.replaceAll("`", "");
    SolrClient solrClient = new HttpSolrClient(solrServer + solrCoreName);
    String fieldStr = null;
    String[] fieldListArr = null;
    List<String> statsFieldList = Lists.newArrayList();
    
    SolrQuery solrQuery = new SolrQuery().setTermsRegexFlag("case_insensitive")
        .setQuery(uniqueKey + ":*").setRows(0);

    if (filters.length() > 0) {
      solrQuery.setParam("fq", filters.toString());
      logger.debug("filter query [ " + filters.toString() + " ]");
    }

    if (fields != null && !fields.isEmpty()) {
      fieldStr = Joiner.on(",").join(fields);
      solrQuery.setParam("fl", fieldStr);
      solrQuery.setRows(solrDocFectCount);
      logger.debug("response field list [" + fieldStr + " ]");
    }
    if (solrAggrParams != null && !solrAggrParams.isEmpty()) {
      if (isGroup) {
        solrQuery.setGetFieldStatistics(true);        
        for (SolrAggrParam solrAggrParam : solrAggrParams) {
          String statsField = solrAggrParam.getFieldName();
          if (!statsFieldList.contains(statsField)) {
            statsFieldList.add(statsField);
          }
          fields.remove(statsField);
        }
        if (!fields.isEmpty()) {
          fieldListArr = fields.toArray(new String[fields.size()]);          
        }
      }
      for (String statsField : statsFieldList) {        
        solrQuery.setGetFieldStatistics(statsField);
        logger.debug(" adding stats field parameter.. [ " + statsField + " ]");
        if (isGroup && fieldListArr != null) {
          logger.debug(" adding stats facet parameters.. [ " + fields + " ]");
          solrQuery.addStatsFieldFacets(statsField, fieldListArr);
        }

      }
      solrQuery.setRows(0);
    }
    QueryResponse rsp = null;
    try {
      logger.info("setting up solrquery with /select handler..");
      rsp = solrClient.query(solrQuery);

      logger.info("response recieved from [ " + solrServer + " ] core [ "
          + solrCoreName + " ] in " + rsp.getQTime() + "ms.");

    } catch (SolrServerException | IOException e) {
      logger.debug("error occured while fetching results from solr server "
          + e.getMessage());
    } finally {
      try {
        solrClient.close();
      } catch (IOException e) {
        logger.debug("error occured while closing connection of solr server "
            + e.getMessage());
      }
    }

    return rsp;
  }

  public QueryResponse getSolrFieldStats(String solrServer,
      String solrCoreName, String uniqueKey, List<String> fields,
      StringBuilder filters) {

    solrCoreName = solrCoreName.replaceAll("`", "");
    SolrClient solrClient = new HttpSolrClient(solrServer + solrCoreName);

    SolrQuery solrQuery = new SolrQuery().setTermsRegexFlag("case_insensitive")
        .setQuery(uniqueKey + ":*").setRows(0);

    solrQuery.setGetFieldStatistics(true);
    for (String field : fields) {
      solrQuery.setGetFieldStatistics(field);
    }
    if (filters.length() > 0) {
      solrQuery.setParam("fq", filters.toString());
      logger.info("filter query [ " + filters.toString() + " ]");
    }
    logger.info("setting up solrquery..");
    try {
      QueryResponse rsp = solrClient.query(solrQuery);
      logger.info("response recieved from [ " + solrServer + " ] core [ "
          + solrCoreName + " ]");
      return rsp;
    } catch (SolrServerException | IOException e) {
      logger.debug("error occured while fetching results from solr server "
          + e.getMessage());
    } finally {
      try {
        solrClient.close();
      } catch (IOException e) {

      }
    }
    return null;
  }

  public SolrStream getSolrStreamResponse(String solrServer,
      SolrClient solrClient, String solrCoreName, List<String> fields,
      StringBuilder filters, String uniqueKey, Integer solrDocFetchCount) {

    Map<String, String> solrParams = new HashMap<String, String>();
    solrParams.put("q", uniqueKey + ":*");

    solrParams.put("sort", uniqueKey + " desc ");
    solrParams.put("fl", Joiner.on(",").join(fields));
    solrParams.put("qt", "/export");
    if (solrDocFetchCount >= 0)
      solrParams.put("rows", solrDocFetchCount.toString());
    if (filters.length() > 0) {
      solrParams.put("fq", filters.toString());
      logger.info("filter query [ " + filters.toString() + " ]");
    }

    logger.info("sending request to solr server " + solrServer + " on core "
        + solrCoreName);
    solrServer = solrServer + solrCoreName;
    SolrStream solrStream = new SolrStream(solrServer, solrParams);

    return solrStream;

  }

  public void createSolrView(String solrCoreName, String solrCoreViewWorkspace,
      SolrSchemaPojo oCVSchema) throws ClientProtocolException, IOException {

    List<SolrSchemaField> schemaFieldList = oCVSchema.getSchemaFields();
    List<String> fieldNames = Lists.newArrayList();
    String createViewSql = "CREATE OR REPLACE VIEW {0}.{1} as SELECT {2} from solr.{3}";
    for (SolrSchemaField cvSchemaField : schemaFieldList) {
      if (!cvSchemaField.isSkipdelete())
        fieldNames.add("`" + cvSchemaField.getFieldName() + "`");
    }
    if (!fieldNames.isEmpty()) {
      String fieldStr = Joiner.on(",").join(fieldNames);
      int lastIdxOf = solrCoreName.lastIndexOf("_");
      String viewName = solrCoreName.toLowerCase() + "view";
      if (lastIdxOf > -1) {
        viewName = solrCoreName.substring(0, lastIdxOf).toLowerCase()
            .replaceAll("_", "");
      }

      createViewSql = MessageFormat.format(createViewSql,
          solrCoreViewWorkspace, viewName, fieldStr, solrCoreName);
      logger.debug("create solr view with sql command :: " + createViewSql);
      String drillWebUI = "http://localhost:8047/query";
      HttpClient client = HttpClientBuilder.create().build();
      HttpPost httpPost = new HttpPost(drillWebUI);
      List<BasicNameValuePair> urlParameters = new ArrayList<BasicNameValuePair>();
      urlParameters.add(new BasicNameValuePair("queryType", "SQL"));
      urlParameters.add(new BasicNameValuePair("query", createViewSql));
      httpPost.setEntity(new UrlEncodedFormEntity(urlParameters));
      HttpResponse response = client.execute(httpPost);
      logger.debug("Response Code after executing create view command : "
          + response.getStatusLine().getStatusCode());
    } else {
      logger
          .debug("No DataSource specific fields are found. Not going create a view for solr core [ "
              + solrCoreName + " ]");
    }

  }
}
