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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

class ProjectPredicate extends OperatorPredicate<TopicData> {
  private final Provider<ReviewDb> dbProvider;

  ProjectPredicate(Provider<ReviewDb> dbProvider, String id) {
    super(TopicQueryBuilder.FIELD_PROJECT, id);
    this.dbProvider = dbProvider;
  }

  Project.NameKey getValueKey() {
    return new Project.NameKey(getValue());
  }

  @Override
  public boolean match(final TopicData object) throws OrmException {
    Topic change = object.topic(dbProvider);
    if (change == null) {
      return false;
    }

    Project.NameKey p = change.getDest().getParentKey();
    return p.equals(getValueKey());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
