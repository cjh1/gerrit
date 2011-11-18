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

import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ChangeSetApprovalSummary;
import com.google.gerrit.common.data.ChangeSetApprovalSummarySet;
import com.google.gerrit.common.data.ChangeSetDetail;
import com.google.gerrit.common.data.ChangeSetPublishDetail;
import com.google.gerrit.common.data.IncludedInDetail;
import com.google.gerrit.common.data.TopicDetail;
import com.google.gerrit.common.data.TopicDetailService;
import com.google.gerrit.common.data.TopicReviewerResult;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.TopicControl;
import com.google.gerrit.server.topic.PublishTopicComments;
import com.google.gerrit.server.workflow.TopicFunctionState;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/*
 * @TODO cjh might want to split changeset methods into separate service
 */
class TopicDetailServiceImpl extends BaseServiceImplementation implements TopicDetailService {
  private final AddTopicReviewerHandler.Factory addTopicReviewerFactory;
  private final ChangeSetDetailFactory.Factory changeSetDetail;
  private final ChangeSetPublishDetailFactory.Factory changeSetPublishDetail;
  private final PublishTopicComments.Factory publishCommentsFactory;
  private final RemoveTopicReviewerHandler.Factory removeTopicReviewerFactory;
  private final TopicDetailFactory.Factory topicDetail;
  private final IncludedInDetailHandler.Factory includedInDetailHandler;
  private final AccountInfoCacheFactory.Factory accountInfoCacheFactory;
  private final TopicControl.Factory topicControlFactory;
  private final TopicFunctionState.Factory functionStateFactory;
  private final ApprovalTypes approvalTypes;

  @Inject
  TopicDetailServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final AddTopicReviewerHandler.Factory addTopicReviewerFactory,
      final ChangeSetDetailFactory.Factory changeSetDetail,
      final ChangeSetPublishDetailFactory.Factory changeSetPublishDetail,
      final PublishTopicComments.Factory publishCommentsFactory,
      final RemoveTopicReviewerHandler.Factory removeTopicReviewerFactory,
      final TopicDetailFactory.Factory topicDetail,
      final IncludedInDetailHandler.Factory includedInDetailHandler,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final TopicControl.Factory topicControlFactory,
      final TopicFunctionState.Factory functionStateFactory,
      final ApprovalTypes approvalTypes) {
    super(schema, currentUser);
    this.addTopicReviewerFactory = addTopicReviewerFactory;
    this.changeSetDetail = changeSetDetail;
    this.changeSetPublishDetail = changeSetPublishDetail;
    this.publishCommentsFactory = publishCommentsFactory;
    this.removeTopicReviewerFactory = removeTopicReviewerFactory;
    this.topicDetail = topicDetail;
    this.includedInDetailHandler = includedInDetailHandler;
    this.accountInfoCacheFactory = accountInfoCacheFactory;
    this.topicControlFactory = topicControlFactory;
    this.functionStateFactory = functionStateFactory;
    this.approvalTypes = approvalTypes;
  }

  @Override
  public void topicDetail(final Topic.Id id,
      final AsyncCallback<TopicDetail> callback) {
    topicDetail.create(id).to(callback);
  }

  @Override
  public void includedInDetail(final Topic.Id id,
      final AsyncCallback<IncludedInDetail> callback) {
    includedInDetailHandler.create(id).to(callback);
  }

  @Override
  public void changeSetDetail(final ChangeSet.Id idA,
      final AsyncCallback<ChangeSetDetail> callback) {
    changeSetDetail.create(idA).to(callback);
  }

  @Override
  public void changeSetPublishDetail(final ChangeSet.Id id,
      final AsyncCallback<ChangeSetPublishDetail> callback) {
    changeSetPublishDetail.create(id).to(callback);
  }

  @Override
  public void addTopicReviewers(final Topic.Id id, final List<String> reviewers,
      final boolean confirmed, final AsyncCallback<TopicReviewerResult> callback) {
    addTopicReviewerFactory.create(id, reviewers, confirmed).to(callback);
  }

  @Override
  public void removeTopicReviewer(final Topic.Id id, final Account.Id reviewerId,
      final AsyncCallback<TopicReviewerResult> callback) {
    removeTopicReviewerFactory.create(id, reviewerId).to(callback);
  }

  @Override
  public void publishComments(final ChangeSet.Id csid, final String msg,
      final Set<ApprovalCategoryValue.Id> tags,
      final AsyncCallback<VoidResult> callback) {
    Handler.wrap(publishCommentsFactory.create(csid, msg, tags)).to(callback);
  }

  @Override
  public void userApprovals(final Set<Topic.Id> cids, final Account.Id aid,
      final AsyncCallback<ChangeSetApprovalSummarySet> callback) {
    run(callback, new Action<ChangeSetApprovalSummarySet>() {
      public ChangeSetApprovalSummarySet run(ReviewDb db) throws OrmException {
        final Map<Topic.Id, ChangeSetApprovalSummary> approvals =
            new HashMap<Topic.Id, ChangeSetApprovalSummary>();
        final AccountInfoCacheFactory aicFactory =
            accountInfoCacheFactory.create();

        aicFactory.want(aid);
        for (final Topic.Id id : cids) {
          try {
            final TopicControl tc = topicControlFactory.validateFor(id);
            final Topic topic = tc.getTopic();
            final ChangeSet.Id cs_id = topic.currentChangeSetId();
            final Map<ApprovalCategory.Id, ChangeSetApproval> csas =
                new HashMap<ApprovalCategory.Id, ChangeSetApproval>();
            final TopicFunctionState fs =
                functionStateFactory.create(topic, cs_id, csas.values());

            for (final ChangeSetApproval ca : db.changeSetApprovals()
                .byChangeSetUser(cs_id, aid)) {
              final ApprovalCategory.Id category = ca.getCategoryId();
              if (ApprovalCategory.SUBMIT.equals(category)) {
                continue;
              }
              if (topic.getStatus().isOpen()) {
                fs.normalize(approvalTypes.byId(category), ca);
              }
              if (ca.getValue() == 0) {
                continue;
              }
              csas.put(category, ca);
            }

            approvals.put(id, new ChangeSetApprovalSummary(csas.values()));
          } catch (NoSuchTopicException nsce) {
            /*
             * The user has no access to see this change, so we simply do not
             * provide any details about it.
             */
          }
        }
        return new ChangeSetApprovalSummarySet(aicFactory.create(), approvals);
      }
    });
  }

  public void strongestApprovals(final Set<Topic.Id> tids,
      final AsyncCallback<ChangeSetApprovalSummarySet> callback) {
    run(callback, new Action<ChangeSetApprovalSummarySet>() {
      public ChangeSetApprovalSummarySet run(ReviewDb db) throws OrmException {
        final Map<Topic.Id, ChangeSetApprovalSummary> approvals =
            new HashMap<Topic.Id, ChangeSetApprovalSummary>();
        final AccountInfoCacheFactory aicFactory =
            accountInfoCacheFactory.create();

        for (final Topic.Id id : tids) {
          try {
            final TopicControl tc = topicControlFactory.validateFor(id);
            final Topic topic = tc.getTopic();
            final ChangeSet.Id cs_id = topic.currentChangeSetId();
            final Map<ApprovalCategory.Id, ChangeSetApproval> psas =
                new HashMap<ApprovalCategory.Id, ChangeSetApproval>();
            final TopicFunctionState fs =
                functionStateFactory.create(topic, cs_id, psas.values());

            for (ChangeSetApproval ca : db.changeSetApprovals().byChangeSet(cs_id)) {
              final ApprovalCategory.Id category = ca.getCategoryId();
              if (ApprovalCategory.SUBMIT.equals(category)) {
                continue;
              }
              if (topic.getStatus().isOpen()) {
                fs.normalize(approvalTypes.byId(category), ca);
              }
              if (ca.getValue() == 0) {
                continue;
              }
              boolean keep = true;
              if (psas.containsKey(category)) {
                final short oldValue = psas.get(category).getValue();
                final short newValue = ca.getValue();
                keep =
                    (Math.abs(oldValue) < Math.abs(newValue))
                        || ((Math.abs(oldValue) == Math.abs(newValue) && (newValue < oldValue)));
              }
              if (keep) {
                aicFactory.want(ca.getAccountId());
                psas.put(category, ca);
              }
            }

            approvals.put(id, new ChangeSetApprovalSummary(psas.values()));
          } catch (NoSuchTopicException nsce) {
            /*
             * The user has no access to see this change, so we simply do not
             * provide any details about it.
             */
          }
        }

        return new ChangeSetApprovalSummarySet(aicFactory.create(), approvals);
      }
    });
  }

}
