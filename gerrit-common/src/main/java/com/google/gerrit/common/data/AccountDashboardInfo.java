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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Account;

import java.util.List;

/** Summary information needed to display an account dashboard.
 */
public abstract class AccountDashboardInfo<T> {
  protected AccountInfoCache accounts;
  protected Account.Id owner;
  protected List<T> byOwner;
  protected List<T> forReview;
  protected List<T> closed;

  protected AccountDashboardInfo() {
  }

  public AccountDashboardInfo(final Account.Id forUser) {
    owner = forUser;
  }

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public void setAccounts(final AccountInfoCache ac) {
    accounts = ac;
  }

  public Account.Id getOwner() {
    return owner;
  }

  public List<T> getByOwner() {
    return byOwner;
  }

  public void setByOwner(List<T> c) {
    byOwner = c;
  }

  public List<T> getForReview() {
    return forReview;
  }

  public void setForReview(List<T> c) {
    forReview = c;
  }

  public List<T> getClosed() {
    return closed;
  }

  public void setClosed(List<T> c) {
    closed = c;
  }
}
