/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Alex Rojkov
 */

package com.caucho.boot;

import com.caucho.server.admin.ManagerClient;
import com.caucho.util.L10N;

import java.util.Arrays;
import java.util.HashSet;

public class AddUserCommand extends AbstractManagementCommand
{
  private static final L10N L = new L10N(AddUserCommand.class);

  @Override
  public void doCommand(WatchdogArgs args, WatchdogClient client)
  {
    final String user = args.getArg("-u");

    if (user == null) {
      usage();

      return;
    }

    String passwordString = args.getArg("-p");
    char []password = null;

    if (passwordString != null)
      password = passwordString.toCharArray();

    while (password == null) {
      char []passwordEntry = System.console().readPassword("%s",
                                                           "enter password:");
      if (passwordEntry.length <= 8) {
        System.out.println("password must be greater then 8 characters");

        continue;
      }

      char []passwordConfirm = System.console().readPassword("%s",
                                                             "re-enter password:");

      if (Arrays.equals(passwordEntry, passwordConfirm))
        password = passwordEntry;
      else
        System.out.println("passwords do not match");
    }

    String []roles = args.getTrailingArgs(new HashSet<String>());

    try {
      ManagerClient manager = getManagerClient(args, client);

      String message = manager.addUser(user, password, roles);

      System.out.println(message);
    } catch (Exception e) {
      if (args.isVerbose())
        e.printStackTrace();
      else
        System.out.println(e.toString());
    }
  }

  @Override
  public void usage()
  {
    System.err.println(L.l(
      "usage: java -jar resin.jar [-conf <file>] user-add -user <user> -password <password> -u <new user name> [-p <new user password>]"));
    System.err.println(L.l(""));
    System.err.println(L.l("description:"));
    System.err.println(L.l("   adds an administrative user\n"));
    System.err.println(L.l(""));
    System.err.println(L.l("options:"));
    System.err.println(L.l("   -address <address>      : ip or host name of the server"));
    System.err.println(L.l("   -port <port>            : server http port"));
    System.err.println(L.l("   -user <user>            : specifies name to use for authorising the request."));
    System.err.println(L.l("   -password <password>    : specifies password to use for authorising the request."));
    System.err.println(L.l("   -u <new user name>      : specifies name for a new user."));
    System.err.println(L.l("   -p <new user password>  : specifies password for a new user."));
  }
}
