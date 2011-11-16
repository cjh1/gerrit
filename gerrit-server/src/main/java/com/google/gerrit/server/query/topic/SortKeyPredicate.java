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

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

abstract class SortKeyPredicate extends OperatorPredicate<TopicData> {
  protected final Provider<ReviewDb> dbProvider;

  SortKeyPredicate(Provider<ReviewDb> dbProvider, String name, String value) {
    super(name, value);
    this.dbProvider = dbProvider;
  }

  @Override
  public int getCost() {
    return 1;
  }

  static class Before extends SortKeyPredicate {
    Before(Provider<ReviewDb> dbProvider, String value) {
      super(dbProvider, "sortkey_before", value);
    }

    @Override
    public boolean match(TopicData cd) throws OrmException {
      Topic topic = cd.topic(dbProvider);
      return topic != null && topic.getSortKey().compareTo(getValue()) < 0;
    }
  }

  static class After extends SortKeyPredicate {
    After(Provider<ReviewDb> dbProvider, String value) {
      super(dbProvider, "sortkey_after", value);
    }

    @Override
    public boolean match(TopicData cd) throws OrmException {
      Topic topic = cd.topic(dbProvider);
      return topic != null && topic.getSortKey().compareTo(getValue()) > 0;
    }
  }
}
