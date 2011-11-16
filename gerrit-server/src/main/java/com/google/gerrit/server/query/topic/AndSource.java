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

import com.google.gerrit.server.query.AndPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class AndSource extends AndPredicate<TopicData> implements TopicDataSource {
  private static final Comparator<Predicate<TopicData>> CMP =
      new Comparator<Predicate<TopicData>>() {
        @Override
        public int compare(Predicate<TopicData> a, Predicate<TopicData> b) {
          int ai = a instanceof TopicDataSource ? 0 : 1;
          int bi = b instanceof TopicDataSource ? 0 : 1;
          int cmp = ai - bi;

          if (cmp == 0 //
              && a instanceof TopicDataSource //
              && b instanceof TopicDataSource) {
            ai = ((TopicDataSource) a).hasTopic() ? 0 : 1;
            bi = ((TopicDataSource) b).hasTopic() ? 0 : 1;
            cmp = ai - bi;
          }

          if (cmp == 0) {
            cmp = a.getCost() - b.getCost();
          }

          if (cmp == 0 //
              && a instanceof TopicDataSource //
              && b instanceof TopicDataSource) {
            TopicDataSource as = (TopicDataSource) a;
            TopicDataSource bs = (TopicDataSource) b;
            cmp = as.getCardinality() - bs.getCardinality();
          }

          return cmp;
        }
      };

  private static List<Predicate<TopicData>> sort(
      Collection<? extends Predicate<TopicData>> that) {
    ArrayList<Predicate<TopicData>> r =
        new ArrayList<Predicate<TopicData>>(that);
    Collections.sort(r, CMP);
    return r;
  }

  private int cardinality = -1;

  AndSource(final Collection<? extends Predicate<TopicData>> that) {
    super(sort(that));
  }

  @Override
  public boolean hasTopic() {
    TopicDataSource source = source();
    return source != null && source.hasTopic();
  }

  @Override
  public ResultSet<TopicData> read() throws OrmException {
    TopicDataSource source = source();
    if (source == null) {
      throw new OrmException("No TopicDataSource: " + this);
    }

    ArrayList<TopicData> r = new ArrayList<TopicData>();
    TopicData last = null;
    boolean skipped = false;
    for (TopicData data : source.read()) {
      if (match(data)) {
        r.add(data);
      } else {
        skipped = true;
      }
      last = data;
    }

    if (skipped && last != null && source instanceof Paginated) {
      // If we our source is a paginated source and we skipped at
      // least one of its results, we may not have filled the full
      // limit the caller wants.  Restart the source and continue.
      //
      Paginated p = (Paginated) source;
      while (skipped && r.size() < p.limit()) {
        TopicData lastBeforeRestart = last;
        skipped = false;
        last = null;
        for (TopicData data : p.restart(lastBeforeRestart)) {
          if (match(data)) {
            r.add(data);
          } else {
            skipped = true;
          }
          last = data;
        }
      }
    }

    return new ListResultSet<TopicData>(r);
  }

  private TopicDataSource source() {
    for (Predicate<TopicData> p : getChildren()) {
      if (p instanceof TopicDataSource) {
        return (TopicDataSource) p;
      }
    }
    return null;
  }

  @Override
  public int getCardinality() {
    if (cardinality < 0) {
      cardinality = Integer.MAX_VALUE;
      for (Predicate<TopicData> p : getChildren()) {
        if (p instanceof TopicDataSource) {
          int c = ((TopicDataSource) p).getCardinality();
          cardinality = Math.min(cardinality, c);
        }
      }
    }
    return cardinality;
  }
}
