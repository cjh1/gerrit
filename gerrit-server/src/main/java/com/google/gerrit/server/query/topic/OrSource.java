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

import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

class OrSource extends OrPredicate<TopicData> implements TopicDataSource {
  private int cardinality = -1;

  OrSource(final Collection<? extends Predicate<TopicData>> that) {
    super(that);
  }

  @Override
  public ResultSet<TopicData> read() throws OrmException {
    ArrayList<TopicData> r = new ArrayList<TopicData>();
    HashSet<Topic.Id> have = new HashSet<Topic.Id>();
    for (Predicate<TopicData> p : getChildren()) {
      if (p instanceof TopicDataSource) {
        for (TopicData cd : ((TopicDataSource) p).read()) {
          if (have.add(cd.getId())) {
            r.add(cd);
          }
        }
      } else {
        throw new OrmException("No TopicDataSource: " + p);
      }
    }
    return new ListResultSet<TopicData>(r);
  }

  @Override
  public boolean hasTopic() {
    for (Predicate<TopicData> p : getChildren()) {
      if (!(p instanceof TopicDataSource)
          || !((TopicDataSource) p).hasTopic()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getCardinality() {
    if (cardinality < 0) {
      cardinality = 0;
      for (Predicate<TopicData> p : getChildren()) {
        if (p instanceof TopicDataSource) {
          cardinality += ((TopicDataSource) p).getCardinality();
        }
      }
    }
    return cardinality;
  }
}
