// Copyright (C) 2008 The Android Open Source Project
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

import static com.google.gerrit.common.PageLinks.ENTITY_TOPIC;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.SingleListTopicInfo;
import com.google.gerrit.common.data.TopicInfo;
import com.google.gerrit.reviewdb.RevId;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;


public class TopicQueryScreen extends PagedSingleListScreen {
  public static TopicQueryScreen forQuery(String query) {
    return forQuery(query, PageLinks.TOP);
  }

  public static TopicQueryScreen forQuery(String query, String position) {
    return new TopicQueryScreen(KeyUtil.encode(query), position);
  }

  private final String query;
  private final String originalQuery;

  public TopicQueryScreen(final String encQuery, final String positionToken) {
    super("/tq/" + encQuery, positionToken);
    originalQuery = KeyUtil.decode(encQuery);
    query = originalQuery.replace(ENTITY_TOPIC, "");
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setWindowTitle(com.google.gerrit.client.changes.Util.M.changeQueryWindowTitle(query));
    setPageTitle(com.google.gerrit.client.changes.Util.M.changeQueryPageTitle(query));
  }

  @Override
  protected AsyncCallback<SingleListTopicInfo> loadCallback() {
    return new GerritCallback<SingleListTopicInfo>() {
      public final void onSuccess(final SingleListTopicInfo result) {
        if (isAttached()) {
          if (result.getTopics().size() == 1 && isSingleQuery(query)) {
            final TopicInfo t = result.getTopics().get(0);
            Gerrit.display(PageLinks.toTopic(t.getId()), new TopicScreen(t));
          } else {
            Gerrit.setQueryString(originalQuery);
            display(result);
            TopicQueryScreen.this.display();
          }
        }
      }
    };
  }

  @Override
  protected void loadPrev() {
    Util.T_LIST_SVC.allQueryPrev(query, pos, pageSize, loadCallback());
  }

  @Override
  protected void loadNext() {
    Util.T_LIST_SVC.allQueryNext(query, pos, pageSize, loadCallback());
  }

  private static boolean isSingleQuery(String query) {
    if (query.matches("^[tT][\\w]{4,}$")) {
      // Topic-Id
      //
      return true;
    }

    return false;
  }
}
