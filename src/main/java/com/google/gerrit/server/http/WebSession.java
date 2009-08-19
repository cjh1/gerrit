package com.google.gerrit.server.http;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.http.WebSessionManager.Key;
import com.google.gerrit.server.http.WebSessionManager.Val;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.servlet.RequestScoped;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestScoped
public final class WebSession {
  private static final String ACCOUNT_COOKIE = "GerritAccount";

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final String cacheName = WebSessionManager.CACHE_NAME;
        final TypeLiteral<Cache<Key, Val>> type =
            new TypeLiteral<Cache<Key, Val>>() {};
        disk(type, cacheName).timeToIdle(12, HOURS).timeToLive(0, SECONDS);
        bind(WebSessionManager.class);
        bind(WebSession.class).in(RequestScoped.class);
      }
    };
  }

  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final WebSessionManager manager;
  private final AnonymousUser anonymous;
  private final IdentifiedUser.RequestFactory identified;
  private Cookie outCookie;

  private Key key;
  private Val val;

  @Inject
  WebSession(final HttpServletRequest request,
      final HttpServletResponse response, final WebSessionManager manager,
      final AnonymousUser anonymous,
      final IdentifiedUser.RequestFactory identified) {
    this.request = request;
    this.response = response;
    this.manager = manager;
    this.anonymous = anonymous;
    this.identified = identified;

    final String cookie = readCookie();
    if (cookie != null) {
      key = new Key(cookie);
      val = manager.get(key);
    } else {
      key = null;
      val = null;
    }

    if (isSignedIn() && val.refreshCookieAt <= System.currentTimeMillis()) {
      // Cookie is more than half old. Send it again to the client with a
      // fresh expiration date.
      //
      manager.updateRefreshCookieAt(val);
      saveCookie(key.token, val.getCookieAge());
    }
  }

  private String readCookie() {
    final Cookie[] all = request.getCookies();
    if (all != null) {
      for (final Cookie c : all) {
        if (ACCOUNT_COOKIE.equals(c.getName())) {
          final String v = c.getValue();
          return v != null && !"".equals(v) ? v : null;
        }
      }
    }
    return null;
  }

  public boolean isSignedIn() {
    return val != null;
  }

  String getToken() {
    return isSignedIn() ? key.token : null;
  }

  boolean isTokenValid(final String keyIn) {
    return isSignedIn() && key.token.equals(keyIn);
  }

  CurrentUser getCurrentUser() {
    return isSignedIn() ? identified.create(val.accountId) : anonymous;
  }

  public void login(final Account.Id id, final boolean rememberMe) {
    logout();

    key = manager.createKey(id);
    val = manager.createVal(key, id);

    final int age;
    if (rememberMe) {
      manager.updateRefreshCookieAt(val);
      age = val.getCookieAge();
    } else {
      val.refreshCookieAt = Long.MAX_VALUE;
      age = -1 /* don't store on client disk */;
    }
    saveCookie(key.token, age);
  }

  public void logout() {
    if (val != null) {
      manager.destroy(key);
      key = null;
      val = null;
      saveCookie("", 0 /* erase at client */);
    }
  }

  private void saveCookie(final String val, final int age) {
    if (outCookie == null) {
      String path = request.getContextPath();
      if (path.equals("")) {
        path = "/";
      }
      outCookie = new Cookie(ACCOUNT_COOKIE, val);
      outCookie.setPath(path);
      outCookie.setMaxAge(age);
      response.addCookie(outCookie);
    } else {
      outCookie.setMaxAge(age);
      outCookie.setValue(val);
    }
  }
}