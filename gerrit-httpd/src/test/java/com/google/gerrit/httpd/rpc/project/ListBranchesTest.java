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

package com.google.gerrit.httpd.rpc.project;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.classextension.EasyMock.createStrictMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.easymock.IExpectationSetters;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ListBranchesTest extends LocalDiskRepositoryTestCase {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private ObjectId idA;
  private Project.NameKey name;
  private Repository realDb;
  private Repository mockDb;
  private ProjectControl.Factory pcf;
  private ProjectControl pc;
  private GitRepositoryManager grm;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    idA = ObjectId.fromString("df84c2f4f7ce7e0b25cdeac84b8870bcff319885");
    name = new Project.NameKey("test");
    realDb = createBareRepository();

    mockDb = createStrictMock(Repository.class);
    pc = createStrictMock(ProjectControl.class);
    pcf = createStrictMock(ProjectControl.Factory.class);
    grm = createStrictMock(GitRepositoryManager.class);
  }

  private IExpectationSetters<ProjectControl> validate()
      throws NoSuchProjectException {
    return expect(pcf.validateFor(eq(name), //
        eq(ProjectControl.OWNER | ProjectControl.VISIBLE)));
  }

  private void doReplay() {
    replay(mockDb, pc, pcf, grm);
  }

  private void doVerify() {
    verify(mockDb, pc, pcf, grm);
  }

  private void set(String branch, ObjectId id) throws IOException {
    final RefUpdate u = realDb.updateRef(R_HEADS + branch);
    u.setForceUpdate(true);
    u.setNewObjectId(id);
    switch (u.update()) {
      case NEW:
      case FAST_FORWARD:
      case FORCED:
        break;
      default:
        fail("unexpected update failure " + branch + " " + u.getResult());
    }
  }

  public void testProjectNotVisible() throws Exception {
    final NoSuchProjectException err = new NoSuchProjectException(name);
    validate().andThrow(err);
    doReplay();
    try {
      new ListBranches(pcf, grm, name).call();
      fail("did not throw when expected not authorized");
    } catch (NoSuchProjectException e2) {
      assertSame(err, e2);
    }
    doVerify();
  }


  private List<Branch> permitted(boolean getFullBranch)
      throws NoSuchProjectException, IOException {
    validate().andReturn(pc);
    expect(grm.openRepository(eq(name.get()))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andDelegateTo(realDb);
    if (getFullBranch) {
      expect(mockDb.getFullBranch()).andDelegateTo(realDb);
    }
    mockDb.close();
    expectLastCall();

    doReplay();
    final List<Branch> r = new ListBranches(pcf, grm, name).call();
    doVerify();
    assertNotNull(r);
    return r;
  }

  public void testEmptyProject() throws Exception {
    List<Branch> r = permitted(true);
    assertEquals(1, r.size());

    Branch b = r.get(0);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(HEAD, b.getNameKey().get());

    assertEquals(HEAD, b.getName());
    assertEquals(HEAD, b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals("master", b.getRevision().get());
  }

  public void testMasterBranch() throws Exception {
    set("master", idA);

    List<Branch> r = permitted(false);
    assertEquals(2, r.size());

    Branch b = r.get(0);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(HEAD, b.getNameKey().get());

    assertEquals(HEAD, b.getName());
    assertEquals(HEAD, b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals("master", b.getRevision().get());

    b = r.get(1);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(R_HEADS + "master", b.getNameKey().get());

    assertEquals(R_HEADS + "master", b.getName());
    assertEquals("master", b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals(idA.name(), b.getRevision().get());
  }

  public void testBranchNotHead() throws Exception {
    set("foo", idA);

    List<Branch> r = permitted(true);
    assertEquals(2, r.size());

    Branch b = r.get(0);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(HEAD, b.getNameKey().get());

    assertEquals(HEAD, b.getName());
    assertEquals(HEAD, b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals("master", b.getRevision().get());

    b = r.get(1);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(R_HEADS + "foo", b.getNameKey().get());

    assertEquals(R_HEADS + "foo", b.getName());
    assertEquals("foo", b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals(idA.name(), b.getRevision().get());
  }

  public void testSortByName() throws Exception {
    Map<String, Ref> u = new LinkedHashMap<String, Ref>();
    u.put("foo", new Ref(Ref.Storage.LOOSE, R_HEADS + "foo", idA));
    u.put("bar", new Ref(Ref.Storage.LOOSE, R_HEADS + "bar", idA));
    u.put(HEAD, new Ref(Ref.Storage.LOOSE, HEAD, R_HEADS + "master", null));

    validate().andReturn(pc);
    expect(grm.openRepository(eq(name.get()))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andReturn(u);
    mockDb.close();
    expectLastCall();

    doReplay();
    final List<Branch> r = new ListBranches(pcf, grm, name).call();
    doVerify();
    assertNotNull(r);

    assertEquals(3, r.size());
    assertEquals(HEAD, r.get(0).getShortName());
    assertEquals("bar", r.get(1).getShortName());
    assertEquals("foo", r.get(2).getShortName());
  }
}