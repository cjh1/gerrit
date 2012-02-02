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

import static com.google.gerrit.server.query.Patterns.PAT_EMAIL;

import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.TopicControl;
import com.google.gerrit.server.query.IntPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.SingleGroupUser;
import com.google.gerrit.server.query.topic.ProjectPredicate;
import com.google.gerrit.server.query.topic.RegexProjectPredicate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses a query string meant to be applied to topic objects.
 */
public class TopicQueryBuilder extends QueryBuilder<TopicData> {
  private static final Pattern PAT_TOPIC_ID =
      Pattern.compile("^[tT][\\w]{4,}$");

  public static final String FIELD_AGE = "age";
  public static final String FIELD_BRANCH = "branch";
  public static final String FIELD_COMMIT = "commit";
  public static final String FIELD_DRAFTBY = "draftby";
  public static final String FIELD_FILE = "file";
  public static final String FIELD_IS = "is";
  public static final String FIELD_HAS = "has";
  public static final String FIELD_LABEL = "label";
  public static final String FIELD_LIMIT = "limit";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_OWNER = "owner";
  public static final String FIELD_OWNERIN = "ownerin";
  public static final String FIELD_PROJECT = "project";
  public static final String FIELD_REF = "ref";
  public static final String FIELD_REVIEWER = "reviewer";
  public static final String FIELD_REVIEWERIN = "reviewerin";
  public static final String FIELD_STARREDBY = "starredby";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_TOPIC = "topic";
  public static final String FIELD_TR = "tr";
  public static final String FIELD_VISIBLETO = "visibleto";
  public static final String FIELD_WATCHEDBY = "watchedby";

  private static final QueryBuilder.Definition<TopicData, TopicQueryBuilder> mydef =
      new QueryBuilder.Definition<TopicData, TopicQueryBuilder>(
          TopicQueryBuilder.class);

  static class Arguments {
    final Provider<ReviewDb> dbProvider;
    final Provider<TopicQueryRewriter> rewriter;
    final IdentifiedUser.GenericFactory userFactory;
    final CapabilityControl.Factory capabilityControlFactory;
    final TopicControl.Factory topicControlFactory;
    final TopicControl.GenericFactory topicControlGenericFactory;
    final AccountResolver accountResolver;
    final GroupCache groupCache;
    final ApprovalTypes approvalTypes;
    final AllProjectsName allProjectsName;
    final PatchListCache patchListCache;
    final GitRepositoryManager repoManager;
    final ProjectCache projectCache;

    @Inject
    Arguments(Provider<ReviewDb> dbProvider,
        Provider<TopicQueryRewriter> rewriter,
        IdentifiedUser.GenericFactory userFactory,
        CapabilityControl.Factory capabilityControlFactory,
        TopicControl.Factory topicControlFactory,
        TopicControl.GenericFactory topicControlGenericFactory,
        AccountResolver accountResolver, GroupCache groupCache,
        ApprovalTypes approvalTypes,
        AllProjectsName allProjectsName,
        PatchListCache patchListCache,
        GitRepositoryManager repoManager,
        ProjectCache projectCache) {
      this.dbProvider = dbProvider;
      this.rewriter = rewriter;
      this.userFactory = userFactory;
      this.capabilityControlFactory = capabilityControlFactory;
      this.topicControlFactory = topicControlFactory;
      this.topicControlGenericFactory = topicControlGenericFactory;
      this.accountResolver = accountResolver;
      this.groupCache = groupCache;
      this.approvalTypes = approvalTypes;
      this.allProjectsName = allProjectsName;
      this.patchListCache = patchListCache;
      this.repoManager = repoManager;
      this.projectCache = projectCache;
    }
  }

  public interface Factory {
    TopicQueryBuilder create(CurrentUser user);
  }

  private final Arguments args;
  private final CurrentUser currentUser;
  private boolean allowsFile;

  @Inject
  TopicQueryBuilder(Arguments args, @Assisted CurrentUser currentUser) {
    super(mydef);
    this.args = args;
    this.currentUser = currentUser;
  }

  public void setAllowFile(boolean on) {
    allowsFile = on;
  }

  @Operator
  public Predicate<TopicData> topic(String query) {
    if (PAT_TOPIC_ID.matcher(query).matches()) {
      if (query.charAt(0) == 't') {
        query = "T" + query.substring(1);
      }
      return new TopicIdPredicate(args.dbProvider, query);
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<TopicData> status(String statusName) {
    if ("open".equals(statusName)) {
      return status_open();

    } else if ("closed".equals(statusName)) {
      return TopicStatusPredicate.closed(args.dbProvider);

    } else if ("reviewed".equalsIgnoreCase(statusName)) {
      return new IsReviewedPredicate(args.dbProvider);

    } else {
      return new TopicStatusPredicate(args.dbProvider, statusName);
    }
  }

  public Predicate<TopicData> status_open() {
    return TopicStatusPredicate.open(args.dbProvider);
  }

  @Operator
  public Predicate<TopicData> visibleto(String who)
      throws QueryParseException, OrmException {
    Account account = args.accountResolver.find(who);
    if (account != null) {
      return visibleto(args.userFactory
          .create(args.dbProvider, account.getId()));
    }

    // If its not an account, maybe its a group?
    //
    AccountGroup g = args.groupCache.get(new AccountGroup.NameKey(who));
    if (g != null) {
      return visibleto(new SingleGroupUser(args.capabilityControlFactory,
          g.getGroupUUID()));
    }

    Collection<AccountGroup> matches =
        args.groupCache.get(new AccountGroup.ExternalNameKey(who));
    if (matches != null && !matches.isEmpty()) {
      HashSet<AccountGroup.UUID> ids = new HashSet<AccountGroup.UUID>();
      for (AccountGroup group : matches) {
        ids.add(group.getGroupUUID());
      }
      return visibleto(new SingleGroupUser(args.capabilityControlFactory, ids));
    }

    throw error("No user or group matches \"" + who + "\".");
  }

  public Predicate<TopicData> visibleto(CurrentUser user) {
    return new IsVisibleToPredicate(args.dbProvider, //
        args.topicControlGenericFactory, //
        user);
  }

  public Predicate<TopicData> is_visible() {
    return visibleto(currentUser);
  }

  @Operator
  public Predicate<TopicData> owner(String who) throws QueryParseException,
      OrmException {
    Set<Account.Id> m = args.accountResolver.findAll(who);
    if (m.isEmpty()) {
      throw error("User " + who + " not found");
    } else if (m.size() == 1) {
      Account.Id id = m.iterator().next();
      return new OwnerPredicate(args.dbProvider, id);
    } else {
      List<OwnerPredicate> p = new ArrayList<OwnerPredicate>(m.size());
      for (Account.Id id : m) {
        p.add(new OwnerPredicate(args.dbProvider, id));
      }
      return Predicate.or(p);
    }
  }

  @Operator
  public Predicate<TopicData> reviewer(String who)
      throws QueryParseException, OrmException {
    Set<Account.Id> m = args.accountResolver.findAll(who);
    if (m.isEmpty()) {
      throw error("User " + who + " not found");
    } else if (m.size() == 1) {
      Account.Id id = m.iterator().next();
      return new ReviewerPredicate(args.dbProvider, id);
    } else {
      List<ReviewerPredicate> p = new ArrayList<ReviewerPredicate>(m.size());
      for (Account.Id id : m) {
        p.add(new ReviewerPredicate(args.dbProvider, id));
      }
      return Predicate.or(p);
    }
  }

  @Operator
  public Predicate<TopicData> limit(String limit) {
    return limit(Integer.parseInt(limit));
  }

  public Predicate<TopicData> limit(int limit) {
    return new IntPredicate<TopicData>(FIELD_LIMIT, limit) {
      @Override
      public boolean match(TopicData object) {
        return true;
      }

      @Override
      public int getCost() {
        return 0;
      }
    };
  }

  @Operator
  public Predicate<TopicData> sortkey_after(String sortKey) {
    return new SortKeyPredicate.After(args.dbProvider, sortKey);
  }

  @Operator
  public Predicate<TopicData> sortkey_before(String sortKey) {
    return new SortKeyPredicate.Before(args.dbProvider, sortKey);
  }

  @Operator
  public Predicate<TopicData> resume_sortkey(String sortKey) {
    return sortkey_before(sortKey);
  }

  @SuppressWarnings("unchecked")
  public boolean hasLimit(Predicate<TopicData> p) {
    return find(p, IntPredicate.class, FIELD_LIMIT) != null;
  }

  @SuppressWarnings("unchecked")
  public int getLimit(Predicate<TopicData> p) {
    return ((IntPredicate<?>) find(p, IntPredicate.class, FIELD_LIMIT)).intValue();
  }

  public boolean hasSortKey(Predicate<TopicData> p) {
    return find(p, SortKeyPredicate.class, "sortkey_after") != null
        || find(p, SortKeyPredicate.class, "sortkey_before") != null;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected Predicate<TopicData> defaultField(String query)
      throws QueryParseException {
    if (PAT_TOPIC_ID.matcher(query).matches()) {
      return topic(query);

    } else if (PAT_EMAIL.matcher(query).find()) {
      try {
        return Predicate.or(owner(query), reviewer(query));
      } catch (OrmException err) {
        throw error("Cannot lookup user", err);
      }
    } else {
      // Try to match a project name by substring query.
      final List<ProjectPredicate> predicate =
          new ArrayList<ProjectPredicate>();
      for (Project.NameKey name : args.projectCache.all()) {
        if (name.get().toLowerCase().contains(query.toLowerCase())) {
          predicate.add(new ProjectPredicate(args.dbProvider, name.get()));
        }
      }

      // If two or more projects contains "query" as substring create an
      // OrPredicate holding predicates for all these projects, otherwise if
      // only one contains that, return only that one predicate by itself.
      if (predicate.size() == 1) {
        return predicate.get(0);
      } else if (predicate.size() > 1) {
        return Predicate.or(predicate);
      }

      throw error("Unsupported query:" + query);
    }
  }

  @Operator
  public Predicate<TopicData> project(String name) {
    if (name.startsWith("^"))
      return new RegexProjectPredicate(args.dbProvider, name);
    return new ProjectPredicate(args.dbProvider, name);
  }
}
