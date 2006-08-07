/*
 * Copyright (c) 1998-2000 Caucho Technology -- all rights reserved
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
 * @author Emil Ong
 */

package com.caucho.xtpdoc;

import java.io.PrintWriter;
import java.io.IOException;

import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;

public class Glossary extends FormattedText {
  private String _title;

  public Glossary(Document document)
  {
    super(document);
  }

  public void setTitle(String title)
  {
    _title = title;
  }

  public void setType(String type)
  {
  }

  public void writeHtml(XMLStreamWriter out)
    throws XMLStreamException
  {
    out.writeStartElement("div");
    out.writeAttribute("class", "glossary");
    out.writeStartElement("table");
    out.writeAttribute("cellspacing", "0");
    out.writeAttribute("border", "0");
    out.writeAttribute("width", "100%");

    if (_title != null) {
      out.writeStartElement("tr");
      out.writeStartElement("th");
      out.writeCharacters(_title);
      out.writeEndElement(); // tr
      out.writeEndElement(); // th
    }

    out.writeStartElement("tr");
    out.writeStartElement("td");

    super.writeHtml(out);

    out.writeEndElement(); // tr
    out.writeEndElement(); // td
    out.writeEndElement(); // table
    out.writeEndElement(); // div
  }

  public void writeLaTeX(PrintWriter out)
    throws IOException
  {
    out.println("\\fbox{");

    if (_title != null) {
      out.println("\\begin{center}\\texttt{" + _title + "}\\end{center}");
      out.println();
    }

    super.writeLaTeX(out);

    out.print("} ");
  }
}
