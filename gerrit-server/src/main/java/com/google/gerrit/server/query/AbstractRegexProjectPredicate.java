// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query;

import com.google.gerrit.reviewdb.Branch.NameKey;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.topic.TopicData;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

public abstract class  AbstractRegexProjectPredicate<T> extends OperatorPredicate<T> {
  protected final Provider<ReviewDb> dbProvider;
  protected final RunAutomaton pattern;

  public AbstractRegexProjectPredicate(Provider<ReviewDb> dbProvider, String re) {
    super(ChangeQueryBuilder.FIELD_PROJECT, re);

    if (re.startsWith("^")) {
      re = re.substring(1);
    }

    if (re.endsWith("$") && !re.endsWith("\\$")) {
      re = re.substring(0, re.length() - 1);
    }

    this.dbProvider = dbProvider;
    this.pattern = new RunAutomaton(new RegExp(re).toAutomaton());
  }

  @Override
  public int getCost() {
    return 1;
  }

  protected boolean match(final NameKey p) throws OrmException {
    return pattern.run(p.get());
  }
}
