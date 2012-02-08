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

package com.google.gerrit.server.mail;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.patch.PatchFile;
import com.google.gerrit.server.patch.PatchList;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TopicCommentSender extends ReplyToTopicSender {
  public static interface Factory {
    public TopicCommentSender create(Topic t);
  }

  private List<PatchLineComment> inlineComments = Collections.emptyList();

  @Inject
  public TopicCommentSender(EmailArguments ea, @Assisted Topic t) {
    super(ea, t, "comment");
  }

  public void setPatchLineComments(final List<PatchLineComment> plc) {


  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    bccWatchesNotifyAllComments();
  }

  @Override
  public void formatTopic() throws EmailException {
    appendText(velocifyFile("TopicComment.vm"));
  }
}
