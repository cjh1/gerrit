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
import com.google.gerrit.server.query.Predicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Predicate for a {@link Topic.Status}.
 * <p>
 * The actual name of this operator can differ, it usually comes as {@code
 * status:} but may also be {@code is:} to help do-what-i-meanery for end-users
 * searching for changes. Either operator name has the same meaning.
 */
final class TopicStatusPredicate extends OperatorPredicate<TopicData> {
  private static final Map<String, Topic.Status> byName;
  private static final EnumMap<Topic.Status, String> byEnum;

  static {
    byName = new HashMap<String, Topic.Status>();
    byEnum = new EnumMap<Topic.Status, String>(Topic.Status.class);
    for (final Topic.Status s : Topic.Status.values()) {
      final String name = s.name().toLowerCase();
      byName.put(name, s);
      byEnum.put(s, name);
    }
  }

  static Predicate<TopicData> open(Provider<ReviewDb> dbProvider) {
    List<Predicate<TopicData>> r = new ArrayList<Predicate<TopicData>>(4);
    for (final Topic.Status e : Topic.Status.values()) {
      if (e.isOpen()) {
        r.add(new TopicStatusPredicate(dbProvider, e));
      }
    }
    return r.size() == 1 ? r.get(0) : or(r);
  }

  static Predicate<TopicData> closed(Provider<ReviewDb> dbProvider) {
    List<Predicate<TopicData>> r = new ArrayList<Predicate<TopicData>>(4);
    for (final Topic.Status e : Topic.Status.values()) {
      if (e.isClosed()) {
        r.add(new TopicStatusPredicate(dbProvider, e));
      }
    }
    return r.size() == 1 ? r.get(0) : or(r);
  }

  private static Topic.Status parse(final String value) {
    final Topic.Status s = byName.get(value);
    if (s == null) {
      throw new IllegalArgumentException();
    }
    return s;
  }

  private final Provider<ReviewDb> dbProvider;
  private final Topic.Status status;

  TopicStatusPredicate(Provider<ReviewDb> dbProvider, String value) {
    this(dbProvider, parse(value));
  }

  TopicStatusPredicate(Provider<ReviewDb> dbProvider, Topic.Status status) {
    super(TopicQueryBuilder.FIELD_STATUS, byEnum.get(status));
    this.dbProvider = dbProvider;
    this.status = status;
  }

  Topic.Status getStatus() {
    return status;
  }

  @Override
  public boolean match(final TopicData object) throws OrmException {
    Topic change = object.topic(dbProvider);
    return change != null && status.equals(change.getStatus());
  }

  @Override
  public int getCost() {
    return 0;
  }

  @Override
  public int hashCode() {
    return status.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof TopicStatusPredicate) {
      final TopicStatusPredicate p = (TopicStatusPredicate) other;
      return status.equals(p.status);
    }
    return false;
  }

  @Override
  public String toString() {
    return getOperator() + ":" + getValue();
  }
}
