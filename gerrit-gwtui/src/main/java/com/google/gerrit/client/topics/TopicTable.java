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

import static com.google.gerrit.client.FormatUtil.shortFormat;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.BranchTopicLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.client.ui.TopicLink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ApprovalSummary;
import com.google.gerrit.common.data.ApprovalSummarySet;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ChangeSetApprovalSummary;
import com.google.gerrit.common.data.ChangeSetApprovalSummarySet;
import com.google.gerrit.common.data.TopicInfo;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.Topic;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TopicTable extends NavigationTable<TopicInfo> {
  private static final int C_ID = 1;
  private static final int C_SUBJECT = 2;
  private static final int C_OWNER = 3;
  private static final int C_PROJECT = 4;
  private static final int C_BRANCH = 5;
  private static final int C_LAST_UPDATE = 6;
  private static final int BASE_COLUMNS = 7;

  private final List<Section> sections;
  private AccountInfoCache accountCache = AccountInfoCache.empty();
  private final List<ApprovalType> approvalTypes;
  private final int columns;

  public TopicTable() {
    this(false);
  }

  public TopicTable(boolean showApprovals) {
    approvalTypes = Gerrit.getConfig().getApprovalTypes().getApprovalTypes();
    if (showApprovals) {
      columns = BASE_COLUMNS + approvalTypes.size();
    } else {
      columns = BASE_COLUMNS;
    }

    keysNavigation.add(new PrevKeyCommand(0, 'k', com.google.gerrit.client.changes.Util.C.changeTablePrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', com.google.gerrit.client.changes.Util.C.changeTableNext()));
    keysNavigation.add(new OpenKeyCommand(0, 'o', com.google.gerrit.client.changes.Util.C.changeTableOpen()));
    keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, com.google.gerrit.client.changes.Util.C.changeTableOpen()));

    sections = new ArrayList<Section>();
    table.setText(0, C_ID, com.google.gerrit.client.changes.Util.C.changeTableColumnID());
    table.setText(0, C_SUBJECT, com.google.gerrit.client.changes.Util.C.changeTableColumnSubject());
    table.setText(0, C_OWNER, com.google.gerrit.client.changes.Util.C.changeTableColumnOwner());
    table.setText(0, C_PROJECT, com.google.gerrit.client.changes.Util.C.changeTableColumnProject());
    table.setText(0, C_BRANCH, com.google.gerrit.client.changes.Util.C.changeTableColumnBranch());
    table.setText(0, C_LAST_UPDATE, com.google.gerrit.client.changes.Util.C.changeTableColumnLastUpdate());
    for (int i = BASE_COLUMNS; i < columns; i++) {
      final ApprovalType type = approvalTypes.get(i - BASE_COLUMNS);
      final ApprovalCategory cat = type.getCategory();
      String text = cat.getAbbreviatedName();
      if (text == null) {
        text = cat.getName();
      }
      table.setText(0, i, text);
      if (text != null) {
        table.getCellFormatter().getElement(0, i).setTitle(cat.getName());
      }
    }

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, C_ID, Gerrit.RESOURCES.css().cID());
    for (int i = C_ID; i < columns; i++) {
      fmt.addStyleName(0, i, Gerrit.RESOURCES.css().dataHeader());
    }

    table.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final Cell cell = table.getCellForEvent(event);
        if (cell == null) {
          return;
        }
        if (cell.getCellIndex() == C_OWNER) {
          // Don't do anything.
        } else if (getRowItem(cell.getRowIndex()) != null) {
          movePointerTo(cell.getRowIndex());
        }
      }
    });
  }

  @Override
  protected Object getRowItemKey(final TopicInfo item) {
    return item.getId();
  }

  @Override
  protected void onOpenRow(final int row) {
    final TopicInfo t = getRowItem(row);
    Gerrit.display(PageLinks.toTopic(t.getId()), new TopicScreen(t));
  }

  private void insertNoneRow(final int row) {
    insertRow(row);
    table.setText(row, 0, com.google.gerrit.client.changes.Util.C.changeTableNone());
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(row, 0, columns);
    fmt.setStyleName(row, 0, Gerrit.RESOURCES.css().emptySection());
  }

  private void insertChangeRow(final int row) {
    insertRow(row);
    applyDataRowStyle(row);
  }

  @Override
  protected void applyDataRowStyle(final int row) {
    super.applyDataRowStyle(row);
    final CellFormatter fmt = table.getCellFormatter();
    for (int i = C_ID; i < columns; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().dataCell());
    }
    fmt.addStyleName(row, C_ID, Gerrit.RESOURCES.css().cID());
    fmt.addStyleName(row, C_SUBJECT, Gerrit.RESOURCES.css().cSUBJECT());
    fmt.addStyleName(row, C_PROJECT, Gerrit.RESOURCES.css().cPROJECT());
    fmt.addStyleName(row, C_BRANCH, Gerrit.RESOURCES.css().cPROJECT());
    fmt.addStyleName(row, C_LAST_UPDATE, Gerrit.RESOURCES.css().cLastUpdate());
    for (int i = BASE_COLUMNS; i < columns; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().cAPPROVAL());
    }
  }

  private void populateTopicRow(final int row, final TopicInfo t) {
    final String idstr = t.getKey().abbreviate();
    table.setWidget(row, C_ARROW, null);
    table.setWidget(row, C_ID, new TableChangeLink(idstr, t));

    String s = t.getSubject();
    if (s.length() > 80) {
      s = s.substring(0, 80);
    }
    if (t.getStatus() != null && t.getStatus() != Change.Status.NEW) {
      s += " (" + t.getStatus().name() + ")";
    }

    table.setWidget(row, C_SUBJECT, new TableChangeLink(s, t));
    table.setWidget(row, C_OWNER, link(t.getOwner()));
    table.setWidget(row, C_PROJECT, new ProjectLink(t.getProject().getKey(), t
        .getStatus()));
    table.setWidget(row, C_BRANCH, new BranchTopicLink(t.getProject().getKey(), t
        .getStatus(), t.getBranch(), t.getTopic(), t.getId()));
    table.setText(row, C_LAST_UPDATE, shortFormat(t.getLastUpdatedOn()));
    setRowItem(row, t);
    table.getRowFormatter().addStyleName(row, Gerrit.RESOURCES.css().topicTable());
  }

  private AccountDashboardLink link(final Account.Id id) {
    return AccountDashboardLink.link(accountCache, id);
  }

  public void addSection(final Section s) {
    assert s.parent == null;

    if (s.titleText != null) {
      s.titleRow = table.getRowCount();
      table.setText(s.titleRow, 0, s.titleText);
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setColSpan(s.titleRow, 0, columns);
      fmt.addStyleName(s.titleRow, 0, Gerrit.RESOURCES.css().sectionHeader());
    } else {
      s.titleRow = -1;
    }

    s.parent = this;
    s.dataBegin = table.getRowCount();
    insertNoneRow(s.dataBegin);
    sections.add(s);
  }

  public void setAccountInfoCache(final AccountInfoCache aic) {
    assert aic != null;
    accountCache = aic;
  }

  private int insertRow(final int beforeRow) {
    for (final Section s : sections) {
      if (beforeRow <= s.titleRow) {
        s.titleRow++;
      }
      if (beforeRow < s.dataBegin) {
        s.dataBegin++;
      }
    }
    return table.insertRow(beforeRow);
  }

  private void removeRow(final int row) {
    for (final Section s : sections) {
      if (row < s.titleRow) {
        s.titleRow--;
      }
      if (row < s.dataBegin) {
        s.dataBegin--;
      }
    }
    table.removeRow(row);
  }

  private void displayApprovals(final int row, final ChangeSetApprovalSummary summary,
      final AccountInfoCache aic, final boolean highlightUnreviewed) {
    final CellFormatter fmt = table.getCellFormatter();
    final Map<ApprovalCategory.Id, ChangeSetApproval> approvals =
        summary.getApprovalMap();
    int col = BASE_COLUMNS;
    boolean haveReview = false;

    boolean displayPersonNameInReviewCategory = false;

    if (Gerrit.isSignedIn()) {
      AccountGeneralPreferences prefs = Gerrit.getUserAccount().getGeneralPreferences();

      if (prefs.isDisplayPersonNameInReviewCategory()) {
        displayPersonNameInReviewCategory = true;
      }
    }

    for (final ApprovalType type : approvalTypes) {
      final ChangeSetApproval ca = approvals.get(type.getCategory().getId());

      fmt.removeStyleName(row, col, Gerrit.RESOURCES.css().negscore());
      fmt.removeStyleName(row, col, Gerrit.RESOURCES.css().posscore());
      fmt.addStyleName(row, col, Gerrit.RESOURCES.css().singleLine());

      if (ca == null || ca.getValue() == 0) {
        table.clearCell(row, col);

      } else {
        haveReview = true;

        final ApprovalCategoryValue acv = type.getValue(ca);
        final AccountInfo ai = aic.get(ca.getAccountId());

        if (type.isMaxNegative(ca)) {

          if (displayPersonNameInReviewCategory) {
            FlowPanel fp = new FlowPanel();
            fp.add(new Image(Gerrit.RESOURCES.redNot()));
            fp.add(new InlineLabel(FormatUtil.name(ai)));
            table.setWidget(row, col, fp);
          } else {
            table.setWidget(row, col, new Image(Gerrit.RESOURCES.redNot()));
          }

        } else if (type.isMaxPositive(ca)) {

          if (displayPersonNameInReviewCategory) {
            FlowPanel fp = new FlowPanel();
            fp.add(new Image(Gerrit.RESOURCES.greenCheck()));
            fp.add(new InlineLabel(FormatUtil.name(ai)));
            table.setWidget(row, col, fp);
          } else {
            table.setWidget(row, col, new Image(Gerrit.RESOURCES.greenCheck()));
          }

        } else {
          String vstr = String.valueOf(ca.getValue());

          if (displayPersonNameInReviewCategory) {
            vstr = vstr + " " + FormatUtil.name(ai);
          }

          if (ca.getValue() > 0) {
            vstr = "+" + vstr;
            fmt.addStyleName(row, col, Gerrit.RESOURCES.css().posscore());
          } else {
            fmt.addStyleName(row, col, Gerrit.RESOURCES.css().negscore());
          }
          table.setText(row, col, vstr);
        }

        // Some web browsers ignore the embedded newline; some like it;
        // so we include a space before the newline to accommodate both.
        //
        fmt.getElement(row, col).setTitle(
            acv.getName() + " \nby " + FormatUtil.nameEmail(ai));
      }

      col++;
    }

    final Element tr = DOM.getParent(fmt.getElement(row, 0));
    UIObject.setStyleName(tr, Gerrit.RESOURCES.css().needsReview(), !haveReview
        && highlightUnreviewed);
  }

  GerritCallback<ChangeSetApprovalSummarySet> approvalFormatter(final int dataBegin,
      final int rows, final boolean highlightUnreviewed) {
    return new GerritCallback<ChangeSetApprovalSummarySet>() {
      @Override
      public void onSuccess(final ChangeSetApprovalSummarySet as) {
        Map<Topic.Id,ChangeSetApprovalSummary> ids = as.getSummaryMap();
        AccountInfoCache aic = as.getAccountInfoCache();
        for (int row = dataBegin; row < dataBegin + rows; row++) {
          final TopicInfo c = getRowItem(row);
          if (ids.containsKey(c.getId())) {
            displayApprovals(row, ids.get(c.getId()), aic, highlightUnreviewed);
          }
        }
      }
    };
  }

  private final class TableChangeLink extends TopicLink {
    private TableChangeLink(final String text, final TopicInfo t) {
      super(text, t.getTopicId());
    }

    @Override
    public void go() {
      movePointerTo(getTopicId());
      super.go();
    }
  }

  public enum ApprovalViewType {
    NONE, USER, STRONGEST
  }

  public static class Section {
    String titleText;

    TopicTable parent;
    final ApprovalViewType viewType;
    final Account.Id ownerId;
    int titleRow = -1;
    int dataBegin;
    int rows;

    public Section() {
      this(null, ApprovalViewType.NONE, null);
    }

    public Section(final String titleText) {
      this(titleText, ApprovalViewType.NONE, null);
    }

    public Section(final String titleText, final ApprovalViewType view,
        final Account.Id owner) {
      setTitleText(titleText);
      viewType = view;
      ownerId = owner;
    }

    public void setTitleText(final String text) {
      titleText = text;
      if (titleRow >= 0) {
        parent.table.setText(titleRow, 0, titleText);
      }
    }

    public void display(final List<TopicInfo> topicList) {
      final int sz = topicList != null ? topicList.size() : 0;
      final boolean hadData = rows > 0;

      if (hadData) {
        while (sz < rows) {
          parent.removeRow(dataBegin);
          rows--;
        }
      }

      if (sz == 0) {
        if (hadData) {
          parent.insertNoneRow(dataBegin);
        }
      } else {
        Set<Topic.Id> tids = new HashSet<Topic.Id>();

        if (!hadData) {
          parent.removeRow(dataBegin);
        }

        while (rows < sz) {
          parent.insertChangeRow(dataBegin + rows);
          rows++;
        }

        for (int i = 0; i < sz; i++) {
          TopicInfo t = topicList.get(i);
          parent.populateTopicRow(dataBegin + i, t);
          tids.add(t.getId());
        }

        switch (viewType) {
          case NONE:
            break;
          case USER:
            Util.T_DETAIL_SVC.userApprovals(tids, ownerId, parent
                .approvalFormatter(dataBegin, rows, true));
            break;
          case STRONGEST:
            Util.T_DETAIL_SVC.strongestApprovals(tids, parent
                .approvalFormatter(dataBegin, rows, false));
            break;
        }
      }
    }
  }
}
