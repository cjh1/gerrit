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

package com.google.gerrit.client.topics;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.AbstractEntity.Status;
import com.google.gerrit.reviewdb.Project;

public class ProjectTopicLink extends ProjectLink {

  public ProjectTopicLink(final Project.NameKey proj, Status stat) {
    this(proj.get(), proj, stat);
  }

  public ProjectTopicLink(final String text, final Project.NameKey proj,
      Status stat) {
    super(text, proj, stat, PageLinks.ENTITY_TOPIC + " " + PageLinks.projectQuery(proj, stat),
        PageLinks.toTopicQuery(PageLinks.projectQuery(proj, stat)));
  }

  @Override
  public void go() {
    Gerrit.display(getTargetHistoryToken(), createScreen());
  }

  private Screen createScreen() {
    return TopicQueryScreen.forQuery(getQuery());
  }
}
