// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.EventHookRunner;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.TopicUtil;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RestoredSender;
import com.google.gerrit.server.patch.PublishComments;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.TopicControl;
import com.google.gerrit.server.topic.PublishTopicComments;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gerrit.server.workflow.TopicFunctionState;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReviewCommand extends BaseCommand {
  private static final Logger log =
      LoggerFactory.getLogger(ReviewCommand.class);

  private static final String TOPIC_ID_REGEX = "^([tT][\\w\\-]+),?(\\d*)$";

  @Override
  protected final CmdLineParser newCmdLineParser() {
    final CmdLineParser parser = super.newCmdLineParser();
    for (ApproveOption c : optionList) {
      parser.addOption(c, c);
    }
    return parser;
  }

  private final Set<PatchSet.Id> patchSetIds = new HashSet<PatchSet.Id>();
  private ChangeSet changeSet;
  private Topic topic;

  @Argument(index = 0, required = true, multiValued = true, metaVar = "{COMMIT | CHANGE,PATCHSET | TOPIC,CHANGESET}", usage = "patch to review")
  void addPatchSetId(final String token) {
    try {

      if(token.matches(TOPIC_ID_REGEX))
      {
        parseTopicId(token);
      }
      else
      {
        patchSetIds.addAll(parsePatchSetId(token));
      }
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database error", e);
    }
  }

  @Option(name = "--project", aliases = "-p", usage = "project containing the patch set/change set")
  private ProjectControl projectControl;

  @Option(name = "--message", aliases = "-m", usage = "cover message to publish on change/topic", metaVar = "MESSAGE")
  private String comment;

  @Option(name = "--abandon", usage = "abandon the patch set/change set")
  private boolean abandon;

  @Option(name = "--restore", usage = "restore an abandoned the patch set/change set")
  private boolean restore;

  @Option(name = "--submit", aliases = "-s", usage = "submit the patch set/change set")
  private boolean submit;

  @Inject
  private ReviewDb db;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private MergeQueue merger;

  @Inject
  private MergeOp.Factory opFactory;

  @Inject
  private ApprovalTypes approvalTypes;

  @Inject
  private ChangeControl.Factory changeControlFactory;

  @Inject
  private TopicControl.Factory topicControlFactory;


  @Inject
  private AbandonedSender.Factory abandonedSenderFactory;

  @Inject
  private FunctionState.Factory functionStateFactory;

  @Inject
  private TopicFunctionState.Factory topicFunctionStateFactory;

  @Inject
  private PublishComments.Factory publishCommentsFactory;

  @Inject
  private PublishTopicComments.Factory publishTopicCommentsFactory;

  @Inject
  private RestoredSender.Factory restoredSenderFactory;

  @Inject
  private EventHookRunner hooks;

  private List<ApproveOption> optionList;

  private Set<PatchSet.Id> toSubmit = new HashSet<PatchSet.Id>();
  private Set<ChangeSet.Id> changeSetsToSubmit = new HashSet<ChangeSet.Id>();

  @Override
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        initOptionList();
        parseCommandLine();
        if (abandon) {
          if (restore) {
            throw error("abandon and restore actions are mutually exclusive");
          }
          if (submit) {
            throw error("abandon and submit actions are mutually exclusive");
          }
        }

        if (comment == null) {
          comment = "";
        }

        review();
      }
    });
  }

  private void review() throws UnloggedFailure, Failure {

    if(topic != null)
    {
      reviewChangeSet();
    }
    else
    {
      reviewPatchSets();
    }
  }

  private void reviewChangeSet() throws Failure {
    boolean ok = true;

    try
    {
      reviewChangeSet(topic, changeSet);
    } catch (UnloggedFailure e) {
      ok = false;
      writeError("error: " + e.getMessage() + "\n");
    } catch (Exception e) {
      ok = false;
      writeError("fatal: internal server error while approving "
          + topic.getKey() + "\n");
      log.error("internal error while approving " + topic.getKey(), e);
    }

    if (!ok) {
      throw new UnloggedFailure(1, "one or more approvals failed;"
          + " review output above");
    }
  }

  /*
   * Submits changes associated with a topic review
   *
   */
  private void submitChangeSetUpdates() throws Failure {
    if (!changeSetsToSubmit.isEmpty()) {
      final Set<Branch.NameKey> toMerge = new HashSet<Branch.NameKey>();
      try {
        for (ChangeSet.Id changeSetId : changeSetsToSubmit) {
          TopicUtil.submit(changeSetId, currentUser, db, opFactory,
              new MergeQueue() {
                @Override
                public void merge(MergeOp.Factory mof, Branch.NameKey branch) {
                  toMerge.add(branch);
                }

                @Override
                public void schedule(Branch.NameKey branch) {
                  toMerge.add(branch);
                }

                @Override
                public void recheckAfter(Branch.NameKey branch, long delay,
                    TimeUnit delayUnit) {
                  toMerge.add(branch);
                }
              });
        }
        for (Branch.NameKey branch : toMerge) {
          merger.merge(opFactory, branch);
        }
      } catch (OrmException updateError) {
        throw new Failure(1, "one or more submits failed", updateError);
      }
    }
  }

  /*
   * Submit changes associated with a Change review
   */
  private void submitPatchSetUpdates() throws Failure {
    if (!toSubmit.isEmpty()) {
      final Set<Branch.NameKey> toMerge = new HashSet<Branch.NameKey>();
      try {
        for (PatchSet.Id patchSetId : toSubmit) {
          ChangeUtil.submit(patchSetId, currentUser, db, opFactory,
              new MergeQueue() {
                @Override
                public void merge(MergeOp.Factory mof, Branch.NameKey branch) {
                  toMerge.add(branch);
                }

                @Override
                public void schedule(Branch.NameKey branch) {
                  toMerge.add(branch);
                }

                @Override
                public void recheckAfter(Branch.NameKey branch, long delay,
                    TimeUnit delayUnit) {
                  toMerge.add(branch);
                }
              });
        }
        for (Branch.NameKey branch : toMerge) {
          merger.merge(opFactory, branch);
        }
      } catch (OrmException updateError) {
        throw new Failure(1, "one or more submits failed", updateError);
      }
    }
  }

  private void reviewPatchSet(final PatchSet.Id patchSetId) throws
      NoSuchChangeException, UnloggedFailure,
      OrmException, EmailException, Failure {
    Change.Id changeId = patchSetId.getParentKey();
    ChangeControl changeControl = changeControlFactory.validateFor(changeId);

    updatePatchSetApprovals(patchSetId, changeControl);

    try {

      if (abandon) {
        abandonPatchSet(patchSetId, changeControl);
      }

      if (restore) {
        changeControl = restorePatchSet(patchSetId, changeId, changeControl);
      }

      if (submit) {
        submitPatchSet(patchSetId, changeId, changeControl);
      }
    } catch (InvalidChangeOperationException e) {
      throw error(e.getMessage());
    }
  }

  private void updatePatchSetApprovals(final PatchSet.Id patchSetId,
      ChangeControl changeControl) throws UnloggedFailure,
      NoSuchChangeException, OrmException {
    try {
      Set<ApprovalCategoryValue.Id> aps = new HashSet<ApprovalCategoryValue.Id>();
      for (ApproveOption ao : optionList) {
        Short v = ao.value();
        if (v != null) {
          assertScoreIsAllowed(patchSetId, changeControl, ao, v);
          aps.add(new ApprovalCategoryValue.Id(ao.getCategoryId(), v));
        }
      }

      publishCommentsFactory.create(patchSetId, comment, aps).call();
    } catch (InvalidChangeOperationException e) {
      throw error(e.getMessage());
    }
  }

  private void submitPatchSet(final PatchSet.Id patchSetId, Change.Id changeId,
      ChangeControl changeControl) throws Failure, UnloggedFailure {
    List<SubmitRecord> result = changeControl.canSubmit(db, patchSetId);
    if (result.isEmpty()) {
      throw new Failure(1, "ChangeControl.canSubmit returned empty list");
    }
    switch (result.get(0).status) {
      case OK:
        if (changeControl.getRefControl().canSubmit()) {
          toSubmit.add(patchSetId);
        } else {
          throw error("change " + changeId + ": you do not have submit permission");
        }
        break;

      case NOT_READY: {
        StringBuilder msg = new StringBuilder();
        for (SubmitRecord.Label lbl : result.get(0).labels) {
          switch (lbl.status) {
            case OK:
              break;

            case REJECT:
              if (msg.length() > 0) msg.append("\n");
              msg.append("change " + changeId + ": blocked by " + lbl.label);
              break;

            case NEED:
              if (msg.length() > 0) msg.append("\n");
              msg.append("change " + changeId + ": needs " + lbl.label);
              break;

            case IMPOSSIBLE:
              if (msg.length() > 0) msg.append("\n");
              msg.append("change " + changeId + ": needs " + lbl.label
                  + " (check project access)");
              break;

            default:
              throw new Failure(1, "Unsupported label status " + lbl.status);
          }
        }
        throw error(msg.toString());
      }

      case CLOSED:
        throw error("change " + changeId + " is closed");

      case RULE_ERROR:
        if (result.get(0).errorMessage != null) {
          throw error("change " + changeId + ": " + result.get(0).errorMessage);
        } else {
          throw error("change " + changeId + ": internal rule error");
        }

      default:
        throw new Failure(1, "Unsupported status " + result.get(0).status);
    }
  }

  private ChangeControl restorePatchSet(final PatchSet.Id patchSetId,
      Change.Id changeId, ChangeControl changeControl)
      throws NoSuchChangeException, InvalidChangeOperationException,
      EmailException, OrmException, UnloggedFailure {
    if (changeControl.canRestore()) {
      ChangeUtil.restore(patchSetId, currentUser, comment, db,
          restoredSenderFactory, hooks);
    } else {
      throw error("Not permitted to restore change");
    }
    if (submit) {
      changeControl = changeControlFactory.validateFor(changeId);
    }
    return changeControl;
  }

  private void abandonPatchSet(final PatchSet.Id patchSetId,
      ChangeControl changeControl) throws NoSuchChangeException,
      InvalidChangeOperationException, EmailException, OrmException,
      UnloggedFailure {
    if (changeControl.canAbandon()) {
      ChangeUtil.abandon(patchSetId, currentUser, comment, db,
          abandonedSenderFactory, hooks);
    } else {
      throw error("Not permitted to abandon change");
    }
  }

  private void reviewChangeSet(Topic topic, ChangeSet changeSet)
      throws NoSuchTopicException, UnloggedFailure, OrmException,
      NoSuchChangeException, EmailException, Failure {

    TopicControl topicControl = topicControlFactory.validateFor(changeSet.getTopicId());

    updateApprovals(topic, changeSet, topicControl);

    try {
      if (abandon) {
        abandonChangeSet(changeSet, topicControl);
      }

      if (restore) {
        restoreChangeSet(changeSet, topicControl);
      }

      if (submit) {
        submitChangeSet(topic, changeSet, topicControl);
        submitChangeSetUpdates();
      }
    } catch (InvalidChangeOperationException e) {
      throw error(e.getMessage());
    }
  }

  private void submitChangeSet(Topic topic, ChangeSet changeSet,
      TopicControl topicControl) throws NoSuchChangeException, OrmException,
      Failure, UnloggedFailure {
    List<SubmitRecord> result = topicControl.canSubmit(db, changeSet.getId(),
                                                changeControlFactory, approvalTypes, topicFunctionStateFactory);
    if (result.isEmpty()) {
      throw new Failure(1, "TopicControl.canSubmit returned empty list");
    }

    String topicKey = topic.getKey().abbreviate();

    switch (result.get(0).status) {
      case OK:
        if (topicControl.getRefControl().canSubmit()) {
          changeSetsToSubmit.add(changeSet.getId());
        } else {
          throw error("topic " + topicKey + ": you do not have submit permission");
        }
        break;

      case NOT_READY: {
        StringBuilder msg = new StringBuilder();
        for (SubmitRecord.Label lbl : result.get(0).labels) {
          switch (lbl.status) {
            case OK:
              break;

            case REJECT:
              if (msg.length() > 0) msg.append("\n");
              msg.append("topic " + topicKey + ": blocked by " + lbl.label);
              break;

            case NEED:
              if (msg.length() > 0) msg.append("\n");
              msg.append("topic " + topicKey + ": needs " + lbl.label);
              break;

            case IMPOSSIBLE:
              if (msg.length() > 0) msg.append("\n");
              msg.append("topic " + topicKey + ": needs " + lbl.label
                  + " (check project access)");
              break;

            default:
              throw new Failure(1, "Unsupported label status " + lbl.status);
          }
        }
        throw error(msg.toString());
      }

      case CLOSED:
        throw error("topic " + topicKey + " is closed");

      case RULE_ERROR:
        if (result.get(0).errorMessage != null) {
          throw error("topic " + topicKey + ": " + result.get(0).errorMessage);
        } else {
          throw error("topic " + topicKey + ": internal rule error");
        }

      default:
        throw new Failure(1, "Unsupported status " + result.get(0).status);
    }
  }

  private void restoreChangeSet(ChangeSet changeSet, TopicControl topicControl)
      throws NoSuchChangeException, NoSuchTopicException,
      InvalidChangeOperationException, EmailException, OrmException,
      UnloggedFailure {
    if (topicControl.canRestore()) {
      TopicUtil.restore(changeSet.getId(), currentUser, comment, db,
          restoredSenderFactory, hooks);
    } else {
      throw error("Not permitted to restore change");
    }
  }

  private void abandonChangeSet(ChangeSet changeSet, TopicControl topicControl)
      throws NoSuchTopicException, NoSuchChangeException,
      InvalidChangeOperationException, EmailException, OrmException,
      UnloggedFailure {
    if (topicControl.canAbandon()) {
      TopicUtil.abandon(changeSet.getId(), currentUser, comment, db,
          abandonedSenderFactory, hooks);
    } else {
      throw error("Not permitted to abandon change");
    }
  }

  private void updateApprovals(Topic topic, ChangeSet changeSet,
      TopicControl topicControl) throws UnloggedFailure, NoSuchTopicException,
      OrmException {
    Set<ApprovalCategoryValue.Id> aps = new HashSet<ApprovalCategoryValue.Id>();
    for (ApproveOption ao : optionList) {
      Short v = ao.value();
      if (v != null) {
        assertScoreIsAllowed(topic, changeSet.getId(), topicControl, ao, v);
        aps.add(new ApprovalCategoryValue.Id(ao.getCategoryId(), v));
      }
    }

    publishTopicCommentsFactory.create(changeSet.getId(), comment, aps).call();
  }

  private ChangeSet.Id parseTopicId(final String changeSetIdentity) throws OrmException, UnloggedFailure
  {
    Matcher m = Pattern.compile(TOPIC_ID_REGEX).matcher(changeSetIdentity);
    m.matches();

    String topicId = m.group(1);

    List<Topic> topics = db.topics().byKey(new Topic.Key(topicId)).toList();

    if(topics.size() == 0)
      throw error("\"" + topicId + "\" no such topic");

    topic = topics.get(0);
    String changeSetIdStr = m.group(2).trim();
    if(!changeSetIdStr.isEmpty())
    {
      List<ChangeSet> changes = db.changeSets().byTopic(topic.getId()).toList();

      int change = Integer.parseInt(changeSetIdStr);

      if(changes.size() == 0 || change-1 > changes.size() ||
         change-1 < 0)
        throw error("\"" + changeSetIdStr + "\" no such change set");

      changeSet = changes.get(change-1);
    }
    else
    {
      changeSet = db.changeSets().get(topic.currentChangeSetId());
    }

    return changeSet.getId();
  }


  private Set<PatchSet.Id> parsePatchSetId(final String patchIdentity)
      throws UnloggedFailure, OrmException {
    // By commit?
    //
    if (patchIdentity.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      final RevId id = new RevId(patchIdentity);
      final ResultSet<PatchSet> patches;
      if (id.isComplete()) {
        patches = db.patchSets().byRevision(id);
      } else {
        patches = db.patchSets().byRevisionRange(id, id.max());
      }

      final Set<PatchSet.Id> matches = new HashSet<PatchSet.Id>();
      for (final PatchSet ps : patches) {
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (inProject(change)) {
          matches.add(ps.getId());
        }
      }

      switch (matches.size()) {
        case 1:
          return matches;
        case 0:
          throw error("\"" + patchIdentity + "\" no such patch set");
        default:
          throw error("\"" + patchIdentity + "\" matches multiple patch sets");
      }
    }

    // By older style change,patchset?
    //
    if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
      final PatchSet.Id patchSetId;
      try {
        patchSetId = PatchSet.Id.parse(patchIdentity);
      } catch (IllegalArgumentException e) {
        throw error("\"" + patchIdentity + "\" is not a valid patch set");
      }
      if (db.patchSets().get(patchSetId) == null) {
        throw error("\"" + patchIdentity + "\" no such patch set");
      }
      if (projectControl != null) {
        final Change change = db.changes().get(patchSetId.getParentKey());
        if (!inProject(change)) {
          throw error("change " + change.getId() + " not in project "
              + projectControl.getProject().getName());
        }
      }
      return Collections.singleton(patchSetId);
    }

    throw error("\"" + patchIdentity + "\" is not a valid patch set");
  }

  private boolean inProject(final Change change) {
    if (projectControl == null) {
      // No --project option, so they want every project.
      return true;
    }
    return projectControl.getProject().getNameKey().equals(change.getProject());
  }

  private void assertScoreIsAllowed(final PatchSet.Id patchSetId,
      final ChangeControl changeControl, ApproveOption ao, Short v)
      throws UnloggedFailure {
    final PatchSetApproval psa =
        new PatchSetApproval(new PatchSetApproval.Key(patchSetId, currentUser
            .getAccountId(), ao.getCategoryId()), v);
    final FunctionState fs =
        functionStateFactory.create(changeControl, patchSetId,
            Collections.<PatchSetApproval> emptyList());
    psa.setValue(v);
    fs.normalize(approvalTypes.byId(psa.getCategoryId()), psa);
    if (v != psa.getValue()) {
      throw error(ao.name() + "=" + ao.value() + " not permitted");
    }
  }

  private void assertScoreIsAllowed(final Topic topic, final ChangeSet.Id changeSetId,
      final TopicControl topicControl, ApproveOption ao, Short v)
      throws UnloggedFailure {
    final ChangeSetApproval psa =
        new ChangeSetApproval(new ChangeSetApproval.Key(changeSetId, currentUser
            .getAccountId(), ao.getCategoryId()), v);
    final TopicFunctionState fs =
        topicFunctionStateFactory.create(topic, changeSetId,
            Collections.<ChangeSetApproval> emptyList());
    psa.setValue(v);
    fs.normalize(approvalTypes.byId(psa.getCategoryId()), psa);
    if (v != psa.getValue()) {
      throw error(ao.name() + "=" + ao.value() + " not permitted");
    }
  }

  private void initOptionList() {
    optionList = new ArrayList<ApproveOption>();

    for (ApprovalType type : approvalTypes.getApprovalTypes()) {
      String usage = "";
      final ApprovalCategory category = type.getCategory();
      usage = "score for " + category.getName() + "\n";

      for (ApprovalCategoryValue v : type.getValues()) {
        usage += v.format() + "\n";
      }

      final String name =
          "--" + category.getName().toLowerCase().replace(' ', '-');
      optionList.add(new ApproveOption(name, usage, type));
    }
  }

  private void writeError(final String msg) {
    try {
      err.write(msg.getBytes(ENC));
    } catch (IOException e) {
    }
  }

  private void reviewPatchSets() throws Failure {

    boolean ok = true;

    for (final PatchSet.Id patchSetId : patchSetIds) {
      try {
        reviewPatchSet(patchSetId);
      } catch (UnloggedFailure e) {
        ok = false;
        writeError("error: " + e.getMessage() + "\n");
      } catch (Exception e) {
        ok = false;
        writeError("fatal: internal server error while approving "
            + patchSetId + "\n");
        log.error("internal error while approving " + patchSetId, e);
      }
    }

    if (!ok) {
      throw new UnloggedFailure(1, "one or more approvals failed;"
          + " review output above");
    }

    submitPatchSetUpdates();
  }

  private static UnloggedFailure error(final String msg) {
    return new UnloggedFailure(1, msg);
  }
}
