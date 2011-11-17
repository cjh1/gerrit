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

package com.google.gerrit.httpd.rpc.topic;

import com.google.gerrit.common.data.AccountTopicDashboardInfo;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.SingleListTopicInfo;
import com.google.gerrit.common.data.TopicInfo;
import com.google.gerrit.common.data.TopicListService;
import com.google.gerrit.common.errors.InvalidQueryException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.reviewdb.TopicAccess;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.TopicControl;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.topic.TopicData;
import com.google.gerrit.server.query.topic.TopicDataSource;
import com.google.gerrit.server.query.topic.TopicQueryBuilder;
import com.google.gerrit.server.query.topic.TopicQueryRewriter;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TopicListServiceImpl extends BaseServiceImplementation implements
    TopicListService {
  private static final Comparator<TopicInfo> ID_COMP =
      new Comparator<TopicInfo>() {
        public int compare(final TopicInfo o1, final TopicInfo o2) {
          return o1.getId().get() - o2.getId().get();
        }
      };
  private static final Comparator<TopicInfo> SORT_KEY_COMP =
      new Comparator<TopicInfo>() {
        public int compare(final TopicInfo o1, final TopicInfo o2) {
          return o2.getSortKey().compareTo(o1.getSortKey());
        }
      };
  private static final Comparator<Topic> QUERY_PREV =
      new Comparator<Topic>() {
        public int compare(final Topic a, final Topic b) {
          return a.getSortKey().compareTo(b.getSortKey());
        }
      };
  private static final Comparator<Topic> QUERY_NEXT =
      new Comparator<Topic>() {
        public int compare(final Topic a, final Topic b) {
          return b.getSortKey().compareTo(a.getSortKey());
        }
      };

  private final Provider<CurrentUser> currentUser;
  private final TopicControl.Factory topicControlFactory;
  private final AccountInfoCacheFactory.Factory accountInfoCacheFactory;

  private final TopicQueryBuilder.Factory queryBuilder;
  private final Provider<TopicQueryRewriter> queryRewriter;

  @Inject
  TopicListServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final TopicControl.Factory topicControlFactory,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final TopicQueryBuilder.Factory queryBuilder,
      final Provider<TopicQueryRewriter> queryRewriter) {
    super(schema, currentUser);
    this.currentUser = currentUser;
    this.topicControlFactory = topicControlFactory;
    this.accountInfoCacheFactory = accountInfoCacheFactory;
    this.queryBuilder = queryBuilder;
    this.queryRewriter = queryRewriter;
  }

  private boolean canRead(final Topic c) {
    try {
      return topicControlFactory.controlFor(c).isVisible();
    } catch (NoSuchTopicException e) {
      return false;
    }
  }

  @Override
  public void allQueryPrev(final String query, final String pos,
      final int pageSize, final AsyncCallback<SingleListTopicInfo> callback) {
    try {
      run(callback, new QueryPrev(pageSize, pos) {
        @Override
        ResultSet<Topic> query(ReviewDb db, int lim, String key)
            throws OrmException, InvalidQueryException {
          return searchQuery(db, query, lim, key, QUERY_PREV);
        }
      });
    } catch (InvalidQueryException e) {
      callback.onFailure(e);
    }
  }

  @Override
  public void allQueryNext(final String query, final String pos,
      final int pageSize, final AsyncCallback<SingleListTopicInfo> callback) {
    try {
      run(callback, new QueryNext(pageSize, pos) {
        @Override
        ResultSet<Topic> query(ReviewDb db, int lim, String key)
            throws OrmException, InvalidQueryException {
          return searchQuery(db, query, lim, key, QUERY_NEXT);
        }
      });
    } catch (InvalidQueryException e) {
      callback.onFailure(e);
    }
  }

  @SuppressWarnings("unchecked")
  private ResultSet<Topic> searchQuery(final ReviewDb db, String query,
      final int limit, final String key, final Comparator<Topic> cmp)
      throws OrmException, InvalidQueryException {
    try {
      final TopicQueryBuilder builder = queryBuilder.create(currentUser.get());
      final Predicate<TopicData> visibleToMe = builder.is_visible();
      Predicate<TopicData> q = builder.parse(query);
      q = Predicate.and(q, //
          cmp == QUERY_PREV //
              ? builder.sortkey_after(key) //
              : builder.sortkey_before(key), //
          builder.limit(limit), //
          visibleToMe //
          );

      TopicQueryRewriter rewriter = queryRewriter.get();
      Predicate<TopicData> s = rewriter.rewrite(q);
      if (!(s instanceof TopicDataSource)) {
        s = rewriter.rewrite(Predicate.and(builder.status_open(), q));
      }

      if (s instanceof TopicDataSource) {
        ArrayList<Topic> r = new ArrayList<Topic>();
        HashSet<Topic.Id> want = new HashSet<Topic.Id>();
        for (TopicData d : ((TopicDataSource) s).read()) {
          if (d.hasTopic()) {
            // Checking visibleToMe here should be unnecessary, the
            // query should have already performed it.  But we don't
            // want to trust the query rewriter that much yet.
            //
            if (visibleToMe.match(d)) {
              r.add(d.getTopic());
            }
          } else {
            want.add(d.getId());
          }
        }

        // Here we have to check canRead. Its impossible to
        // do that test without the topic object, and it being
        // missing above means we have to compute it ourselves.
        //
        if (!want.isEmpty()) {
          for (Topic t : db.topics().get(want)) {
            if (canRead(t)) {
              r.add(t);
            }
          }
        }

        Collections.sort(r, cmp);
        return new ListResultSet<Topic>(r);
      } else {
        throw new InvalidQueryException("Not Supported", s.toString());
      }
    } catch (QueryParseException e) {
      throw new InvalidQueryException(e.getMessage(), query);
    }
  }

  public void forAccount(final Account.Id id,
      final AsyncCallback<AccountTopicDashboardInfo> callback) {
    final Account.Id me = getAccountId();
    final Account.Id target = id != null ? id : me;
    if (target == null) {
      callback.onFailure(new NoSuchEntityException());
      return;
    }

    run(callback, new Action<AccountTopicDashboardInfo>() {
      public AccountTopicDashboardInfo run(final ReviewDb db) throws OrmException,
          Failure {
        final AccountInfoCacheFactory ac = accountInfoCacheFactory.create();
        final Account user = ac.get(target);
        if (user == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final TopicAccess topics = db.topics();
        final AccountTopicDashboardInfo d;

        final Set<Topic.Id> openReviews = new HashSet<Topic.Id>();
        final Set<Topic.Id> closedReviews = new HashSet<Topic.Id>();

        for(final ChangeSetApproval csa : db.changeSetApprovals().openByUser(id)) {
          openReviews.add(csa.getChangeSetId().getParentKey());
        }

        for(final ChangeSetApproval csa : db.changeSetApprovals().closedByUser(id)) {
          closedReviews.add(csa.getChangeSetId().getParentKey());
        }

        d = new AccountTopicDashboardInfo(target);
        d.setByOwner(filter(topics.byOwnerOpen(target), ac));
        d.setClosed(filter(topics.byOwnerClosed(target), ac));

        for (final TopicInfo t : d.getByOwner()) {
          openReviews.remove(t.getId());
        }
        d.setForReview(filter(topics.get(openReviews), ac));
        Collections.sort(d.getForReview(), ID_COMP);

        for (final TopicInfo t : d.getClosed()) {
          closedReviews.remove(t.getId());
        }
        if (!closedReviews.isEmpty()) {
          d.getClosed().addAll(filter(topics.get(closedReviews), ac));
          Collections.sort(d.getClosed(), SORT_KEY_COMP);
        }

        d.setAccounts(ac.create());
        return d;
      }
    });
  }

//  public void toggleStars(final ToggleStarRequest req,
//      final AsyncCallback<VoidResult> callback) {
//    run(callback, new Action<VoidResult>() {
//      public VoidResult run(final ReviewDb db) throws OrmException {
//        final Account.Id me = getAccountId();
//        final Set<Change.Id> existing = currentUser.get().getStarredChanges();
//        List<StarredChange> add = new ArrayList<StarredChange>();
//        List<StarredChange.Key> remove = new ArrayList<StarredChange.Key>();
//
//        if (req.getAddSet() != null) {
//          for (final Change.Id id : req.getAddSet()) {
//            if (!existing.contains(id)) {
//              add.add(new StarredChange(new StarredChange.Key(me, id)));
//            }
//          }
//        }
//
//        if (req.getRemoveSet() != null) {
//          for (final Change.Id id : req.getRemoveSet()) {
//            remove.add(new StarredChange.Key(me, id));
//          }
//        }
//
//        db.starredChanges().insert(add);
//        db.starredChanges().deleteKeys(remove);
//        return VoidResult.INSTANCE;
//      }
//    });
//  }

  //public void myStarredTopciIds(final AsyncCallback<Set<Topic.Id>> callback) {
  //  callback.onSuccess(currentUser.get().getStarredChanges());
  //}

  private int safePageSize(final int pageSize) throws InvalidQueryException {
    int maxLimit = currentUser.get().getCapabilities()
      .getRange(GlobalCapability.QUERY_LIMIT)
      .getMax();
    if (maxLimit <= 0) {
      throw new InvalidQueryException("Search Disabled");
    }
    return 0 < pageSize && pageSize <= maxLimit ? pageSize : maxLimit;
  }

  private List<TopicInfo> filter(final ResultSet<Topic> rs,
      final AccountInfoCacheFactory accts) {
    final ArrayList<TopicInfo> r = new ArrayList<TopicInfo>();
    for (final Topic t : rs) {
      if (canRead(t)) {
        final TopicInfo ci = new TopicInfo(t);
        accts.want(ci.getOwner());
        r.add(ci);
      }
    }
    return r;
  }

  private abstract class QueryNext implements Action<SingleListTopicInfo> {
    protected final String pos;
    protected final int limit;
    protected final int slim;

    QueryNext(final int pageSize, final String pos) throws InvalidQueryException {
      this.pos = pos;
      this.limit = safePageSize(pageSize);
      this.slim = limit + 1;
    }

    public SingleListTopicInfo run(final ReviewDb db) throws OrmException,
        InvalidQueryException {
      final AccountInfoCacheFactory ac = accountInfoCacheFactory.create();
      final SingleListTopicInfo d = new SingleListTopicInfo();

      final ArrayList<TopicInfo> list = new ArrayList<TopicInfo>();
      final ResultSet<Topic> rs = query(db, slim, pos);
      for (final Topic c : rs) {
        final TopicInfo ci = new TopicInfo(c);
        ac.want(ci.getOwner());
        list.add(ci);
        if (list.size() == slim) {
          rs.close();
          break;
        }
      }

      final boolean atEnd = finish(list);
      d.setTopics(list, atEnd);
      d.setAccounts(ac.create());
      return d;
    }

    boolean finish(final ArrayList<TopicInfo> list) {
      final boolean atEnd = list.size() <= limit;
      if (list.size() == slim) {
        list.remove(limit);
      }
      return atEnd;
    }

    abstract ResultSet<Topic> query(final ReviewDb db, final int slim,
        String sortKey) throws OrmException, InvalidQueryException;
  }

  private abstract class QueryPrev extends QueryNext {
    QueryPrev(int pageSize, String pos) throws InvalidQueryException {
      super(pageSize, pos);
    }

    @Override
    boolean finish(final ArrayList<TopicInfo> list) {
      final boolean atEnd = super.finish(list);
      Collections.reverse(list);
      return atEnd;
    }
  }
}
