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

package com.google.gerrit.server.rpc;

import com.google.gerrit.client.changes.ChangeDetailService;
import com.google.gerrit.client.changes.PatchSetPublishDetail;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.BaseServiceImplementation;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

class ChangeDetailServiceImpl extends BaseServiceImplementation implements
    ChangeDetailService {
  private final ChangeDetailFactory.Factory changeDetail;
  private final PatchSetDetailFactory.Factory patchSetDetail;
  private final PatchSetPublishDetailFactory.Factory patchSetPublishDetail;

  @Inject
  ChangeDetailServiceImpl(final SchemaFactory<ReviewDb> sf,
      final ChangeDetailFactory.Factory changeDetail,
      final PatchSetDetailFactory.Factory patchSetDetail,
      final PatchSetPublishDetailFactory.Factory patchSetPublishDetail) {
    super(sf);
    this.changeDetail = changeDetail;
    this.patchSetDetail = patchSetDetail;
    this.patchSetPublishDetail = patchSetPublishDetail;
  }

  public void changeDetail(final Change.Id id,
      final AsyncCallback<ChangeDetail> callback) {
    run(callback, changeDetail.create(id));
  }

  public void patchSetDetail(final PatchSet.Id id,
      final AsyncCallback<PatchSetDetail> callback) {
    run(callback, patchSetDetail.create(id));
  }

  public void patchSetPublishDetail(final PatchSet.Id id,
      final AsyncCallback<PatchSetPublishDetail> callback) {
    run(callback, patchSetPublishDetail.create(id));
  }
}
