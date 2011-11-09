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

import com.google.gerrit.common.data.TopicDetailService;
import com.google.gerrit.common.data.TopicManageService;
import com.google.gwt.core.client.GWT;
import com.google.gwtjsonrpc.client.JsonUtil;

public class Util {

  public static final TopicMessages TM = GWT.create(TopicMessages.class);
  public static final TopicConstants TC = GWT.create(TopicConstants.class);
  public static final TopicDetailService T_DETAIL_SVC;
  public static final TopicManageService T_MANAGE_SVC;

  static
  {
    T_DETAIL_SVC = GWT.create(TopicDetailService.class);
    JsonUtil.bind(com.google.gerrit.client.topics.Util.T_DETAIL_SVC, "rpc/TopicDetailService");

    T_MANAGE_SVC = GWT.create(TopicManageService.class);
    JsonUtil.bind(com.google.gerrit.client.topics.Util.T_MANAGE_SVC, "rpc/TopicManageService");
  }
}
