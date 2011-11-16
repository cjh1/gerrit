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
import com.google.gerrit.reviewdb.TopicAccess;
import com.google.gerrit.server.query.IntPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryRewriter;
import com.google.gerrit.server.query.RewritePredicate;
import com.google.gerrit.server.query.change.ChangeCosts;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import java.util.Collection;

public class TopicQueryRewriter extends QueryRewriter<TopicData> {
  private static final QueryRewriter.Definition<TopicData, TopicQueryRewriter> mydef =
      new QueryRewriter.Definition<TopicData, TopicQueryRewriter>(
          TopicQueryRewriter.class, new TopicQueryBuilder(
              new TopicQueryBuilder.Arguments( //
                  new InvalidProvider<ReviewDb>(), //
                  new InvalidProvider<TopicQueryRewriter>(), //
                  null, null, null, null, null, null, null, //
                  null, null, null, null), null));

  private final Provider<ReviewDb> dbProvider;

  @Inject
  TopicQueryRewriter(Provider<ReviewDb> dbProvider) {
    super(mydef);
    this.dbProvider = dbProvider;
  }

  @Override
  public Predicate<TopicData> and(Collection<? extends Predicate<TopicData>> l) {
    return hasSource(l) ? new AndSource(l) : super.and(l);
  }

  @Override
  public Predicate<TopicData> or(Collection<? extends Predicate<TopicData>> l) {
    return hasSource(l) ? new OrSource(l) : super.or(l);
  }

  @Rewrite("status:open S=(sortkey_before:*) L=(limit:*)")
  public Predicate<TopicData> r20_byOpenNext(
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<TopicData> l) {
    return new PaginatedSource(2000, s.getValue(), l.intValue()) {
      @Override
      ResultSet<Topic> scan(TopicAccess a, String key, int limit)
          throws OrmException {
        return a.allOpenNext(key, limit);
      }

      @Override
      public boolean match(TopicData cd) throws OrmException {
        return cd.topic(dbProvider).getStatus().isOpen() && s.match(cd);
      }
    };
  }
  
  @SuppressWarnings("unchecked")
  @Rewrite("status:merged S=(sortkey_before:*) L=(limit:*)")
  public Predicate<TopicData> r20_byMergedNext(
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<TopicData> l) {
    return new PaginatedSource(50000, s.getValue(), l.intValue()) {
      {
        init("r20_byMergedNext", s, l);
      }

      @Override
      ResultSet<Topic> scan(TopicAccess a, String key, int limit)
          throws OrmException {
        return a.allClosedNext(Topic.Status.MERGED.getCode(), key, limit);
      }

      @Override
      public boolean match(TopicData cd) throws OrmException {
        return cd.topic(dbProvider).getStatus() == Topic.Status.MERGED
            && s.match(cd);
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Rewrite("status:abandoned S=(sortkey_before:*) L=(limit:*)")
  public Predicate<TopicData> r20_byAbandonedNext(
      @Named("S") final SortKeyPredicate.Before s,
      @Named("L") final IntPredicate<TopicData> l) {
    return new PaginatedSource(50000, s.getValue(), l.intValue()) {
      {
        init("r20_byAbandonedNext", s, l);
      }

      @Override
      ResultSet<Topic> scan(TopicAccess a, String key, int limit)
          throws OrmException {
        return a.allClosedNext(Topic.Status.ABANDONED.getCode(), key, limit);
      }

      @Override
      public boolean match(TopicData cd) throws OrmException {
        return cd.topic(dbProvider).getStatus() == Topic.Status.ABANDONED
            && s.match(cd);
      }
    };
  }

  private static boolean hasSource(Collection<? extends Predicate<TopicData>> l) {
    for (Predicate<TopicData> p : l) {
      if (p instanceof TopicDataSource) {
        return true;
      }
    }
    return false;
  }

  private abstract static class Source extends RewritePredicate<TopicData>
      implements TopicDataSource {
    @Override
    public boolean hasTopic() {
      return false;
    }
  }

  private abstract class TopicSource extends Source {
    private final int cardinality;

    TopicSource(int card) {
      this.cardinality = card;
    }

    abstract ResultSet<Topic> scan(TopicAccess a) throws OrmException;

    @Override
    public ResultSet<TopicData> read() throws OrmException {
      return TopicDataResultSet.topic(scan(dbProvider.get().topics()));
    }

    @Override
    public boolean hasTopic() {
      return true;
    }

    @Override
    public int getCardinality() {
      return cardinality;
    }

    @Override
    public int getCost() {
      return ChangeCosts.cost(ChangeCosts.CHANGES_SCAN, getCardinality());
    }
  }

  private abstract class PaginatedSource extends TopicSource implements
      Paginated {
    private final String startKey;
    private final int limit;

    PaginatedSource(int card, String start, int lim) {
      super(card);
      this.startKey = start;
      this.limit = lim;
    }

    @Override
    public int limit() {
      return limit;
    }

    @Override
    ResultSet<Topic> scan(TopicAccess a) throws OrmException {
      return scan(a, startKey, limit);
    }

    @Override
    public ResultSet<TopicData> restart(TopicData last) throws OrmException {
      return TopicDataResultSet.topic(scan(dbProvider.get().topics(), //
          last.topic(dbProvider).getSortKey(), //
          limit));
    }

    abstract ResultSet<Topic> scan(TopicAccess a, String key, int limit)
        throws OrmException;
  }

  private static final class InvalidProvider<T> implements Provider<T> {
    @Override
    public T get() {
      throw new OutOfScopeException("Not available at init");
    }
  }
}
