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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeListScreen;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.topics.TopicTable.ApprovalViewType;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.AccountTopicDashboardInfo;
import com.google.gerrit.reviewdb.Account;


public class AccountTopicDashboardScreen extends Screen implements ChangeListScreen {
  private final Account.Id ownerId;
  private TopicTable table;
  private TopicTable.Section byOwner;
  private TopicTable.Section forReview;
  private TopicTable.Section closed;

  public AccountTopicDashboardScreen(final Account.Id id) {
    ownerId = id;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    table = new TopicTable(true);
    table.addStyleName(Gerrit.RESOURCES.css().accountDashboard());
    byOwner = new TopicTable.Section("", ApprovalViewType.STRONGEST, null);
    forReview = new TopicTable.Section("", ApprovalViewType.USER, ownerId);
    closed = new TopicTable.Section("", ApprovalViewType.STRONGEST, null);

    table.addSection(byOwner);
    table.addSection(forReview);
    table.addSection(closed);
    add(table);
    table.setSavePointerId(PageLinks.toAccountTopicDashboard(ownerId));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.T_LIST_SVC.forAccount(ownerId,
        new ScreenLoadCallback<AccountTopicDashboardInfo>(this) {
          @Override
          protected void preDisplay(final AccountTopicDashboardInfo r) {
            display(r);
          }
        });
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  private void display(final AccountTopicDashboardInfo r) {
    table.setAccountInfoCache(r.getAccounts());

    final AccountInfo o = r.getAccounts().get(r.getOwner());
    final String name = FormatUtil.name(o);
    setWindowTitle(name);
    setPageTitle(Util.TM.accountTopicDashboardTitle(name));
    byOwner.setTitleText(com.google.gerrit.client.changes.Util.M.changesStartedBy(name));
    forReview.setTitleText(com.google.gerrit.client.changes.Util.M.changesReviewableBy(name));
    closed.setTitleText(com.google.gerrit.client.changes.Util.C.changesRecentlyClosed());

    byOwner.display(r.getByOwner());
    forReview.display(r.getForReview());
    closed.display(r.getClosed());
    table.finishDisplay();
  }
}
