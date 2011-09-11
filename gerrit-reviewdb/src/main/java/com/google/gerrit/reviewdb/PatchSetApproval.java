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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;

/** An approval (or negative approval) on a patch set. */
public final class PatchSetApproval extends SetApproval<PatchSet.Id> {
  public static class Key extends CompoundKey<PatchSet.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column(id = 2)
    protected Account.Id accountId;

    @Column(id = 3)
    protected ApprovalCategory.Id categoryId;

    protected Key() {
      patchSetId = new PatchSet.Id();
      accountId = new Account.Id();
      categoryId = new ApprovalCategory.Id();
    }

    public Key(final PatchSet.Id ps, final Account.Id a,
        final ApprovalCategory.Id c) {
      this.patchSetId = ps;
      this.accountId = a;
      this.categoryId = c;
    }

    @Override
    public PatchSet.Id getParentKey() {
      return patchSetId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {accountId, categoryId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  /** <i>Cached copy of Change.open.</i> */
  @Column(id = 4)
  protected boolean changeOpen;

  /** <i>Cached copy of Change.sortKey</i>; only if {@link #changeOpen} = false */
  @Column(id = 5, length = 16, notNull = false)
  protected String changeSortKey;

  protected PatchSetApproval() {
  }

  public PatchSetApproval(final PatchSetApproval.Key k, final short v) {
    key = k;
    changeOpen = true;
    setValue(v);
    setGranted();
  }

  public PatchSetApproval(final PatchSet.Id psId, final PatchSetApproval src) {
    key =
        new PatchSetApproval.Key(psId, src.getAccountId(), src.getCategoryId());
    changeOpen = true;
    value = src.getValue();
    granted = src.granted;
  }

  public PatchSetApproval.Key getKey() {
    return key;
  }

  public PatchSet.Id getPatchSetId() {
    return key.patchSetId;
  }

  public Account.Id getAccountId() {
    return key.accountId;
  }

  @Override
  public ApprovalCategory.Id getCategoryId() {
    return key.categoryId;
  }

  public void cache(final Change c) {
    changeOpen = c.open;
    changeSortKey = c.sortKey;
  }

  @Override
  public PatchSet.Id getSetId() {
    return getPatchSetId();
  }
}