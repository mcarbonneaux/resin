/*
 * Copyright (c) 1998-2005 Caucho Technology -- all rights reserved
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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Sam
 */


package com.caucho.profiler;

import com.caucho.util.L10N;
import com.caucho.vfs.XmlWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Formatter;

/**
 * Html interface to profiling information.
 */
public class ProfilerServlet
  extends HttpServlet
{
  private static final L10N L = new L10N(ProfilerServlet.class);

  // can do this because an instance of Filter is created for each environment
  private final ProfilerManager _profilerManager = ProfilerManager.getLocal();

  public ProfilerManager createProfiler()
  {
    return _profilerManager;
  }

  public void init()
  {
  }

  protected void doGet(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException
  {
    handleRequest(req, res);
    handleResponse(req, res);
  }

  protected void doPost(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException
  {
    handleRequest(req, res);
    handleResponse(req, res);
  }

  protected void handleRequest(HttpServletRequest request,
                               HttpServletResponse response)
    throws ServletException, IOException
  {
  }

  protected void handleResponse(HttpServletRequest request,
                                HttpServletResponse response)
    throws ServletException, IOException
  {
    ProfilerNodeComparator comparator = new TimeComparator();

    comparator.setDescending(true);

    response.setContentType("text/html");

    response.setHeader("Cache-Control", "no-cache, post-check=0, pre-check=0");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");

    XmlWriter out = new XmlWriter(response.getWriter());

    out.setStrategy(XmlWriter.HTML);
    out.setIndenting(true);

    out.println(
      "<!DOCTYPE html  PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"  \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");

    String title = L.l("Profiling Results for {0}", request.getContextPath());

    out.startElement("html");

    out.startElement("head");
    out.writeElement("title", title);

    out.startElement("style");
    out.writeAttribute("type", "text/css");

    out.println(
      "h1 { background: #ccddff; margin : 0 -0.5em 0.25em -0.25em; padding: 0.25em 0.25em; }");
    out.println(
      "h2 { background: #ccddff; padding: 0.25em 0.5em; margin : 0.5em -0.5em; }");
    out.println("table { border-collapse : collapse; }");
    out.println("th { background : #c78ae6; border-left : 1px; border-right : 1px}");
    out.println("tr { border-bottom : 1px dotted; }");
    out.println(".number { text-align : right; }");
    out.println("table table tr { border-bottom : none; }");

    out.endElement("style");

    out.endElement("head");

    out.writeElement("Total of " +
                     _profilerManager.getAllProfilerNodes().size() +
                     " nodes");

    out.startElement("body");
    out.writeElement("h1", title);

    out.startElement("table");
    out.writeAttribute("border", 0);

    out.startElement("tr");

    out.writeElement("th", L.l("Name"));

    out.writeElement("th", L.l("Average Time"));
    out.writeElement("th", L.l("Total Time"));

    out.writeElement("th", L.l("Invocation Count"));

    out.endElement("tr");

    Collection<ProfilerNode> children
      =  _profilerManager.getChildProfilerNodes(null, comparator);

    displayChildren(children, comparator, out, 0);

    out.endElement("table");

    out.endElement("body");

    out.endElement("html");
  }

  private void display(ProfilerNode node,
                       ProfilerNodeComparator comparator,
                       XmlWriter out,
                       int depth)
  {
    Collection<ProfilerNode> children
      =  _profilerManager.getChildProfilerNodes(node, comparator);

    long thisTime = node.getTime();

    long childrenTime = 0;

    for (ProfilerNode child : children) {
      childrenTime += child.getTime();
    }

    long totalTime = childrenTime + thisTime;

    long invocationCount = node.getInvocationCount();
    long averageThisTime;
    long averageTotalTime;
    long averageChildrenTime;

    if (invocationCount <= 0) {
      averageThisTime = -1;
      averageTotalTime = -1;
      averageChildrenTime = -1;
    }
    else {
      averageThisTime = thisTime / invocationCount;
      averageTotalTime = totalTime / invocationCount;
      averageChildrenTime = childrenTime / invocationCount;
    }

    out.startElement("tr");

    out.writeAttribute("class", "level" + depth);

    // Name

    out.startElement("td");

    out.startElement("table");
    out.startElement("tr");

    out.startElement("td");

    if (depth > 0) {
      for (int i = depth; i > 0; i--) {
        out.write("&nbsp;");
        out.write("&nbsp;");
      }

      out.write("&rarr;");
    }
    out.endElement("td");

    out.startElement("td");
    out.writeAttribute("class", "text");
    out.writeText(node.getName());
    out.endElement("td");

    out.endElement("tr");
    out.endElement("table");

    out.endElement("td");

    out.startElement("td");
    out.writeAttribute("class", "number");
    if (averageThisTime < 0)
      out.write("&nbsp;");
    else {
      String averageTimeString = createTimeString(averageTotalTime, averageThisTime, averageChildrenTime);
      out.writeAttribute("title", averageTimeString);
      printTime(out, averageTotalTime);
    }

    out.endElement("td");

    out.startElement("td");

    out.writeAttribute("class", "number");
    String timeString = createTimeString(totalTime, thisTime, childrenTime);
    out.writeAttribute("title", timeString);
    printTime(out, totalTime);
    out.endElement("td");

    out.startElement("td");
    out.writeAttribute("class", "number");
    out.print(invocationCount);
    out.endElement("td");

    out.endElement("tr");

    // All children

    displayChildren(children, comparator, out, depth + 1);
  }

  private String createTimeString(long totalTime,
                                  long thisTime,
                                  long childrenTime)
  {
    StringBuilder builder = new StringBuilder();

    Formatter formatter = new Formatter(builder);

    builder.append("totalTime=");
    formatTime(formatter, totalTime);

    builder.append(" thisTime=");
    formatTime(formatter, thisTime);

    builder.append(" childrenTime=");
    formatTime(formatter, childrenTime);

    return builder.toString();
  }

  private void displayChildren(Collection<ProfilerNode> children,
                               ProfilerNodeComparator comparator,
                               XmlWriter out,
                               int depth)
  {
    // All children

    for (ProfilerNode child : children)
      display(child, comparator, out, depth);
  }

  private void printTime(XmlWriter out, long time)
  {
    formatTime(new Formatter(out), time);
  }

  private void formatTime(Formatter formatter, long nanoseconds)
  {
    long milliseconds = nanoseconds / 1000000;

    long minutes = milliseconds /  1000 / 60;

    if (minutes > 0) {
      formatter.format("%d:", minutes);
      milliseconds -= minutes * 60 * 1000;
    }

    long seconds = milliseconds /  1000;

    if (minutes > 0)
      formatter.format("%02d.", seconds);
    else
      formatter.format("%d.", seconds);

    milliseconds -= seconds * 1000;

    formatter.format("%03d", milliseconds);
  }

}
