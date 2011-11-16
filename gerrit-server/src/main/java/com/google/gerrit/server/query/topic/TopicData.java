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

import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import java.util.Collection;

public class TopicData {
  private final Topic.Id legacyId;
  private Topic topic;
  private CurrentUser visibleTo;
  private Collection<ChangeSetApproval> approvals;

  public TopicData(final Topic.Id id) {
    legacyId = id;
  }

  public TopicData(final Topic topic) {
    legacyId = topic.getId();
    this.topic = topic;
  }


  public Topic.Id getId() {
    return legacyId;
  }

  public Topic getTopic() {
    return topic;
  }

  boolean fastIsVisibleTo(CurrentUser user) {
    return visibleTo == user;
  }

  void cacheVisibleTo(CurrentUser user) {
    visibleTo = user;
  }

  public Topic topic(Provider<ReviewDb> db) throws OrmException {
    if (topic == null) {
      topic = db.get().topics().get(legacyId);
    }
    return topic;
  }

  public boolean hasTopic() {
    return topic != null;
  }

  public Collection<ChangeSetApproval> approvals(Provider<ReviewDb> db)
      throws OrmException {
    if (approvals == null) {
      approvals = db.get().changeSetApprovals().byTopic(topic.getId()).toList();
    }
    return approvals;
  }
}
