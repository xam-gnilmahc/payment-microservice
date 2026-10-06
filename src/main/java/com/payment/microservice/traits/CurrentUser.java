package com.payment.microservice.traits;

import com.payment.microservice.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * The signed-in user, in one place.
 *
 * <p>Every controller needs the same three things: who is calling, what is their id, and are they
 * an admin. Spring Security already put the user row in the session, so instead of repeating the
 * same lookup in every controller, ask here:
 *
 * <pre>
 *   Long customerId = CurrentUser.id();        // whose payment this is
 *   boolean admin = CurrentUser.isAdmin();     // may I see everybody's data?
 *   User me = CurrentUser.require();           // the whole row, 401 if nobody is signed in
 * </pre>
 *
 * <p>All of it is read from the session, so there is no database query and the admin flag comes
 * from the is_superadmin column that was read when the account signed in.
 */
public final class CurrentUser {

  private CurrentUser() {}

  /** The signed-in user, or null when nobody is signed in. */
  public static User get() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
      return null;
    }
    return user;
  }

  /** The signed-in user, or 401 when nobody is signed in. Use this in endpoints that need a login. */
  public static User require() {
    User user = get();
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in");
    }
    return user;
  }

  /** The signed-in user's id, or 401. This is the customerId for payments, logs and addresses. */
  public static Long id() {
    return require().getId();
  }

  /** The signed-in user's email, or 401. */
  public static String email() {
    return require().getEmail();
  }

  /** True when the account's is_superadmin column is set. The column is a string, so "1" and "true" both count. */
  public static boolean isAdmin() {
    return isAdmin(get());
  }

  /** Same check for a user you already have in hand, e.g. straight after registering somebody. */
  public static boolean isAdmin(User user) {
    if (user == null) return false;
    String flag = user.getIsSuperAdmin();
    return flag != null && ("1".equals(flag) || "true".equalsIgnoreCase(flag));
  }
}
