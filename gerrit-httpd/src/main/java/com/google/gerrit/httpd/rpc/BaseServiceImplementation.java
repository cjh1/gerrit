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

package com.google.gerrit.httpd.rpc;

import com.google.gerrit.common.errors.CorruptEntityException;
import com.google.gerrit.common.errors.InvalidQueryException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

/** Support for services which require a {@link ReviewDb} instance. */
public class BaseServiceImplementation {
  private final Provider<ReviewDb> schema;
  private final Provider<? extends CurrentUser> currentUser;

  protected BaseServiceImplementation(final Provider<ReviewDb> schema,
      final Provider<? extends CurrentUser> currentUser) {
    this.schema = schema;
    this.currentUser = currentUser;
  }

  protected Account.Id getAccountId() {
    CurrentUser u = currentUser.get();
    if (u instanceof IdentifiedUser) {
      return ((IdentifiedUser) u).getAccountId();
    }
    return null;
  }

  /**
   * Executes <code>action.run</code> with an active ReviewDb connection.
   * <p>
   * A database handle is automatically opened and closed around the action's
   * {@link Action#run(ReviewDb)} method. OrmExceptions are caught and passed
   * into the onFailure method of the callback.
   *
   * @param <T> type of result the callback expects.
   * @param callback the callback that will receive the result.
   * @param action the action logic to perform.
   */
  protected <T> void run(final AsyncCallback<T> callback, final Action<T> action) {
    try {
      final T r = action.run(schema.get());
      if (r != null) {
        callback.onSuccess(r);
      }
    } catch (InvalidQueryException e) {
      callback.onFailure(e);
    } catch (NoSuchProjectException e) {
      callback.onFailure(new NoSuchEntityException());
    } catch (NoSuchGroupException e) {
      callback.onFailure(new NoSuchEntityException());

    } catch (OrmException e) {
      if (e.getCause() instanceof Failure) {
        callback.onFailure(e.getCause().getCause());

      } else if (e.getCause() instanceof CorruptEntityException) {
        callback.onFailure(e.getCause());

      } else if (e.getCause() instanceof NoSuchEntityException) {
        callback.onFailure(e.getCause());

      } else {
        callback.onFailure(e);
      }
    } catch (Failure e) {
      if (e.getCause() instanceof NoSuchProjectException
          || e.getCause() instanceof NoSuchChangeException) {
        callback.onFailure(new NoSuchEntityException());

      } else {
        callback.onFailure(e.getCause());
      }
    }
  }

  /** Exception whose cause is passed into onFailure. */
  public static class Failure extends Exception {
    private static final long serialVersionUID = 1L;

    public Failure(final Throwable why) {
      super(why);
    }
  }

  /** Arbitrary action to run with a database connection. */
  public static interface Action<T> {
    /**
     * Perform this action, returning the onSuccess value.
     *
     * @param db an open database handle to be used by this connection.
     * @return he value to pass to {@link AsyncCallback#onSuccess(Object)}.
     * @throws OrmException any schema based action failed.
     * @throws Failure cause is given to
     *         {@link AsyncCallback#onFailure(Throwable)}.
     * @throws NoSuchProjectException
     * @throws NoSuchGroupException
     * @throws InvalidQueryException
     */
    T run(ReviewDb db) throws OrmException, Failure, NoSuchProjectException,
        NoSuchGroupException, InvalidQueryException;
  }
}
