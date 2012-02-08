package com.google.gerrit.server.mail;

import com.google.gerrit.reviewdb.AbstractMessage;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Account.Id;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroup.UUID;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public abstract class ReviewEmail extends OutgoingEmail {

  protected ProjectState projectState;
  protected Set<Account.Id> authors;
  protected boolean emailOnlyAuthors;
  protected AbstractMessage message;
  protected final Set<Account.Id> reviewers = new HashSet<Account.Id>();

  protected ReviewEmail(EmailArguments ea, String mc) {
    super(ea, mc);
    // TODO Auto-generated constructor stub
  }

  public void setFrom(final Account.Id id) {
    super.setFrom(id);

    /** Is the from user in an email squelching group? */
    final IdentifiedUser user =  args.identifiedUserFactory.create(id);
    final Set<AccountGroup.UUID> gids = user.getEffectiveGroups();
    for (final AccountGroup.UUID gid : gids) {
      if (args.groupCache.get(gid).isEmailOnlyAuthors()) {
        emailOnlyAuthors = true;
        break;
      }
    }
  }

  protected void setCommitIdHeader(RevId commit) {
    setHeader("X-Gerrit-Commit", commit.get());
  }

  protected void setSubjectHeader(String vmSubject) throws EmailException {
    setHeader("Subject", velocifyFile(vmSubject));
  }

  /** Get the text of the "cover letter", from {@link ChangeMessage}. */
  public String getCoverLetter() {
    if (message != null) {
      final String txt = message.getMessage();
      if (txt != null) {
        return txt.trim();
      }
    }
    return "";
  }

  /** Format the sender's "cover letter", {@link #getCoverLetter()}. */
  protected void formatCoverLetter() {
    final String cover = getCoverLetter();
    if (!"".equals(cover)) {
      appendText(cover);
      appendText("\n\n");
    }
  }

  /** Get the project entity the change is in; null if its been deleted. */
  protected ProjectState getProjectState() {
    return projectState;
  }

  protected Project getProject() {
    return projectState.getProject();
  }

  protected void add(final RecipientType rt, final Account.Id to) {
    if (! emailOnlyAuthors || authors.contains(to)) {
      super.add(rt, to);
    }
  }

  protected void formatReviewers(Set<Account.Id> reviewers) {
      TreeSet<String> names = new TreeSet<String>();
      for (Account.Id who : reviewers) {
        names.add(getNameEmailFor(who));
      }

      for (String name : names) {
        appendText("Gerrit-Reviewer: " + name + "\n");
      }
  }

  protected void setListIdHeader() throws EmailException {
    // Set a reasonable list id so that filters can be used to sort messages
    setVHeader("Mailing-List", "list $email.listId");
    setVHeader("List-Id", "<$email.listId.replace('@', '.')>");
    if (getSettingsUrl() != null) {
      setVHeader("List-Unsubscribe", "<$email.settingsUrl>");
    }
  }

  public String getListId() throws EmailException {
    return velocify("gerrit-$projectName.replace('/', '-')@$email.gerritHost");
  }

  protected abstract List<AccountProjectWatch> getWatches() throws OrmException;

  protected abstract Set<Account.Id> getReviewers() throws OrmException;

  protected abstract Set<AccountGroup.UUID> getProjectOwners();

  /** BCC any user who has set "notify all comments" on this project. */
  protected void bccWatchesNotifyAllComments() {
    try {
      // BCC anyone else who has interest in this project's changes
      //
      for (final AccountProjectWatch w : getWatches()) {
        if (w.isNotify(NotifyType.ALL_COMMENTS)) {
          add(RecipientType.BCC, w.getAccountId());
        }
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
    }
  }

  public void addReviewers(final Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  public List<String> getReviewerNames() {
    if (reviewers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<String>();
    for (Account.Id id : reviewers) {
      names.add(getNameFor(id));
    }
    return names;
  }

  protected void bccWatchers(final GroupCache groupCache, NotifyType notifyType) {
    try {
      // Try to mark interested owners with a TO and not a BCC line.
      //
      final Set<Account.Id> owners = new HashSet<Account.Id>();
      for (AccountGroup.UUID uuid : getProjectOwners()) {
        AccountGroup group = groupCache.get(uuid);
        if (group != null) {
          for (AccountGroupMember m : args.db.get().accountGroupMembers()
              .byGroup(group.getId())) {
            owners.add(m.getAccountId());
          }
        }
      }
  
      // BCC anyone who has interest in this project's reviews
      //
      for (final AccountProjectWatch w : getWatches()) {
        if (w.isNotify(notifyType)) {
          if (owners.contains(w.getAccountId())) {
            add(RecipientType.TO, w.getAccountId());
          } else {
            add(RecipientType.BCC, w.getAccountId());
          }
        }
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the review.
    }
  }

}
