// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Account.Id;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ChangeSetElement;
import com.google.gerrit.reviewdb.ChangeSetInfo;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.StarredChange;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.reviewdb.TopicMessage;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.TopicUtil;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.topic.TopicData;
import com.google.gerrit.server.query.topic.TopicQueryBuilder;
import com.google.gerrit.server.topic.ChangeSetInfoNotAvailableException;
import com.google.gwtorm.client.OrmException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sends an email to one or more interested parties. */
public abstract class TopicEmail extends ReviewEmail {
  protected final Topic topic;
  protected ChangeSet changeSet;
  protected ChangeSetInfo changeSetInfo;
  protected TopicData topicData;
  protected TopicEmail(EmailArguments ea, final Topic t, final String mc) {
    super(ea, mc);
    topic = t;
    topicData = topic != null ? new TopicData(topic) : null;
    emailOnlyAuthors = false;
  }

  public void setChangeSet(final ChangeSet cs) {
    changeSet = cs;
  }

  public void setChangeSet(final ChangeSet cs, final ChangeSetInfo csi) {
    changeSet = cs;
    changeSetInfo = csi;
  }

  public void setTopicMessage(final TopicMessage cm) {
    message = cm;
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected void format() throws EmailException {
    formatTopic();
    appendText(velocifyFile("ChangeFooter.vm"));
    formatReviewers(getReviewers());
  }

  protected Set<Account.Id> getReviewers()  {
    HashSet<Account.Id> reviewers = new HashSet<Account.Id>();
    try {
    for (ChangeSetApproval p : args.db.get().changeSetApprovals().byTopic(
        topic.getId())) {
      reviewers.add(p.getAccountId());
    }
    } catch (OrmException e) {
    }

    return reviewers;

  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected abstract void formatTopic() throws EmailException;

  /** Setup the message headers and envelope (TO, CC, BCC). */
  protected void init() throws EmailException {
    initProjectState(topic.getProject());

    if (changeSet == null) {
      try {
        changeSet = args.db.get().changeSets().get(topic.currentChangeSetId());
      } catch (OrmException err) {
        changeSet = null;
      }
    }

    if (changeSet != null && changeSetInfo == null) {
      try {
        changeSetInfo = args.changeSetInfoFactory.get(changeSet.getId());
      } catch (ChangeSetInfoNotAvailableException err) {
        changeSetInfo = null;
      } catch (OrmException e) {
        changeSetInfo = null;
      } catch (NoSuchEntityException e) {
        changeSetInfo = null;
      }
    }
    authors = getAuthors();

    super.init();

    if (message != null && message.getWrittenOn() != null) {
      setHeader("Date", new Date(message.getWrittenOn().getTime()));
    }
    setTopicSubjectHeader();
    setHeader("X-Gerrit-Topic-Id", "" + topic.getKey().get());
    setListIdHeader();
    setTopicUrlHeader();
    setCommitIdHeader();
  }

  private void initProjectState(Project.NameKey project) {
    if (args.projectCache != null) {
      projectState = args.projectCache.get(project);
    } else {
      projectState = null;
    }
  }

  private void setTopicUrlHeader() {
    final String u = getTopicUrl();
    if (u != null) {
      setHeader("X-Gerrit-TopicURL", "<" + u + ">");
    }
  }

  private void setCommitIdHeader() {
    if (changeSet != null) {

      try {
        RevId tip = TopicUtil.getChangeSetTip(args.db.get(), changeSet);
        if(tip != null && tip.get() != null && tip.get().length() > 0) {
          setCommitIdHeader(tip);
        }
      } catch (OrmException e) {
      }
    }
  }

  private void setTopicSubjectHeader() throws EmailException {
    setSubjectHeader("ChangeSubject.vm"); // FIXME: This can be reused for now
  }

  public String getChangeMessageThreadId() throws EmailException {
    return velocify("<gerrit.${topic.createdOn.time}.$topic.key.get()" +
                    "@$email.gerritHost>");
  }

  protected void formatTopicDetail() {
    appendText(getTopicDetail());
  }

  /** Create the change message and the affected file list. */
  public String getTopicDetail() {
    StringBuilder detail = new StringBuilder();

    if (changeSetInfo != null) {
      detail.append(changeSetInfo.getMessage().trim() + "\n");
    } else {
      detail.append(topic.getSubject().trim() + "\n");
    }

    detail.append("---\n");

    if (changeSet != null) {

      try {

        List<ChangeSetElement> changes = args.db.get().changeSetElements().byChangeSet(changeSet.getId()).toList();

        for (ChangeSetElement cse : changes) {
          Change c = args.db.get().changes().get(cse.getChangeId());
          detail.append(c.getKey());
          detail.append(" ").append(c.getSubject());
          detail.append("\n");
        }

        detail.append("\n");
      } catch(OrmException oex) {

      }
    }

    return detail.toString();
  }


  /** Get the groups which own the project. */
  protected Set<AccountGroup.UUID> getProjectOwners() {
    final ProjectState r;

    r = args.projectCache.get(topic.getProject());
    return r != null ? r.getOwners() : Collections.<AccountGroup.UUID> emptySet();
  }

  /** TO or CC all vested parties (change owner, patch set uploader, author). */
  protected void rcptToAuthors(final RecipientType rt) {
    for (final Account.Id id : authors) {
      add(rt, id);
    }
  }

  /** BCC any user who has starred this change. */
  protected void bccStarredBy() {
// Currently no support for topic stars
//    try {
//      // BCC anyone who has starred this change.
//      //
//
//      for (StarredChange w : args.db.get().starredChanges().byChange(
//          change.getId())) {
//        add(RecipientType.BCC, w.getAccountId());
//      }
//    } catch (OrmException err) {
//      // Just don't BCC everyone. Better to send a partial message to those
//      // we already have queued up then to fail deliver entirely to people
//      // who have a lower interest in the change.
//    }
  }

  /** Returns all watches that are relevant */
  protected final List<AccountProjectWatch> getWatches() throws OrmException {
    if (topicData == null) {
      return Collections.emptyList();
    }

    List<AccountProjectWatch> matching = new ArrayList<AccountProjectWatch>();
    Set<Account.Id> projectWatchers = new HashSet<Account.Id>();

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(topic.getProject())) {
      projectWatchers.add(w.getAccountId());
      add(matching, w);
    }

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(args.allProjectsName)) {
      if (!projectWatchers.contains(w.getAccountId())) {
        add(matching, w);
      }
    }

    return Collections.unmodifiableList(matching);
  }

  @SuppressWarnings("unchecked")
  private void add(List<AccountProjectWatch> matching, AccountProjectWatch w)
      throws OrmException {
    IdentifiedUser user =
        args.identifiedUserFactory.create(args.db, w.getAccountId());
    TopicQueryBuilder qb = args.topicQueryBuilder.create(user);
    Predicate<TopicData> p = qb.is_visible();
    if (w.getFilter() != null) {
      try {
        qb.setAllowFile(true);
        p = Predicate.and(qb.parse(w.getFilter()), p);
        p = args.topicQueryRewriter.get().rewrite(p);
        if (p.match(topicData)) {
          matching.add(w);
        }
      } catch (QueryParseException e) {
        // Ignore broken filter expressions.
      }
    } else if (p.match(topicData)) {
      matching.add(w);
    }
  }

  /** Any user who has published comments on this change. */
  protected void ccAllApprovals() {
    ccApprovals(true);
  }

  /** Users who have non-zero approval codes on the change. */
  protected void ccExistingReviewers() {
    ccApprovals(false);
  }

  private void ccApprovals(final boolean includeZero) {
    try {
      // CC anyone else who has posted an approval mark on this topic
      //
      for (ChangeSetApproval ap : args.db.get().changeSetApprovals().byTopic(
          topic.getId())) {
        if (!includeZero && ap.getValue() == 0) {
          continue;
        }
        add(RecipientType.CC, ap.getAccountId());
      }
    } catch (OrmException err) {
    }
  }

  protected boolean isVisibleTo(final Account.Id to) {
    return projectState == null
        || topic == null
        || projectState.controlFor(args.identifiedUserFactory.create(to))
            .controlFor(topic).isVisible();
  }

  /** Find all users who are authors of any part of this change. */
  protected Set<Account.Id> getAuthors() {
    Set<Account.Id> authors = new HashSet<Account.Id>();

    authors.add(topic.getOwner());
    if (changeSet != null) {
      authors.add(changeSet.getUploader());

      try {
        List<ChangeSetElement> changes = args.db.get().changeSetElements().byChangeSet(changeSet.getId()).toList();

        for (ChangeSetElement cse : changes) {
          Change c = args.db.get().changes().get(cse.getChangeId());
          authors.add(c.getOwner());

          PatchSet currentPatchSet = args.db.get().patchSets().get(c.currentPatchSetId());
          authors.add(currentPatchSet.getUploader());

          try {
            PatchSetInfo patchSetInfo = args.patchSetInfoFactory.get(currentPatchSet.getId());
            authors.add(patchSetInfo.getAuthor().getAccount());
            authors.add(patchSetInfo.getCommitter().getAccount());
          } catch (PatchSetInfoNotAvailableException err) {
          }
        }
      } catch (OrmException e) {
      }
    }

    if (changeSetInfo != null) {
      authors.add(changeSetInfo.getAuthor().getAccount());
    }

    return authors;
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("topic", topic);
    velocityContext.put("topicId", topic.getKey());
    velocityContext.put("coverLetter", getCoverLetter());
    velocityContext.put("branch", topic.getDest());
    velocityContext.put("fromName", getNameFor(fromId));
    velocityContext.put("projectName", //
        projectState != null ? projectState.getProject().getName() : null);
    velocityContext.put("changeSet", changeSet);
    velocityContext.put("changeSetInfo", changeSetInfo);
  }

  /** Get a link to the change; null if the server doesn't know its own address. */
  public String getTopicUrl() {
    if (topic != null && getGerritUrl() != null) {

      return TopicUtil.getTopicUrl(getGerritUrl(), topic.getId());

    }
    return null;
  }
}
