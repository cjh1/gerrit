// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.query.topic;

import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.query.AbstractResultSet;
import com.google.gwtorm.client.ResultSet;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

abstract class TopicDataResultSet<T> extends AbstractResultSet<TopicData> {

  static ResultSet<TopicData> topic(final ResultSet<Topic> rs) {
    return new TopicDataResultSet<Topic>(rs, true) {
      @Override
      TopicData convert(Topic t) {
        return new TopicData(t);
      }
    };
  }

  private final ResultSet<T> source;
  private final boolean unique;

  TopicDataResultSet(ResultSet<T> source, boolean unique) {
    this.source = source;
    this.unique = unique;
  }

  @Override
  public Iterator<TopicData> iterator() {
    if (unique) {
      return new Iterator<TopicData>() {
        private final Iterator<T> itr = source.iterator();

        @Override
        public boolean hasNext() {
          return itr.hasNext();
        }

        @Override
        public TopicData next() {
          return convert(itr.next());
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };

    } else {
      return new Iterator<TopicData>() {
        private final Iterator<T> itr = source.iterator();
        private final HashSet<Topic.Id> seen = new HashSet<Topic.Id>();
        private TopicData next;

        @Override
        public boolean hasNext() {
          if (next != null) {
            return true;
          }
          while (itr.hasNext()) {
            TopicData d = convert(itr.next());
            if (seen.add(d.getId())) {
              next = d;
              return true;
            }
          }
          return false;
        }

        @Override
        public TopicData next() {
          if (hasNext()) {
            TopicData r = next;
            next = null;
            return r;
          }
          throw new NoSuchElementException();
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }
  }

  @Override
  public void close() {
    source.close();
  }

  abstract TopicData convert(T t);
}
