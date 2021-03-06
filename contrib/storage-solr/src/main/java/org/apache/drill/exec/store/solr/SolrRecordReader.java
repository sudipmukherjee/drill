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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.Charsets;
import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.types.TypeProtos;
import org.apache.drill.common.types.TypeProtos.MajorType;
import org.apache.drill.common.types.TypeProtos.MinorType;
import org.apache.drill.exec.exception.SchemaChangeException;
import org.apache.drill.exec.memory.OutOfMemoryException;
import org.apache.drill.exec.ops.FragmentContext;
import org.apache.drill.exec.ops.OperatorContext;
import org.apache.drill.exec.physical.impl.OutputMutator;
import org.apache.drill.exec.record.MaterializedField;
import org.apache.drill.exec.store.AbstractRecordReader;
import org.apache.drill.exec.store.solr.schema.SolrSchemaPojo;
import org.apache.drill.exec.store.solr.schema.SolrSchemaField;
import org.apache.drill.exec.vector.DateVector;
import org.apache.drill.exec.vector.Float8Vector;
import org.apache.drill.exec.vector.NullableBigIntVector;
import org.apache.drill.exec.vector.NullableIntVector;
import org.apache.drill.exec.vector.NullableTimeStampVector;
import org.apache.drill.exec.vector.NullableVarCharVector;
import org.apache.drill.exec.vector.ValueVector;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.client.solrj.io.stream.SolrStream;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class SolrRecordReader extends AbstractRecordReader {
  static final Logger logger = LoggerFactory.getLogger(SolrRecordReader.class);

  private FragmentContext fc;
  protected Map<String, ValueVector> vectors = null;
  protected String solrServerUrl;
  protected SolrClient solrClient;
  protected SolrSubScan solrSubScan;
  protected List<SolrScanSpec> scanList;
  protected SolrClientAPIExec solrClientApiExec;
  protected OutputMutator outputMutator;

  protected Iterator<SolrDocument> resultDocsIter;
  protected List<String> fields;
  private MajorType.Builder t;
  private Map<String, SolrSchemaField> schemaFieldMap;
  // private Iterator<Tuple> resultIter;
  // private List<Tuple> solrDocsTuple;
  private static final String defaultDateFormat = "EEE MMM dd kk:mm:ss z yyyy";
  private static final String ISODateFormat = "yyyyMMdd'T'HHmmss'Z'";
  private boolean solrStreamReadFinished = false;
  private boolean useSolrStream = false;

  public SolrRecordReader(FragmentContext context, SolrSubScan config) {
    fc = context;
    solrSubScan = config;

    solrServerUrl = solrSubScan.getSolrPlugin().getSolrStorageConfig()
        .getSolrServer();
    scanList = solrSubScan.getScanList();
    solrClientApiExec = solrSubScan.getSolrPlugin().getSolrClientApiExec();
    solrClient = solrSubScan.getSolrPlugin().getSolrClient();
    useSolrStream = solrSubScan.getSolrPlugin().getSolrStorageConfig()
        .getSolrStorageProperties().isUseSolrStream();

    String solrCoreName = scanList.get(0).getSolrCoreName();
    List<SchemaPath> colums = config.getColumns();

    List<String> responseFieldList = config.getSolrScanSpec()
        .getResponseFieldList();

    Integer solrDocFetchCount = solrSubScan.getSolrScanSpec()
        .getSolrDocFetchCount();
    SolrSchemaPojo oCVSchema = config.getSolrScanSpec().getCvSchema(); // solr core
                                                                 // schema

    if (oCVSchema.getSchemaFields() != null) {
      schemaFieldMap = new HashMap<String, SolrSchemaField>(oCVSchema
          .getSchemaFields().size());

      for (SolrSchemaField cvSchemaField : oCVSchema.getSchemaFields()) {
        if (!cvSchemaField.isSkipdelete()) {
          schemaFieldMap.put(cvSchemaField.getFieldName(), cvSchemaField);
        }
      }
    }

    setColumns(colums);

    // resultIter = solrDocsTuple.iterator();
    // logger.info("SolrRecordReader:: solrDocsTuple:: " +
    // solrDocsTuple.size());

    // solrDocList = solrClientApiExec.getSolrDocs(solrServerUrl, solrCoreName,
    // this.fields, solrDocFetchCount, sb); // solr docs
    // resultIter = solrDocList.iterator();
    // logger.info("SolrRecordReader:: solrDocList:: " + solrDocList.size());

  }

  @Override
  protected Collection<SchemaPath> transformColumns(
      Collection<SchemaPath> projectedColumns) {
    Set<SchemaPath> transformed = Sets.newLinkedHashSet();
    if (!isStarQuery()) {
      logger
          .debug(" This is not a star query, restricting response to projected columns only "
              + projectedColumns);
      fields = Lists.newArrayListWithExpectedSize(projectedColumns.size());
      for (SchemaPath column : projectedColumns) {
        String fieldName = column.getRootSegment().getPath();
        if (schemaFieldMap.containsKey(fieldName)) {
          transformed.add(SchemaPath.getSimplePath(fieldName));
          this.fields.add(fieldName);
        }

      }
    } else {
      fields = Lists.newArrayListWithExpectedSize(schemaFieldMap.size());
      for (String fieldName : schemaFieldMap.keySet()) {
        this.fields.add(fieldName);
      }
      transformed.add(AbstractRecordReader.STAR_COLUMN);
    }
    return transformed;
  }

  @Override
  public void setup(OperatorContext context, OutputMutator output)
      throws ExecutionSetupException {
    logger.debug("SolrRecordReader :: setup");

    // SolrDocument solrDocument = solrDocList.get(0);
    // Collection<String> fieldNames = solrDocument.getFieldNames();
    try {
      vectors = new HashMap<String, ValueVector>(fields.size());
      for (String field : /* fieldNames */fields) {
        MaterializedField m_field = null;
        SolrSchemaField cvSchemaField = schemaFieldMap.get(field);
        Preconditions.checkNotNull(cvSchemaField);

        switch (cvSchemaField.getType()) {
        case "string":
          t = MajorType.newBuilder().setMinorType(MinorType.VARCHAR);
          m_field = MaterializedField.create(field, t.build());
          vectors.put(field,
              output.addField(m_field, NullableVarCharVector.class));
          break;
        case "long":
        case "tlong":
        case "rounded1024":
        case "double":
        case "tdouble":
          t = MajorType.newBuilder().setMinorType(TypeProtos.MinorType.BIGINT);
          m_field = MaterializedField.create(field, t.build());
          vectors.put(field,
              output.addField(m_field, NullableBigIntVector.class));
          break;
        case "int":
          t = MajorType.newBuilder().setMinorType(TypeProtos.MinorType.INT);
          m_field = MaterializedField.create(field, t.build());
          vectors.put(field, output.addField(m_field, NullableIntVector.class));
          break;
        case "float":
          t = MajorType.newBuilder().setMinorType(TypeProtos.MinorType.FLOAT8);
          m_field = MaterializedField.create(field, t.build());
          vectors.put(field, output.addField(m_field, Float8Vector.class));
          break;
        case "date":
        case "tdate":
        case "timestamp":
          t = MajorType.newBuilder().setMinorType(
              TypeProtos.MinorType.TIMESTAMP);
          m_field = MaterializedField.create(field, t.build());
          vectors.put(field,
              output.addField(m_field, NullableTimeStampVector.class));
          break;
        default:
          t = MajorType.newBuilder().setMinorType(TypeProtos.MinorType.VARCHAR);
          m_field = MaterializedField.create(field, t.build());
          vectors.put(field,
              output.addField(m_field, NullableVarCharVector.class));
          break;
        }

      }
      this.outputMutator = output;
    } catch (SchemaChangeException e) {
      throw new ExecutionSetupException(e);
    }

  }

  @Override
  public int next() {
    int counter = 0;
    if (!solrStreamReadFinished) {
      logger.debug("SolrRecordReader :: next");
      SolrFilterParam filters = solrSubScan.getSolrScanSpec().getFilter();
      String solrCoreName = solrSubScan.getSolrScanSpec().getSolrCoreName();
      String uniqueKey = solrSubScan.getSolrScanSpec().getCvSchema()
          .getUniqueKey();
      Integer solrDocFetchCount = solrSubScan.getSolrScanSpec()
          .getSolrDocFetchCount();
      StringBuilder sb = new StringBuilder();
      if (filters != null) {
        for (String filter : filters) {
          sb.append(filter);
        }
      }

      if (useSolrStream) {
        SolrStream solrStream = solrClientApiExec.getSolrStreamResponse(
            solrServerUrl, solrClient, solrCoreName, this.fields, sb,
            uniqueKey, solrDocFetchCount);

        try {
          solrStream.open();
          Tuple solrDocument = null;
          while (true) {
            solrDocument = solrStream.read();

            if (solrDocument.EOF) {
              break;
            }

            for (String columns : vectors.keySet()) {
              ValueVector vv = vectors.get(columns);
              Object fieldValue = solrDocument.get(columns);
              processRecord(vv, fieldValue, counter);

            }
            counter++;
          }
        } catch (Exception e) {
          logger.info("error occured while fetching results from solr server "
              + e.getMessage());
          return 0;
        } finally {
          try {
            solrStream.close();
            solrStream = null;
          } catch (IOException e) {
            logger
                .debug("error occured while fetching results from solr server "
                    + e.getMessage());
          }
        }
      } else {
        if (solrDocFetchCount == -1) {
          solrDocFetchCount = Integer.MAX_VALUE;
        }
        SolrDocumentList solrDocList = solrClientApiExec.getSolrDocs(
            solrServerUrl, solrCoreName, uniqueKey, this.fields,
            solrDocFetchCount, sb); // solr
        // docs

        logger.info("SolrRecordReader:: solrDocList:: " + solrDocList.size());

        for (SolrDocument solrDocument : solrDocList) {
          for (String columns : vectors.keySet()) {
            ValueVector vv = vectors.get(columns);
            Object fieldValue = solrDocument.get(columns);
            processRecord(vv, fieldValue, counter);

          }
          counter++;
        }
      }

      for (String key : vectors.keySet()) {
        ValueVector vv = vectors.get(key);
        vv.getMutator().setValueCount(counter > 0 ? counter : 0);
      }
    }
    solrStreamReadFinished = true;
    return counter > 0 ? counter : 0;
  }

  private void processRecord(ValueVector vv, Object fieldValue,
      int recordCounter) {
    String fieldValueStr = null;
    byte[] record = null;
    try {
      fieldValueStr = fieldValue.toString();
      record = fieldValueStr.getBytes(Charsets.UTF_8);
      if (vv.getClass().equals(NullableVarCharVector.class)) {
        NullableVarCharVector v = (NullableVarCharVector) vv;
        v.getMutator().setSafe(recordCounter, record, 0, record.length);
        v.getMutator().setValueLengthSafe(recordCounter, record.length);
      } else if (vv.getClass().equals(NullableBigIntVector.class)) {
        NullableBigIntVector v = (NullableBigIntVector) vv;
        Double d = Double.parseDouble(fieldValueStr);
        v.getMutator().setSafe(recordCounter, d.longValue());
      } else if (vv.getClass().equals(NullableIntVector.class)) {
        NullableIntVector v = (NullableIntVector) vv;
        v.getMutator().setSafe(recordCounter, Integer.parseInt(fieldValueStr));
      } else if (vv.getClass().equals(DateVector.class)) {
        DateVector v = (DateVector) vv;
        SimpleDateFormat dateParser = new SimpleDateFormat(defaultDateFormat);
        long dtime = 0l;
        try {
          dtime = dateParser.parse(fieldValueStr).getTime();
        } catch (Exception e) {
          logger.trace(" Unable to format the date recieved..."
              + e.getMessage());
        }
        v.getMutator().setSafe(recordCounter, dtime);
      } else if (vv.getClass().equals(NullableTimeStampVector.class)) {
        NullableTimeStampVector v = (NullableTimeStampVector) vv;
        DateTimeFormatter dtf = DateTimeFormat.forPattern(defaultDateFormat);
        SimpleDateFormat dateParser = new SimpleDateFormat(defaultDateFormat);
        // Format for output
        SimpleDateFormat dateFormatter = new SimpleDateFormat(ISODateFormat); // ISO_8601

        long dtime = 0l;
        try {
          // Thu Sep 17 16:57:17 IST 2015
          dtime = dateParser.parse(fieldValueStr).getTime();
          // dtime = dtf.parseMillis(fieldValueStr);
        } catch (Exception e) {
          logger
              .debug("Unable to format the date recieved..." + e.getMessage());
        }
        v.getMutator().setSafe(recordCounter, dtime);
      }
    } catch (Exception e) {

    }
  }

  @Override
  public void allocate(Map<MaterializedField.Key, ValueVector> vectorMap)
      throws OutOfMemoryException {
    super.allocate(vectorMap);
  }

  @Override
  public void cleanup() {

  }

}
