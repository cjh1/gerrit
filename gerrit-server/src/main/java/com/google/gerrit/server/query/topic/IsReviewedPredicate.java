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

import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import java.util.Collection;

class IsReviewedPredicate extends OperatorPredicate<TopicData> {
  private final Provider<ReviewDb> dbProvider;

  IsReviewedPredicate(Provider<ReviewDb> dbProvider) {
    super(TopicQueryBuilder.FIELD_IS, "reviewed");
    this.dbProvider = dbProvider;
  }

  @Override
  public boolean match(final TopicData object) throws OrmException {
    Topic t = object.topic(dbProvider);
    if (t == null) {
      return false;
    }

    ChangeSet.Id current = t.currentChangeSetId();
    Collection<ChangeSetApproval> approvals = object.approvals(dbProvider);

    for (ChangeSetApproval changeSetApproval : approvals) {
      if(changeSetApproval.getChangeSetId().equals(current) && changeSetApproval.getValue() != 0) {
        return true;
      }
        
    }

    return false;
  }

  @Override
  public int getCost() {
    return 2;
  }
}
