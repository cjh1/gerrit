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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.SetApproval;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Summarizes the approvals (or negative approvals) for a set.
 * This will typically contain zero or one approvals for each
 * category, with all of the approvals coming from a single set.
 *
 * @TODO Template a generic class and share between changesets and patchsets.
 * I got GWT errors when I tried todo this.
 */
public class ChangeSetApprovalSummary {
  protected Map<ApprovalCategory.Id, ChangeSetApproval> approvals;

  protected ChangeSetApprovalSummary() {
  }

  public ChangeSetApprovalSummary(final Iterable<ChangeSetApproval> list) {
    approvals = new HashMap<ApprovalCategory.Id, ChangeSetApproval>();
    for (final ChangeSetApproval a : list) {
      approvals.put(a.getCategoryId(), a);
    }
  }

  public Map<ApprovalCategory.Id, ChangeSetApproval> getApprovalMap() {
    return Collections.unmodifiableMap(approvals);
  }
}
