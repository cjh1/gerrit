// Copyright (C) 2011 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.query.topic;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.events.ChangeAttribute;
import com.google.gerrit.server.events.EventFactory;
import com.google.gerrit.server.events.QueryStats;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.Gson;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

public class QueryProcessor {
  private static final Logger log =
      LoggerFactory.getLogger(QueryProcessor.class);

  public static enum OutputFormat {
    TEXT, JSON;
  }

  private final Gson gson = new Gson();
  private final SimpleDateFormat sdf =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");

  private final EventFactory eventFactory;
  private final TopicQueryBuilder queryBuilder;
  private final TopicQueryRewriter queryRewriter;
  private final Provider<ReviewDb> db;
  private final int maxLimit;

  private OutputFormat outputFormat = OutputFormat.TEXT;

  private OutputStream outputStream = DisabledOutputStream.INSTANCE;
  private PrintWriter out;

  @Inject
  QueryProcessor(EventFactory eventFactory,
      TopicQueryBuilder.Factory queryBuilder, CurrentUser currentUser,
      TopicQueryRewriter queryRewriter, Provider<ReviewDb> db) {
    this.eventFactory = eventFactory;
    this.queryBuilder = queryBuilder.create(currentUser);
    this.queryRewriter = queryRewriter;
    this.db = db;
    this.maxLimit = currentUser.getCapabilities()
      .getRange(GlobalCapability.QUERY_LIMIT)
      .getMax();
  }

  public void setOutput(OutputStream out, OutputFormat fmt) {
    this.outputStream = out;
    this.outputFormat = fmt;
  }

  public void query(String queryString) throws IOException {
    out = new PrintWriter( //
        new BufferedWriter( //
            new OutputStreamWriter(outputStream, "UTF-8")));
    try {
      if (maxLimit <= 0) {
        ErrorMessage m = new ErrorMessage();
        m.message = "query disabled";
        show(m);
        return;
      }

      try {
        final QueryStats stats = new QueryStats();
        stats.runTimeMilliseconds = System.currentTimeMillis();

        final Predicate<TopicData> visibleToMe = queryBuilder.is_visible();
        Predicate<TopicData> s = compileQuery(queryString, visibleToMe);
        List<TopicData> results = new ArrayList<TopicData>();
        HashSet<Topic.Id> want = new HashSet<Topic.Id>();
        for (TopicData d : ((TopicDataSource) s).read()) {
          if (d.hasTopic()) {
            // Checking visibleToMe here should be unnecessary, the
            // query should have already performed it. But we don't
            // want to trust the query rewriter that much yet.
            //
            if (visibleToMe.match(d)) {
              results.add(d);
            }
          } else {
            want.add(d.getId());
          }
        }

        if (!want.isEmpty()) {
          for (Topic t : db.get().topics().get(want)) {
            TopicData d = new TopicData(t);
            if (visibleToMe.match(d)) {
              results.add(d);
            }
          }
        }

        Collections.sort(results, new Comparator<TopicData>() {
          @Override
          public int compare(TopicData a, TopicData b) {
            return b.getTopic().getSortKey().compareTo(
                a.getTopic().getSortKey());
          }
        });

        int limit = limit(s);
        if (limit < results.size()) {
          results = results.subList(0, limit);
        }

        stats.rowCount = results.size();
        stats.runTimeMilliseconds =
            System.currentTimeMillis() - stats.runTimeMilliseconds;
        show(stats);
      } catch (OrmException err) {
        log.error("Cannot execute query: " + queryString, err);

        ErrorMessage m = new ErrorMessage();
        m.message = "cannot query database";
        show(m);

      } catch (QueryParseException e) {
        ErrorMessage m = new ErrorMessage();
        m.message = e.getMessage();
        show(m);
      }
    } finally {
      try {
        out.flush();
      } finally {
        out = null;
      }
    }
  }

  private int limit(Predicate<TopicData> s) {
    return queryBuilder.hasLimit(s) ? queryBuilder.getLimit(s) : maxLimit;
  }

  @SuppressWarnings("unchecked")
  private Predicate<TopicData> compileQuery(String queryString,
      final Predicate<TopicData> visibleToMe) throws QueryParseException {

    Predicate<TopicData> q = queryBuilder.parse(queryString);
    if (!queryBuilder.hasSortKey(q)) {
      q = Predicate.and(q, queryBuilder.sortkey_before("z"));
    }
    q = Predicate.and(q, queryBuilder.limit(maxLimit), visibleToMe);

    Predicate<TopicData> s = queryRewriter.rewrite(q);
    if (!(s instanceof TopicDataSource)) {
      s = queryRewriter.rewrite(Predicate.and(queryBuilder.status_open(), q));
    }

    if (!(s instanceof TopicDataSource)) {
      throw new QueryParseException("cannot execute query: " + s);
    }

    return s;
  }

  private void show(Object data) {
    switch (outputFormat) {
      default:
      case TEXT:
        if (data instanceof ChangeAttribute) {
          out.print("change ");
          out.print(((ChangeAttribute) data).id);
          out.print("\n");
          showText(data, 1);
        } else {
          showText(data, 0);
        }
        out.print('\n');
        break;

      case JSON:
        out.print(gson.toJson(data));
        out.print('\n');
        break;
    }
  }

  private void showText(Object data, int depth) {
    for (Field f : fieldsOf(data.getClass())) {
      Object val;
      try {
        val = f.get(data);
      } catch (IllegalArgumentException err) {
        continue;
      } catch (IllegalAccessException err) {
        continue;
      }
      if (val == null) {
        continue;
      }

      showField(f.getName(), val, depth);
    }
  }

  private String indent(int spaces) {
    if (spaces == 0) {
      return "";
    } else {
      return String.format("%" + spaces + "s", " ");
    }
  }

  private void showField(String field, Object value, int depth) {
    final int spacesDepthRatio = 2;
    String indent = indent(depth * spacesDepthRatio);
    out.print(indent);
    out.print(field);
    out.print(':');
    if (value instanceof String && ((String) value).contains("\n")) {
      out.print(' ');
      // Idention for multi-line text is
      // current depth indetion + length of field + length of ": "
      indent = indent(indent.length() + field.length() + spacesDepthRatio);
      out.print(((String) value).replaceAll("\n", "\n" + indent).trim());
      out.print('\n');
    } else if (value instanceof Long && isDateField(field)) {
      out.print(' ');
      out.print(sdf.format(new Date(((Long) value) * 1000L)));
      out.print('\n');
    } else if (isPrimitive(value)) {
      out.print(' ');
      out.print(value);
      out.print('\n');
    } else if (value instanceof Collection) {
      out.print('\n');
      for (Object thing : ((Collection<?>) value)) {
        if (isPrimitive(thing)) {
          out.print(' ');
          out.print(value);
          out.print('\n');
        } else {
          showText(thing, depth + 1);
        }
      }
    } else {
      out.print('\n');
      showText(value, depth + 1);
    }
  }

  private static boolean isPrimitive(Object value) {
    return value instanceof String //
        || value instanceof Number //
        || value instanceof Boolean //
        || value instanceof Enum;
  }

  private static boolean isDateField(String name) {
    return "lastUpdated".equals(name) //
        || "grantedOn".equals(name) //
        || "timestamp".equals(name) //
        || "createdOn".equals(name);
  }

  private List<Field> fieldsOf(Class<?> type) {
    List<Field> r = new ArrayList<Field>();
    if (type.getSuperclass() != null) {
      r.addAll(fieldsOf(type.getSuperclass()));
    }
    r.addAll(Arrays.asList(type.getDeclaredFields()));
    return r;
  }

  static class ErrorMessage {
    public final String type = "error";
    public String message;
  }
}
